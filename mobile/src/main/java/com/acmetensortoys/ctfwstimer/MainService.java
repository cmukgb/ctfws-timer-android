package com.acmetensortoys.ctfwstimer;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;

import com.acmetensortoys.ctfwstimer.lib.CtFwSGameState;
import com.acmetensortoys.ctfwstimer.lib.TimerProvider;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.android.service.MqttTraceHandler;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.HashSet;
import java.util.Set;

public class MainService extends Service {
    // Android stuff
    static final int NOTE_ID_USER = 1;
    private Handler mHandler; // set in onCreate

    // The reason we're here!
    private final CtFwSGameState mCgs
            = new CtFwSGameState(new TimerProvider() {
        @Override
        public long wallMS() {
            return System.currentTimeMillis();
        }

        @Override
        public void postDelay(Runnable r, long delayMS) {
            mHandler.postDelayed(r, delayMS);
        }

        @Override
        public void cancelPost(Runnable r) {
            mHandler.removeCallbacks(r);
        }
    });
    private final CtFwSCallbacksMQTT mCtfwscbs = new CtFwSCallbacksMQTT(mCgs);

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private MainServiceNotification mMsn; // set in onCreate

    public MainService() { }

    // MQTT client management

    private MqttAndroidClient mMqc;
    // Trace MQTT state
    private final MqttTraceHandler mqttth = new MqttTraceHandler() {
        @Override
        public void traceDebug(String tag, String message) {
            Log.d("CtFwSMqtt:" + tag, message);
        }

        @Override
        public void traceError(String tag, String message) {
            Log.e("CtFwSMqtt:" + tag, message);
        }

        @Override
        public void traceException(String tag, String message, Exception e) {
            Log.e("CtFwSMqtt:" + tag, message, e);
        }
    };

