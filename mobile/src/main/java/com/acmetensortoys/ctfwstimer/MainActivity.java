package com.acmetensortoys.ctfwstimer;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    // TODO surely this belongs somewhere else
    private static final String defserver = "tcp://ctfws-mqtt.ietfng.org:1883";

    private MainActivityBuildHooks mabh = new MainActivityBuildHooksImpl();

    private MainService.LocalBinder mSrvBinder; // set once connection completed
    private MainService.Observer mSrvObs = new MainService.Observer() {
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
                case MSE_SUB: setServerStateText(R.string.mqtt_subbed);
            }
        }
    };

    private CtFwSDisplayLocal mCdl; // set in onStart
    private TextView mTvSU; // set in onStart
    private TextView mTvSS; // set in onStart
    private void setServerStateText(@StringRes final int resid) {
        mTvSS.post(new Runnable() {
            @Override
            public void run() { mTvSS.setText(resid); }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (sp.getString("server", null) == null) {
            sp.edit().putString("server", defserver).apply();
        }
        if (BuildConfig.DEBUG && sp.getString("server", null) == null) {
            throw new AssertionError("Shared Preferences not sticking!");
        }

        mTvSU = (TextView) findViewById(R.id.tv_mqtt_server_uri);
        mTvSS = (TextView) findViewById(R.id.tv_mqtt_state);

        mCdl = new CtFwSDisplayLocal(this, new Handler());
    }

    @Override
    public void onStart() {
        super.onStart();

        bindService(new Intent(this, MainService.class), new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mSrvBinder = (MainService.LocalBinder) service;
                mSrvBinder.getGameState().registerObserver(mCdl);
                mSrvBinder.registerObserver(mSrvObs);
                // Fake an initial server event so we draw some text
                mSrvObs.onMqttServerEvent(mSrvBinder, mSrvBinder.getServerState());
                mabh.onStart(MainActivity.this, mSrvBinder);
                mSrvBinder.connect(false);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mSrvBinder = null;
            }
        }, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        if (mSrvBinder != null) {
            mSrvBinder.getGameState().unregisterObserver(mCdl);
            mSrvBinder.unregisterObserver(mSrvObs);
        }

        super.onStop();
    }

    // Every good application needs an easter egg
    private boolean egg = false;
    @SuppressLint({"SetTextI18n"})
    public void onclick_gamestate(View v) {
        TextView tv = (TextView) v;
        if (egg) {
            tv.setText(R.string.header_gamestate);
        } else {
            ((TextView) v).setText("DO NOT TAP ON GLASS");
        }
        egg = !egg;
    }

    // Kick the mqtt layer on a click on the status stuff
    public void onclick_connmeta(View v) {
        mSrvBinder.connect(true);
    }

    // TODO should we be using onClick instead for routing?
    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {
        switch(mi.getItemId()) {
            case R.id.menu_mqtt :
                DialogFragment d =
                        StringSettingDialogFragment.newInstance(
                                R.layout.server_dialog, R.id.server_text, "server", defserver);
                d.show(getSupportFragmentManager(),"serverdialog");
                return true;
            case R.id.menu_about :
                startActivity(new Intent(this, AboutActivity.class));
                return true;
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
