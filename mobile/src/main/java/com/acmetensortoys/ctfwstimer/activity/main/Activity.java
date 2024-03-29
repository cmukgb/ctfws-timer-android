package com.acmetensortoys.ctfwstimer.activity.main;

import android.annotation.SuppressLint;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import android.os.Bundle;
import androidx.appcompat.view.menu.MenuBuilder;

import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.acmetensortoys.ctfwstimer.R;
import com.acmetensortoys.ctfwstimer.activity.AboutActivity;
import com.acmetensortoys.ctfwstimer.activity.CtFwSActivityBase;
import com.acmetensortoys.ctfwstimer.activity.HandbookActivity;
import com.acmetensortoys.ctfwstimer.activity.SettingsActivity;
import com.acmetensortoys.ctfwstimer.service.MainService;
import com.acmetensortoys.ctfwstimer.utils.AndroidResourceUtils;
import com.acmetensortoys.ctfwstimer.utils.CheckedAsyncDownloader;

public class Activity extends CtFwSActivityBase {

    private static final String TAG = "CtFwS";

    private final BuildHooks mabh = new BuildHooksImpl();

    private MainService.MqttServerEvent mLastMSE;
    private final MainService.Observer mSrvObs = new MainService.Observer() {
        @Override
        public void onMqttServerChanged(MainService.LocalBinder b, final String sURL) {
        }

        @Override
        public void onMqttServerEvent(MainService.LocalBinder b, MainService.MqttServerEvent mse) {
            mLastMSE = mse;
            if (mMenuReconn != null) {
                updateMenuReconnVis();
            }
            switch(mse) {
                case MSE_CONN:
                    setServerStateText(R.string.mqtt_conn);
                    break;
                case MSE_DISCONN:
                    setServerStateText(R.string.mqtt_disconn);
                    break;
                case MSE_SUB: {
                    long lstde = b.getLastServerTimeDeltaEstimate();
                    if (Math.abs(lstde) <= 5) {
                        setServerStateText(R.string.mqtt_subbed);
                    } else {
                        setServerStateText(R.string.mqtt_subbed_tdelta, lstde);
                    }
                }
            }
        }

        @Override
        public void onHandbookFetch(MainService.LocalBinder b, CheckedAsyncDownloader.DL dl) {
        }
    };

    private MenuItem mMenuReconn;

    private CtFwSDisplay mCdl; // set in onStart
    private TextView mTvSS; // set in onStart
    private void setServerStateText(@StringRes final int resid, Object... args) {
        final Spanned h = AndroidResourceUtils.htmlFromStrResId(getResources(), resid, args);
        mTvSS.post(() -> mTvSS.setText(h));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.main_connmeta).setOnClickListener(this::onclick_connmeta);
        findViewById(R.id.header_gamestate).setOnClickListener(this::onclick_gamestate);

        mTvSS = findViewById(R.id.tv_mqtt_state);

        mCdl = new CtFwSDisplay(this);
    }

    protected void doRegisterObservers(){
        mSrvBinder.getGameState().registerObserver(mCdl);
        mSrvBinder.registerObserver(mSrvObs);
        mabh.onRegisterObservers(Activity.this, mSrvBinder);
    }

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");
        super.onStart();

        CtFwSDisplay.resetWindow(this);
    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();

        if (mSrvBinder != null) {
            mSrvBinder.getGameState().registerObserver(mCdl);
            mSrvBinder.registerObserver(mSrvObs);
        }
    }

    private final String SIS_KEY_TMR_FINI = "tmr-ends";

    @Override
    public void onRestoreInstanceState(Bundle in) {
        super.onRestoreInstanceState(in);
        mCdl.timersFromBundle(in, SIS_KEY_TMR_FINI);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        mCdl.timersToBundle(out, SIS_KEY_TMR_FINI);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        if (mSrvBinder != null) {
            mSrvBinder.getGameState().unregisterObserver(mCdl);
            mSrvBinder.unregisterObserver(mSrvObs);
        }

        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    // Every good application needs an easter egg
    private boolean egg = false;
    public void onclick_gamestate(View v) {
        final TextView tv = (TextView) v;
        // Cam: Because every good easter egg needs to be way over-engineered.
        if (!egg) {
            egg = true;
            tv.setText(R.string.header_egg);
            tv.postDelayed(() -> {
                if (mCdl != null) {
                    tv.setText(mCdl.gameStateLabelText);
                } else {
                    tv.setText(R.string.header_not_config);
                }
                egg = false;
            }, 3000);
        }
    }

    // Kick the mqtt layer on a click on the status stuff
    public void onclick_connmeta(View v) {
        if (mSrvBinder != null) {
            mSrvBinder.connect(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {
        int itemId = mi.getItemId();
        if (itemId == R.id.mainmenu_hand) {
            startActivity(new Intent(this, HandbookActivity.class));
            return true;
        } else if (itemId == R.id.mainmenu_reconn) {
            if (mSrvBinder != null) {
                mSrvBinder.connect(true);
            }
            return true;
        } else if (itemId == R.id.mainmenu_prf) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (itemId == R.id.mainmenu_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        } else if (itemId == R.id.mainmenu_quit) {
            if (mSrvBinder != null) {
                mSrvBinder.exit();
            }
            finish();
            return true;
            // Cam: Changing this doesn't appear to do anything? Leaving just in case.
        }
        return super.onOptionsItemSelected(mi);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater mi = getMenuInflater();
        mi.inflate(R.menu.mainmenu, menu);

        mMenuReconn = menu.findItem(R.id.mainmenu_reconn);
        if (mLastMSE != null) {
            updateMenuReconnVis();
        }


        if (menu instanceof MenuBuilder) {
            ((MenuBuilder)menu).setOptionalIconsVisible(true);
        }

        return true;
    }

    private void updateMenuReconnVis() {
        runOnUiThread(() -> {
            switch(mLastMSE) {
                case MSE_CONN:
                case MSE_SUB:
                    mMenuReconn.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                    break;
                case MSE_DISCONN:
                    mMenuReconn.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                    break;
            }
        });
    }
}
