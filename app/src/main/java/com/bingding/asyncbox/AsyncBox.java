package com.bingding.asyncbox;

import android.os.Handler;
import android.util.Log;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by bingding on 16/2/18.
 */
public class AsyncBox {

    private static final String TAG = "AsyncBox";

    private static Map<Integer, AsyncThread> THREAD_MAP = new ConcurrentHashMap<Integer, AsyncThread>();
    private static Map<Integer, BlockingQueue<AsyncAction>> ACTION_MAP = new ConcurrentHashMap<Integer, BlockingQueue<AsyncAction>>();
    private static Patrol PATROL;


    public static final int RESULT_OK = 0;
    public static final int RESULT_ERROR = -1;
    public static final int IO = -88;
    public static final int MAIN = 0;

    private static int DEFAULT_THREAD = MAIN;

    private static Handler HANDLER = new Handler();

    public static void setDefaultThread(int id) {
        DEFAULT_THREAD = id;
    }

    public static AsyncWorker post(Runnable runnable) {
        return new AsyncWorker().post(runnable);
    }

    public static AsyncWorker post(int thread, Runnable runnable) {
        return new AsyncWorker().post(thread, runnable);

    }

    public static AsyncWorker post(Observable observable) {
        return new AsyncWorker().post(observable);
    }

    public static AsyncWorker post(int thread, Observable observable) {
        return new AsyncWorker().post(thread, observable);
    }

    public static AsyncWorker observerOn(int thread) {
        return new AsyncWorker().observeOn(thread);
    }

    public static AsyncWorker timeout(long timeout) {
        return new AsyncWorker().timeout(timeout);
    }


    public static abstract class Observable<T> {

        private int thread = DEFAULT_THREAD;

        public abstract T call();

        private void run(AtomicBoolean isCompleted) {
            final T result = call();
            if (!isCompleted.compareAndSet(true, true)) {
                post(thread, new Runnable() {
                    @Override
                    public void run() {
                        onResult(result);
                    }
                });
            }

        }

        public abstract void onResult(T t);
    }

    public interface TimeOutObserver {
        void onTimeOut();
    }

    public static class AsyncWorker {

        private AsyncAction mLastAction;
        private long mTimeOut;
        private int mTimeOutThread = DEFAULT_THREAD;
        private TimeOutObserver mTimeOutObserver;
        private int mObserverThread = DEFAULT_THREAD;

        public AsyncWorker post(int thread, Runnable runnable) {
            AsyncAction asyncAction = new AsyncAction();
            asyncAction.runnable = runnable;
            asyncAction.timeout = mTimeOut;
            asyncAction.timeOutObserver = mTimeOutObserver;
            asyncAction.timeoutThread = mTimeOutThread;
            asyncAction.thread = thread;
            if (mLastAction == null || mLastAction.isCompleted.compareAndSet(true, true)) {
                asyncAction.prepareAction();
            } else {
                mLastAction.next = asyncAction;
            }
            mLastAction = asyncAction;
            return this;
        }

        public AsyncWorker post(Runnable runnable) {
            return post(DEFAULT_THREAD, runnable);
        }

        public AsyncWorker observeOn(int thread) {
            mObserverThread = thread;
            return this;
        }

        public AsyncWorker post(int thread, Observable observable) {
            AsyncAction asyncAction = new AsyncAction();
            asyncAction.observable = observable;
            asyncAction.thread = thread;
            asyncAction.timeout = mTimeOut;
            asyncAction.timeOutObserver = mTimeOutObserver;
            asyncAction.timeoutThread = mTimeOutThread;
            if (observable != null) {
                asyncAction.observable.thread = mObserverThread;
            }
            if (mLastAction == null || mLastAction.isCompleted.compareAndSet(true, true)) {
                asyncAction.prepareAction();
            } else {
                mLastAction.next = asyncAction;
            }
            mLastAction = asyncAction;
            return this;
        }

        public AsyncWorker post(Observable observable) {
            return post(DEFAULT_THREAD, observable);
        }

        public AsyncWorker timeout(long timeout) {
            mTimeOut = timeout;
            return this;
        }

        public AsyncWorker cancelTimeOut() {
            mTimeOut = 0;
            mTimeOutObserver = null;
            mObserverThread = DEFAULT_THREAD;
            return this;
        }

        public AsyncWorker observerTimeout(TimeOutObserver timeOutObserver) {
            return observerTimeout(DEFAULT_THREAD, timeOutObserver);
        }

