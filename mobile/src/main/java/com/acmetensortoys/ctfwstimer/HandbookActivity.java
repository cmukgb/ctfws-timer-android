package com.acmetensortoys.ctfwstimer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetManager;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.net.Uri;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class HandbookActivity extends AppCompatActivity {

    public static final String HAND_FILE_NAME = "handbook.html";
    private static final String TAG = "CtFwSHandbook";

    private WebView mWV;

    private void display() {
        final File dlf = new File(getFilesDir(), HAND_FILE_NAME);
        if (dlf.exists()) {
            /* render the version we've downloaded */
            mWV.loadUrl(dlf.toURI().toString());
        } else {
            /* render the version we were shipped with instead */
            mWV.loadUrl("file:///android_asset/hand.html");
        }
    }

    private final MainService.Observer mSrvObs = new MainService.Observer() {
        @Override
        public void onMqttServerChanged(MainService.LocalBinder b, String sURL) {
            ;
        }

        @Override
        public void onMqttServerEvent(MainService.LocalBinder b, MainService.MqttServerEvent mse) {
            ;
        }

        @Override
        public void onHandbookFetch(MainService.LocalBinder b, CheckedAsyncDownloader.DL dl) {
            display();
            if (dl.result == CheckedAsyncDownloader.Result.RES_OK) {
                Toast.makeText(HandbookActivity.this,
                                R.string.hand_new,
                                Toast.LENGTH_SHORT)
                     .show();
            }
        }
    };

    private MainService.LocalBinder mSrvBinder;
    private final ServiceConnection ctfwssc = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mSrvBinder = (MainService.LocalBinder) service;
            mSrvBinder.registerObserver(mSrvObs);
            mSrvBinder.connect(false);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mSrvBinder = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_handbook);

        mWV = findViewById(R.id.hand_wv);

        WebSettings wvs = mWV.getSettings();
        wvs.setBuiltInZoomControls(true);
        wvs.setDisplayZoomControls(false);
        display();
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();

        if (mSrvBinder == null) {
            Intent si = new Intent(this, MainService.class);
            bindService(si, ctfwssc, Context.BIND_AUTO_CREATE | Context.BIND_ABOVE_CLIENT);
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        if (mSrvBinder != null) {
            mSrvBinder.registerObserver(mSrvObs);
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        if (mSrvBinder != null) {
            mSrvBinder.unregisterObserver(mSrvObs);
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        unbindService(ctfwssc);

        super.onDestroy();
    }
}