    // We'll use this common callback object for our subscriptions below
    private final IMqttActionListener subal = new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken asyncActionToken) {
            Log.d("CtFwS", "Sub OK: " + asyncActionToken);
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
            Log.e("CtFws", "Sub Fail: " + asyncActionToken, exception);
            setMSE(MqttServerEvent.MSE_CONN);
        }
    };
    // And this handles making our subscriptions for us
    private final MqttCallbackExtended mqttcb = new MqttCallbackExtended() {
        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            Log.d("CtFwS", "Conn OK 2 srv=" + serverURI + " reconn=" + reconnect);
            String p = "ctfws/game/";
            try {
                mMqc.subscribe(p + "config", 2, null, subal, mCtfwscbs.onConfig);
                mMqc.subscribe(p + "endtime", 2, null, subal, mCtfwscbs.onEnd);
                mMqc.subscribe(p + "flags", 2, null, subal, mCtfwscbs.onFlags);
                mMqc.subscribe(p + "message", 2, null, subal, mCtfwscbs.onMessage);
                mMqc.subscribe(p + "message/player", 2, null, subal, mCtfwscbs.onPlayerMessage);
            } catch (MqttException e) {
                Log.e("CtFwS", "Exn Sub", e);
            }
            setMSE(MqttServerEvent.MSE_SUB);
        }

        @Override
        public void connectionLost(Throwable cause) {
            Log.d("CtFwS", "Conn Lost: " + cause, cause);
            setMSE(MqttServerEvent.MSE_DISCONN);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            Log.d("CtFwS", "Message(Generic) " + topic + " : '" + message + "'" );
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // Unused, as we never publish
            Log.d("CtFwS", "Delivery OK");
        }
    };
    // And this handles yet more about connecting
    private final IMqttActionListener mqttal = new IMqttActionListener() {
        @Override
        public void onSuccess(IMqttToken asyncActionToken) {
            Log.d("CtFwS", "Conn OK 1");
            IMqttAsyncClient c = asyncActionToken.getClient();
            if (c.equals(mMqc)) {
                setMSE(MqttServerEvent.MSE_CONN);
            } else {
                Log.d("Service", "IS STALE CONN");
                try {
                    c.disconnect().waitForCompletion();
                } catch (MqttException me) {
                    // Drop it, we've already dropped the client handle
                }
            }
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
            Log.e("CtFws", "Conn Fail", exception);
            if (asyncActionToken.getClient().equals(mMqc)) {
                setMSE(MqttServerEvent.MSE_DISCONN);
            } else {
                Log.d("Service", "IS STALE DISCONN");
            }
        }
    };

    private synchronized void doMqtt(@Nullable String server) {
        Log.d("Service", "domqtt");

        // Hang up on an existing connection, if we have one
        if (mMqc != null) {
            mMqc.setCallback(null);

            // TODO: This is *really* annoying; we might leak a connection here because
            // .disconnect() is so @#*@&#*@^#*&@^ asynchronous it hurts.  There appears
            // to be no way to force its hand, and adding .waitforcompletion() here just
            // causes our app to hang.  Bah humbug.  Just leak the connection.  The server
            // will presumably eventually give up on us or something.  Man, I have no idea,
            // and the documentation for all of this is amazingly lacking.  Burn it all down.
            try {
                mMqc.close();
            } catch (IllegalArgumentException iae) {
                Log.d("Service", "domqtt disconn close exn");
                // *&@#&^*#@#&@#&@#
            }
            mMqc.unregisterResources();
        } else {
            Log.d("Service", "domqtt no client");
        }

        // If we're deliberately disconnecting, tell the service about it.  Otherwise, we'll
        // just keep doing what we're doing until we get some message telling us to do something
        // else. :)
        if (server == null) {
            mCgs.deconfigure();
        }

        notifyServerChanged(server);

        // If disconnecting is all we were told to do, we're done.
        if (server == null) {
            mMqc = null;
            return;
        }

        // Make our MQTT client and grab callbacks on *everything in sight*
        //
        // XXX For reasons beyond my understanding, we have to use a new client ID every time
        // or we won't resubscribe.  I think this is github issue eclipse/paho.mqtt.android#170
        // but heavens only knows.  Whatever, this works for the moment and doesn't leave
        // stragglers on my server as far as I can tell.
        mMqc = new MqttAndroidClient(this, server, MqttClient.generateClientId());
        mMqc.setCallback(mqttcb);
        // Debugging aid: trace the paho client internals
        mMqc.setTraceCallback(mqttth);
        // mMqc.setTraceEnabled(true);

        // Ahem.  Now then.  Connect with *more callbacks*, which will fire off our
        // subscription requests, which of course have *yet more* callbacks, which
        // react to messages sent to us.  Have we lost the thread yet?
        MqttConnectOptions mco = new MqttConnectOptions();
        mco.setCleanSession(true);
        mco.setAutomaticReconnect(true);
        mco.setKeepAliveInterval(180); // seconds
        try {
            mMqc.connect(mco, null, mqttal);
        } catch (MqttException e) {
            Log.e("Service", "Conn Exn", e);
        }
        Log.d("Service", "Connect dispatched");
    }

    // Must hold strongly since Android only holds weakly once registered.
    // TODO: Option for notification persistence during game?
    // TODO: Option for polling even after game ends?
    private final SharedPreferences.OnSharedPreferenceChangeListener mOSPCL
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch(key) { case "server": doMqtt(sharedPreferences.getString(key,null)); break; }
        }
    };

    // MQTT Observers
    public enum MqttServerEvent {
        MSE_DISCONN,
        MSE_CONN,
        MSE_SUB,
    }
    private MqttServerEvent mMSE = MqttServerEvent.MSE_DISCONN;
    public interface Observer {
        void onMqttServerChanged(LocalBinder b, String sURL);
        void onMqttServerEvent(LocalBinder b, MqttServerEvent mse);
    }
    private final Set<Observer> mObsvs = new HashSet<>();
    private void setMSE(MqttServerEvent mse) {
        synchronized(this) {
            mMSE = mse;
            for (Observer o : mObsvs) { o.onMqttServerEvent(mBinder, mse); }
        }
    }
    private void notifyServerChanged(String sURL) {
        synchronized(this) {
            for (Observer o : mObsvs) { o.onMqttServerChanged(mBinder, sURL); }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mHandler = new Handler();
        mMsn = new MainServiceNotification(this, mCgs);
    }

    public class LocalBinder extends Binder {
        CtFwSGameState getGameState() {
            return mCgs;
        }

        // It should not be necessary to call this except at the beginning or to force a reconnect;
        // most everything else you might want in a connect method is handled by the
        // OnSharedPreferenceChangeListener listener above.
        void connect(boolean force) {
            if (force || mMSE != MqttServerEvent.MSE_SUB) {
                SharedPreferences sp = PreferenceManager
                        .getDefaultSharedPreferences(MainService.this);
                sp.registerOnSharedPreferenceChangeListener(mOSPCL);
                doMqtt(sp.getString("server", null));
            }
        }
        void registerObserver(Observer o) {
            synchronized(this) {
                if (mObsvs.add(o)) {
                    // Fire off synthetic deltas to bring the observer up to date.
                    if (mMqc == null) {
                        o.onMqttServerChanged(mBinder, null);
                    } else {
                        o.onMqttServerChanged(mBinder, mMqc.getServerURI());
                    }
                    o.onMqttServerEvent(mBinder, mMSE);
                }
            }
        }
        void unregisterObserver(Observer o) {
            synchronized(this) { mObsvs.remove(o); }
        }
    }
    private final LocalBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        Log.d("Service", "onBind: " + intent);
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Log.d("Service", "onRebind" + intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d("Service", "onUnbind: " + intent);
        // All clients have been unbound, go ahead and disconnect and stop ourselves.
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(mOSPCL);
        doMqtt(null);
        return true;
    }
}