        public AsyncWorker observerTimeout(int thread, TimeOutObserver observer) {
            mObserverThread = thread;
            mTimeOutObserver = observer;
            return this;
        }

    }

    private static class Patrol extends Thread {

        @Override
        public void run() {
            while (true) {

                for (AsyncThread thread : THREAD_MAP.values()) {

                    if (!thread.isQuited() && !thread.isAlive()) {
                        AsyncThread asyncThread = new AsyncThread(thread.mActionQueue);
                        asyncThread.start();
                    } else if (!thread.isCompleted() && thread.isTimeOut()) {
                        thread.timeout();
                        thread.interrupt();
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class AsyncAction implements Comparable<AsyncAction> {
        private Runnable runnable;
        private Observable observable;
        private AsyncAction next;
        private int timeoutThread;
        private TimeOutObserver timeOutObserver;
        private int thread;
        private long timeout;
        private AtomicBoolean isCompleted = new AtomicBoolean();

        @Override
        public int compareTo(AsyncAction another) {
            return 0;
        }

        private void runOnMain(final Runnable runnable) {
            HANDLER.post(new Runnable() {
                @Override
                public void run() {
                    if (runnable != null) {
                        runnable.run();
                    } else if (observable != null) {
                        observable.run(isCompleted);
                    }
                    isCompleted.compareAndSet(false, true);
                    Log.i(TAG, "finish main thread!");
                    if (next != null) {
                        next.prepareAction();
                    }
                }
            });
        }

        private void prepareAction() {
            Log.i(TAG, "prepare");
            if (MAIN == thread) {
                runOnMain(runnable);
            } else {
                if (!THREAD_MAP.containsKey(thread)) {
                    if (PATROL == null) {
                        PATROL = new Patrol();
                        PATROL.start();
                    }
                    BlockingQueue<AsyncAction> blockingQueue = new PriorityBlockingQueue<>();
                    AsyncThread asyncThread = new AsyncThread(blockingQueue);
                    asyncThread.start();
                    if (thread == IO) {
                        asyncThread.setName("IO");
                    }
                    THREAD_MAP.put(thread, asyncThread);
                    ACTION_MAP.put(thread, blockingQueue);
                }
                BlockingQueue<AsyncAction> queue = ACTION_MAP.get(thread);
                if (queue != null) {
                    try {
                        queue.put(this);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e(TAG, "action queue un find!");
                }
            }
        }
    }

    private static class AsyncThread extends Thread {

        private boolean mIsQuited;
        private BlockingQueue<AsyncAction> mActionQueue;
        private AsyncAction mCurrentAction;
        private volatile long mCurrentStartTime;

        AsyncThread(BlockingQueue<AsyncAction> asyncActions) {
            mActionQueue = asyncActions;
        }

        @Override
        public void run() {
            while (!mIsQuited) {
                try {
                    mCurrentStartTime = 0;
                    AsyncAction asyncAction = mActionQueue.take();
                    mCurrentAction = asyncAction;
                    mCurrentStartTime = System.currentTimeMillis();
                    if (asyncAction.runnable != null) {
                        asyncAction.runnable.run();
                    } else if (asyncAction.observable != null) {
                        call(asyncAction);
                    }
                    mCurrentStartTime = 0;
                    asyncAction.isCompleted.compareAndSet(false, true);
                    Log.i(TAG, "finish thread" + asyncAction.thread);
                    if (asyncAction.next != null) {
                        asyncAction.next.prepareAction();
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "thread interrupt istimeout " + isTimeOut());
                }

            }

        }

        private void call(final AsyncAction action) {
            action.observable.run(action.isCompleted);
        }

        boolean isQuited() {
            return mIsQuited;
        }

        boolean isTimeOut() {
            return mCurrentAction != null && mCurrentAction.timeout != 0 && mCurrentStartTime != 0 && System.currentTimeMillis() > mCurrentStartTime + mCurrentAction.timeout;
        }

        boolean isCompleted() {
            return mCurrentAction == null && mCurrentAction.isCompleted.compareAndSet(true, true);
        }

        void quit() {
            mIsQuited = true;
            interrupt();
        }

        void timeout() {
            if (mCurrentAction != null && mCurrentAction.timeOutObserver != null) {
                post(mCurrentAction.timeoutThread, new Runnable() {
                    @Override
                    public void run() {
                        mCurrentAction.timeOutObserver.onTimeOut();
                    }
                });
            }
            mCurrentAction.isCompleted.compareAndSet(false, true);
        }
    }

}
