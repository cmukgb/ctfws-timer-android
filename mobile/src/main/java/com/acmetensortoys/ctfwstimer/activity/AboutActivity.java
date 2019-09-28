package com.acmetensortoys.ctfwstimer.activity;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.v7.app.ActionBar;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.webkit.WebView;
import android.widget.Chronometer;
import android.widget.TabHost;
import android.widget.TextView;

import com.acmetensortoys.ctfwstimer.BuildConfig;
import com.acmetensortoys.ctfwstimer.utils.CtFwSDisplayTinyChrono;
import com.acmetensortoys.ctfwstimer.R;
import com.acmetensortoys.ctfwstimer.lib.CtFwSGameStateManager;
import com.acmetensortoys.ctfwstimer.service.MainService;
import com.acmetensortoys.ctfwstimer.utils.CheckedAsyncDownloader;

import java.util.Arrays;
import java.util.Locale;
import java.util.SortedSet;

public class AboutActivity extends CtFwSActivityBase {

    private static final String TAG = "About";
    private CtFwSDisplayTinyChrono mTitleChronoObs;

    private static final String TAB_PROG  = "TabProg";
    private static final String TAB_LIC   = "TabLic";
    private static final String TAB_DEBUG = "TabDebug";

    private CheckedAsyncDownloader.DL lastDL;

    private TextView mTvDebug;

    private void makeDebugText() {
        final StringBuffer sb = new StringBuffer();

        sb.append("Android host version: ");
        sb.append(Build.VERSION.RELEASE);
        sb.append(" (SDK ");
        sb.append(Build.VERSION.SDK_INT);
        sb.append(")\n");

        sb.append("Source git description: ");
        sb.append(BuildConfig.gitDescription);
        sb.append("\n");

        sb.append("\n");

        {
            if (lastDL != null) {
                sb.append("Last fetched handbook:\n  result: ");
                sb.append(lastDL.getResult());
                sb.append("\n  checksum: ");
                for (byte b : Arrays.copyOfRange(lastDL.sha256, 0, 16)) {
                    sb.append(String.format(Locale.ROOT, "%02x", b));
                }
                sb.append("...\n");
            } else {
                sb.append("No handbook download attempted.\n");
            }
        }

        if (mSrvBinder != null) {
            CtFwSGameStateManager cgs = mSrvBinder.getGameState();

            sb.append("\nLast game configuration:\n  raw:       ");
            sb.append(cgs.getLastConfigMessage());
            sb.append("\n  parsed: ");
            sb.append(cgs.toMqttConfigMessage());
            sb.append("\n");
        } else {
            sb.append("Null service binder\n");
        }

        mTvDebug.post(new Runnable() {
            @Override
            public void run() {
                mTvDebug.setText(sb);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        mTvDebug = findViewById(R.id.about_debug_tv);
        makeDebugText();

        ActionBar ab = getSupportActionBar();
        if (ab != null) {
            ab.setTitle(R.string.about_title);
        }

        View iv = findViewById(R.id.about_image);
        iv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("http://www.cmukgb.org/")));
                } catch (ActivityNotFoundException anfe) {
                    // NOP
                }
            }
        });

        {
            final WebView wv = findViewById(R.id.about_text);
            wv.loadData(getResources().getString(R.string.about_text),
                    "text/html", null);
        }

        {
            final WebView wv = findViewById(R.id.about_licenses);
            wv.loadUrl("file:///android_asset/licenses.html");
        }

        TabHost th = findViewById(R.id.about_tab_host);
        th.setup();

        th.addTab(th.newTabSpec(TAB_PROG)
                .setContent(R.id.about_tab_program)
                .setIndicator(getResources().getString(R.string.about_tab_program)));

        th.addTab(th.newTabSpec(TAB_LIC)
                .setContent(R.id.about_tab_lic)
                .setIndicator(getResources().getString(R.string.about_tab_license)));

        th.addTab(th.newTabSpec(TAB_DEBUG)
                .setContent(R.id.about_tab_debug)
                .setIndicator(getResources().getString(R.string.about_tab_debug)));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.aboutmenu, menu);

        Chronometer ch = (Chronometer) menu.findItem(R.id.about_menu_crono).getActionView();
        mTitleChronoObs = new CtFwSDisplayTinyChrono(getResources(), ch);
        if (mSrvBinder != null) {
            doRegisterObservers();
        }

        return true;
    }

    private final MainService.Observer mSrvObs = new MainService.Observer() {
        @Override
        public void onMqttServerChanged(MainService.LocalBinder b, String sURL) {

        }

        @Override
        public void onMqttServerEvent(MainService.LocalBinder b, MainService.MqttServerEvent mse) {

        }

        @Override
        public void onHandbookFetch(MainService.LocalBinder b, CheckedAsyncDownloader.DL dl) {
            lastDL = dl;
            makeDebugText();
        }
    };

    private final CtFwSGameStateManager.Observer mCtFwSObs = new CtFwSGameStateManager.Observer() {
        @Override
        public void onCtFwSConfigure(CtFwSGameStateManager game) {
            makeDebugText();
        }

        @Override
        public void onCtFwSNow(CtFwSGameStateManager game, CtFwSGameStateManager.Now now) {

        }

        @Override
        public void onCtFwSFlags(CtFwSGameStateManager game) {

        }

        @Override
        public void onCtFwSMessage(CtFwSGameStateManager game, SortedSet<CtFwSGameStateManager.Msg> msgs) {

        }
    };

    protected void doRegisterObservers() {
        mSrvBinder.registerObserver(mSrvObs);
        mSrvBinder.getGameState().registerObserver(mCtFwSObs);
        if (mTitleChronoObs != null) {
            mSrvBinder.getGameState().registerObserver(mTitleChronoObs);
        }
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        if (mSrvBinder != null) {
            mSrvBinder.getGameState().unregisterObserver(mTitleChronoObs);
            mSrvBinder.getGameState().unregisterObserver(mCtFwSObs);
            mSrvBinder.unregisterObserver(mSrvObs);
        }

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }
}
