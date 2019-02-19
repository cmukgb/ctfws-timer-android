package com.acmetensortoys.ctfwstimer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.StringRes;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CtFwS";

    private final MainActivityBuildHooks mabh = new MainActivityBuildHooksImpl();

    private MainService.LocalBinder mSrvBinder; // set once connection completed
    private final MainService.Observer mSrvObs = new MainService.Observer() {
        @Override
        public void onMqttServerChanged(MainService.LocalBinder b, final String sURL) {
            mTvSU.post(new Runnable() {
                @Override
                public void run() {
                    if (sURL == null) {
                        mTvSU.setText(R.string.string_null);
                    } else {
                        mTvSU.setText(sURL);
                    }
                }
            });
        }

        @Override
        public void onMqttServerEvent(MainService.LocalBinder b, MainService.MqttServerEvent mse) {
            switch(mse) {
                case MSE_CONN: setServerStateText(R.string.mqtt_conn); break;
                case MSE_DISCONN: setServerStateText(R.string.mqtt_disconn); break;
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
    };

    private CtFwSDisplayLocal mCdl; // set in onStart
    private TextView mTvSU; // set in onStart
    private TextView mTvSS; // set in onStart
    private void setServerStateText(@StringRes final int resid, Object... args) {
        final Spanned h = AndroidResourceUtils.htmlFromStrResId(getResources(), resid, args);
        mTvSS.post(new Runnable() {
            @Override
            public void run() { mTvSS.setText(h); }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.connmeta).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onclick_connmeta(v);
            }
        });

        findViewById(R.id.header_gamestate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onclick_gamestate(v);
            }
        });

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (sp.getString("server", null) == null) {
            sp.edit().putString("server", getString(R.string.server_default)).apply();
        }
        if (BuildConfig.DEBUG && sp.getString("server", null) == null) {
            throw new AssertionError("Shared Preferences not sticking!");
        }

        mTvSU = findViewById(R.id.tv_mqtt_server_uri);
        mTvSS = findViewById(R.id.tv_mqtt_state);

        mCdl = new CtFwSDisplayLocal(this);
    }

    private final ServiceConnection ctfwssc = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mSrvBinder = (MainService.LocalBinder) service;
            mSrvBinder.getGameState().registerObserver(mCdl);
            mSrvBinder.registerObserver(mSrvObs);
            mabh.onStart(MainActivity.this, mSrvBinder);
            mSrvBinder.connect(false);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mSrvBinder = null;
        }
    };

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
    public void onSaveInstanceState(Bundle out) {
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
        unbindService(ctfwssc);

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
            tv.postDelayed(new Runnable() {
                public void run() {
                    if (mCdl != null) {
                        tv.setText(mCdl.gameStateLabelText);
                    } else {
                        tv.setText(R.string.header_gamestate0);
                    }
                    egg = false;
                }
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
        switch(mi.getItemId()) {
            case R.id.menu_hand:
                startActivity(new Intent(this, HandbookActivity.class));
                return true;
            case R.id.menu_reconn:
                if (mSrvBinder != null) {
                    mSrvBinder.connect(true);
                }
                return true;
            case R.id.menu_prf :
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.menu_about :
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            case R.id.menu_quit:
                if (mSrvBinder != null) {
                    mSrvBinder.exit();
                }
                finish();
                return true;
            // Cam: Changing this doesn't appear to do anything? Leaving just in case.
            default:
                return super.onOptionsItemSelected(mi);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater mi = getMenuInflater();
        mi.inflate(R.menu.mainmenu, menu);
        return true;
    }
}
