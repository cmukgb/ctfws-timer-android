package com.acmetensortoys.ctfwstimer.activity;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.appcompat.app.AppCompatActivity;

import com.acmetensortoys.ctfwstimer.service.MainService;

/*
 * Basically all of our activities bind to the MainService and so
 * contain code that looks like this.
 */

public abstract class CtFwSActivityBase extends AppCompatActivity {

    protected MainService.LocalBinder mSrvBinder;

    /*
     * Register the activity's observer(s).  Unregister in onPause.
     */
    abstract protected void doRegisterObservers();

    private final ServiceConnection ctfwssc = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mSrvBinder = (MainService.LocalBinder) service;
            doRegisterObservers();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mSrvBinder = null;
        }
    };

    @Override
    public void onStart() {
        super.onStart();

        if (mSrvBinder == null) {
            Intent si = new Intent(this, MainService.class);
            bindService(si, ctfwssc, Context.BIND_AUTO_CREATE | Context.BIND_ABOVE_CLIENT);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mSrvBinder != null) {
            doRegisterObservers();
        }
    }

    @Override
    protected void onDestroy() {
        unbindService(ctfwssc);
        super.onDestroy();
    }
}
