package com.bingding.asyncbox.demo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.bingding.asyncbox.AsyncBox;
import com.bingding.asyncbox.R;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AsyncBox.post(AsyncBox.IO, new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 10000; i++) {
                    Log.v("he", "hello" + i);

                }
            }
        }).post(new Runnable() {
            @Override
            public void run() {
                Log.i("this","main work");
                        ((TextView) findViewById(R.id.tv)).setText("Hello AsyncBox~~~");
            }
        });
    }
}
