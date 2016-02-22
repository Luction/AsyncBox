package com.bingding.asyncbox.demo;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.bingding.asyncbox.AsyncBox;
import com.bingding.asyncbox.R;

public class MainActivity extends Activity {

    private static final String TAG = "DEMO";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AsyncBox.timeout(3000)
                .observerTimeout(new AsyncBox.TimeOutObserver() {
                    @Override
                    public void onTimeOut() {
                        Log.i(TAG, "run in main .  timeout !!!");
                    }
                })
                .post(AsyncBox.IO, new AsyncBox.Observable<String>() {
                    @Override
                    public String call() {
                        Log.i(TAG, "run in " + Thread.currentThread().getName());
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        return "nihao";
                    }

                    @Override
                    public void onResult(String o) {
                        Log.i(TAG, "run in " + Thread.currentThread().getName() + " result:" + o);
                        ((TextView) findViewById(R.id.tv)).setText(o);
                    }
                }).post(AsyncBox.IO, new AsyncBox.Observable<Integer>() {
            @Override
            public Integer call() {
                Log.i(TAG, "run in " + Thread.currentThread().getName());
                return 3;
            }

            @Override
            public void onResult(Integer o) {
                Log.i(TAG, "run in " + Thread.currentThread().getName() + " result:" + o);
                ((TextView) findViewById(R.id.tv)).setText(String.valueOf(o));

            }
        });

//        AsyncBox.post(AsyncBox.IO, new Runnable() {
//            @Override
//            public void run() {
//                Log.i(TAG, "run in io.");
//                try {
//                    Thread.sleep(3000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        }).post(new Runnable() {
//            @Override
//            public void run() {
//                Log.i(TAG, "tun in main.");
//                        ((TextView) findViewById(R.id.tv)).setText("Hello AsyncBox~~~");
//            }
//        }).observerOn(new AsyncBox.Observer() {
//            @Override
//            public void onCompleted() {
//                Log.i(TAG, "observerOn  main onCompleted");
//
//                ((TextView) findViewById(R.id.tv)).setText("Hello AsyncBox onCompleted~~~");
//
//            }
//
//            @Override
//            public void onError() {
//                Log.i(TAG, "observerOn main onError");
//                ((TextView) findViewById(R.id.tv)).setText("Hello AsyncBox onError~~~");
//
//            }
//        }).post(AsyncBox.IO, new AsyncBox.Observable() {
//            @Override
//            public int call() {
//                Log.i(TAG, "call in  IO  main return ok");
//                try {
//                    Thread.sleep(5000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//                return AsyncBox.RESULT_OK;
//            }
//        }).post(new AsyncBox.Observable() {
//            @Override
//            public int call() {
//                Log.i(TAG, "call in main return error");
//                return AsyncBox.RESULT_ERROR;
//            }
//        });
    }
}
