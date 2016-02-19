package com.bingding.asyncbox.demo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.bingding.asyncbox.AsyncBox;
import com.bingding.asyncbox.R;

public class MainActivity extends AppCompatActivity {

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
                .observerOn(new AsyncBox.Observer() {
                    @Override
                    public void onCompleted() {
                        Log.i(TAG, "observerOn  main onCompleted");

                        ((TextView) findViewById(R.id.tv)).setText("Hello AsyncBox onCompleted~~~");

                    }

                    @Override
                    public void onError() {
                        Log.i(TAG, "observerOn main onError");
                        ((TextView) findViewById(R.id.tv)).setText("Hello AsyncBox onError~~~");

                    }
                })
                .post(AsyncBox.IO, new AsyncBox.Callable() {
                    @Override
                    public int call() {
                        Log.i(TAG, "call in  IO  main return ok");
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        return AsyncBox.RESULT_OK;
                    }
                }).post(AsyncBox.IO, new AsyncBox.Callable() {
            @Override
            public int call() {
                Log.i(TAG, "call in main return error");
                return AsyncBox.RESULT_ERROR;
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
//        }).post(AsyncBox.IO, new AsyncBox.Callable() {
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
//        }).post(new AsyncBox.Callable() {
//            @Override
//            public int call() {
//                Log.i(TAG, "call in main return error");
//                return AsyncBox.RESULT_ERROR;
//            }
//        });
    }
}
