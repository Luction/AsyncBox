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

    private static Map<Integer, AsyncThread> THREAD_MAP = new ConcurrentHashMap<>();
    private static Map<Integer, BlockingQueue<AsyncAction>> ACTION_MAP = new ConcurrentHashMap<>();
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

    public static AsyncWorker post(Callable callable) {
        return new AsyncWorker().post(callable);
    }

    public static AsyncWorker post(int thread, Callable callable) {
        return new AsyncWorker().post(thread, callable);
    }

    public static AsyncWorker timeout(long timeout) {
        return new AsyncWorker().timeout(timeout);
    }

    public AsyncWorker observerOn(int thread, Observer observer) {
        return new AsyncWorker().observerOn(thread, observer);
    }

    public static AsyncWorker observerOn(Observer observer) {
        return new AsyncWorker().observerOn(observer);
    }

    public interface Callable {
        int call();
    }

    public interface TimeOutObserver {
        void onTimeOut();
    }

    public static class AsyncWorker {

        private AsyncAction mLastAction;
        private Observer mObserver;
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

        public AsyncWorker observerOn(int thread, Observer observer) {
            mObserver = observer;
            mObserverThread = thread;
            return this;
        }

        public AsyncWorker post(int thread, Callable callable) {
            AsyncAction asyncAction = new AsyncAction();
            asyncAction.callable = callable;
            asyncAction.thread = thread;
            asyncAction.observer = mObserver;
            asyncAction.timeout = mTimeOut;
            asyncAction.timeOutObserver = mTimeOutObserver;
            asyncAction.timeoutThread = mTimeOutThread;
            asyncAction.observerThread = mObserverThread;
            if (mLastAction == null || mLastAction.isCompleted.compareAndSet(true, true)) {
                asyncAction.prepareAction();
            } else {
                mLastAction.next = asyncAction;
            }
            mLastAction = asyncAction;
            return this;
        }

        public AsyncWorker post(Callable callable) {
            return post(DEFAULT_THREAD, callable);
        }

        public AsyncWorker observerOn(Observer observer) {
            return observerOn(DEFAULT_THREAD, observer);
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

    private static void call(final AsyncAction action) {
        int result = action.callable.call();
        if (action.observer != null) {
            if (result == RESULT_OK) {
                post(action.observerThread, new Runnable() {
                    @Override
                    public void run() {
                        action.observer.onCompleted();
                    }
                });
            } else {
                post(action.observerThread, new Runnable() {
                    @Override
                    public void run() {
                        action.observer.onError();
                    }
                });
            }
        }
    }

    public interface Observer {
        void onCompleted();

        void onError();
    }

    private static class AsyncAction implements Comparable<AsyncAction> {
        private Runnable runnable;
        private Callable callable;
        private AsyncAction next;
        private Observer observer;
        private int timeoutThread;
        private TimeOutObserver timeOutObserver;
        private int observerThread;
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
                    } else if (callable != null) {
                        call(AsyncAction.this);
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
                    } else if (asyncAction.callable != null) {
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
            int result = action.callable.call();
            if (action.observer != null && !action.isCompleted.compareAndSet(true, true) && !isTimeOut()) {
                if (result == RESULT_OK) {
                    post(action.observerThread, new Runnable() {
                        @Override
                        public void run() {
                            action.observer.onCompleted();
                        }
                    });
                } else {
                    post(action.observerThread, new Runnable() {
                        @Override
                        public void run() {
                            action.observer.onError();
                        }
                    });
                }
            }
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
