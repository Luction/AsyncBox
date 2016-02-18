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

    private static final String TAG="AsyncBox";

    private static Map<Integer,AsyncThread> THREAD_MAP = new ConcurrentHashMap<>();
    private static Map<Integer,BlockingQueue<AsyncAction>> ACTION_MAP = new ConcurrentHashMap<>();
    private static Patrol PATROL;

    public static final int IO = -88;
    public static final int MAIN = 0;

    private static int DEFAULT_THREAD = MAIN;

    private static Handler HANDLER = new Handler();

    public static void setDefaultThread(int id){
        DEFAULT_THREAD = id;
    }

    public static AsyncWorker post(Runnable runnable){
        return new AsyncWorker().post(DEFAULT_THREAD,runnable);
    }

    public static AsyncWorker post(int thread, Runnable runnable){
        return new AsyncWorker().post(thread,runnable);

    }

    public static class AsyncWorker{

        private AsyncAction lastAction;

        public AsyncWorker post(int thread, Runnable runnable){
            AsyncAction asyncAction = new AsyncAction();
            asyncAction.runnable = runnable;
            asyncAction.thread = thread;
            if(lastAction == null || lastAction.isCompleted.compareAndSet(true,true)) {
                asyncAction.prepareAction();
            }else {
                lastAction.next = asyncAction;
            }
            lastAction = asyncAction;
            return this;
        }

        public AsyncWorker post(Runnable runnable){
            return post(DEFAULT_THREAD,runnable);
        }

    }


    private static class Patrol extends Thread{

        @Override
        public void run() {
            while (true){
                for(AsyncThread thread : THREAD_MAP.values()){
                    if(!thread.isQuited() && !thread.isAlive()){
                        AsyncThread asyncThread = new AsyncThread(thread.mActionQueue);
                        asyncThread.start();
                    }
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static class AsyncAction implements Comparable<AsyncAction>{
        private Runnable runnable;
        private AsyncAction next;
        private int thread;
        private AtomicBoolean isCompleted = new AtomicBoolean();

        @Override
        public int compareTo(AsyncAction another) {
            return 0;
        }

        private void runOnMain(final Runnable runnable){
            HANDLER.post(new Runnable() {
                @Override
                public void run() {
                    if(runnable != null) {
                        runnable.run();
                    }
                    isCompleted.compareAndSet(false,true);
                    Log.i(TAG, "finish main thread!");
                    if(next != null) {
                        next.prepareAction();
                    }
                }
            });
        }

        private void prepareAction(){
            Log.i(TAG,"prepare");
            if(MAIN == thread){
                runOnMain(runnable);
            }else {
                if(!THREAD_MAP.containsKey(thread)){
                    if(PATROL == null){
                        PATROL = new Patrol();
                        PATROL.start();
                    }
                    BlockingQueue<AsyncAction> blockingQueue = new PriorityBlockingQueue<>();
                    AsyncThread asyncThread = new AsyncThread(blockingQueue);
                    asyncThread.start();
                    THREAD_MAP.put(thread,asyncThread);
                    ACTION_MAP.put(thread,blockingQueue);
                }
                BlockingQueue<AsyncAction> queue = ACTION_MAP.get(thread);
                if(queue != null){

                    try {
                        queue.put(this);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }else {
                    Log.e(TAG,"action queue un find!");
                }
            }
        }
    }

    private static class AsyncThread extends Thread{

        private boolean mIsQuited;
        private BlockingQueue<AsyncAction> mActionQueue;
        private AsyncAction mCurrentAction;

        AsyncThread(BlockingQueue<AsyncAction> asyncActions){
            mActionQueue = asyncActions;
        }

        @Override
        public void run() {
            while (!mIsQuited){
                try {
                    mCurrentAction = mActionQueue.take();
                    if(mCurrentAction.runnable != null){
                        mCurrentAction.runnable.run();
                    }
                    mCurrentAction.isCompleted.compareAndSet(false, true);
                    Log.i(TAG, "finish thread" + mCurrentAction.thread);
                    if(mCurrentAction.next != null){
                        mCurrentAction.next.prepareAction();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }

        boolean isQuited(){
            return mIsQuited;
        }

        void quit(){
            mIsQuited = true;
            interrupt();
        }
    }

}
