package com.acmetensortoys.ctfwstimer;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.acmetensortoys.ctfwstimer.lib.CtFwSGameState;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.android.service.MqttTraceHandler;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainService extends Service {
    // Android stuff
    private static final int NOTE_ID_USER = 1;
    private Handler mHandler; // set in OnCreate

    // The reason we're here!
    private final CtFwSGameState mCgs
            = new CtFwSGameState(new CtFwSGameState.TimerProvider() {
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
    private CtFwSCallbacksMQTT mCtfwscbs = new CtFwSCallbacksMQTT(mCgs);

    public MainService() {
        mCgs.registerObserver(mCgsObserver);
    }

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
            try {
                String p = "ctfws/game/";
                mMqc.subscribe(p+"config"        , 2, null, subal, mCtfwscbs.onConfig);
                mMqc.subscribe(p+"endtime"       , 2, null, subal, mCtfwscbs.onEnd);
                mMqc.subscribe(p+"flags"         , 2, null, subal, mCtfwscbs.onFlags);
                mMqc.subscribe(p+"message"       , 2, null, subal, mCtfwscbs.onMessage);
                mMqc.subscribe(p+"message/player", 2, null, subal, mCtfwscbs.onPlayerMessage);
                setMSE(MqttServerEvent.MSE_SUB);
            } catch (MqttException e) {
                Log.e("CtFwS", "Exn Sub", e);
            }
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
            setMSE(MqttServerEvent.MSE_CONN);
        }

        @Override
        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
            Log.e("CtFws", "Conn Fail", exception);
            setMSE(MqttServerEvent.MSE_DISCONN);
        }
    };

    private synchronized void doMqtt(@Nullable String server) {
        // Hang up on an existing connection, if we have one
        synchronized (this) {
            if (mMqc != null) {
                if (mMqc.isConnected()) {
                    try {
                        mMqc.disconnect();
                        Log.d("CtFwS", "domqtt disconnected");
                    } catch (MqttException me) {
                        Log.e("CtFwS", "domqtt disconn exn", me);
                    }
                }
            }
            mMqc = null;
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
            return;
        }

        // Make our MQTT client and grab callbacks on *everything in sight*
        //
        // XXX For reasons beyond my understanding, we have to use a new client ID every time
        // or we won't resubscribe.  I think this is github issue eclipse/paho.mqtt.android#170
        // but heavens only knows.  Whatever, this works for the moment and doesn't leave
        // stragglers on my server as far as I can tell.
        MqttAndroidClient mqc = new MqttAndroidClient(this,server, MqttClient.generateClientId());
        mqc.setCallback(mqttcb);
        /*
        // Debugging aid: trace the paho client internals
        mqc.setTraceCallback(mqttth);
        mqc.setTraceEnabled(true);
        */

        // Ahem.  Now then.  Connect with *more callbacks*, which will fire off our
        // subscription requests, which of course have *yet more* callbacks, which
        // react to messages sent to us.  Have we lost the thread yet?
        try {
            MqttConnectOptions mco = new MqttConnectOptions();
            mco.setCleanSession(true);
            mco.setAutomaticReconnect(true);
            mco.setKeepAliveInterval(180); // seconds
            synchronized (this) {
                if (BuildConfig.DEBUG && mMqc != null) { throw new AssertionError(); }
                mMqc = mqc;
            }
            mqc.connect(mco, null, mqttal);
            Log.d("CtFwS", "Connect dispatched");
        } catch (MqttException e) {
            Log.e("CtFwS", "Conn Exn", e);
        }
    }

    // Must hold strongly since Android only holds weakly once registered.
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

    // User-facing notification
    // TODO Move to its own display module?
    private NotificationCompat.Builder userNoteBuilder;
    private CtFwSGameState.Observer mCgsObserver = new CtFwSGameState.Observer() {
        @Override
        public void onCtFwSConfigure(CtFwSGameState game) { }

        @Override
        public void onCtFwSNow(CtFwSGameState game, CtFwSGameState.Now now) {
            userNoteBuilder.setWhen((now.roundEnd+1)*1000);
            userNoteBuilder.setUsesChronometer(true);
            if (now.rationale == null || !now.stop) {
                // game is afoot!
                userNoteBuilder.setContentTitle(
                        now.rationale == null ? "Game is afoot!" : now.rationale);
                userNoteBuilder.setContentText(
                        now.round == 0 ? "Setup phase" : ("Round " + now.round));
                startForeground(NOTE_ID_USER, userNoteBuilder.build());
            } else {
                // game no longer afoot
                stopForeground(true);
            }
        }

        @Override
        public void onCtFwSFlags(CtFwSGameState game) { }

        @Override
        public void onCtFwSMessage(CtFwSGameState game, List<CtFwSGameState.Msg> msgs) { }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        mHandler = new Handler();


        userNoteBuilder = new NotificationCompat.Builder(MainService.this)
                .setSmallIcon(R.drawable.shield1)
                .setContentIntent(PendingIntent.getActivity(MainService.this, 0,
                        new Intent(MainService.this, MainActivity.class), 0));

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        synchronized(this) {
            sp.registerOnSharedPreferenceChangeListener(mOSPCL);
            doMqtt(sp.getString("server", null));
        }
    }

    public class LocalBinder extends Binder {
        CtFwSGameState getGameState() {
            return mCgs;
        }
        MqttServerEvent getServerState() {
            return mMSE;
        }

        // It should not be necessary to call this execpt at the beginning or to force a reconnect;
        // most everything else you might want in a connect method is handled by the
        // OnSharedPreferenceChangeListener listener above.
        void connect(boolean force) {
            if (force || mMSE != MqttServerEvent.MSE_CONN) {
                doMqtt(PreferenceManager
                        .getDefaultSharedPreferences(MainService.this)
                        .getString("server", null));
            }
        }
        void registerObserver(Observer o) {
            synchronized(this) { mObsvs.add(o); }
        }
        void unregisterObserver(Observer o) {
            synchronized(this) { mObsvs.remove(o); }
        }
    }
    private LocalBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
