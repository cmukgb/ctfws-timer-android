package com.acmetensortoys.ctfwstimer;

import android.content.SharedPreferences;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MainActivity extends AppCompatActivity {

    static private final String mqttClientId = MqttClient.generateClientId();
    private MqttAndroidClient mMqc;

    private final CtFwSGameState mCgs = new CtFwSGameState();
    private CtFwSDisplay mCdl; // set in onCreate
    private CtFwSCallbacksMQTT mCtfwscbs ; // set in onCreate

    private synchronized void doMqtt(@Nullable String server) {
        // Hang up on an existing connection, if we have one
        synchronized (this) {
            if (mMqc != null) {
                mMqc.close();
            }
            mMqc = null;
            mCgs.configured = false;
            mCdl.notifyGameState();
        }

        // If that's all we were told to do, we're done
        if (server == null) { return ; }

        // We'll use this common callback object for our subscriptions below
        final IMqttActionListener subal = new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                Log.d("CtFwS", "Sub OK: " + asyncActionToken);
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                Log.e("CtFws", "Sub Fail: " + asyncActionToken, exception);
            }
        };

        // Make our MQTT client and grab callbacks on *everything in sight*
        final MqttAndroidClient mqc = new MqttAndroidClient(this,server, mqttClientId);
        mqc.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                Log.d("CtFwS", "Conn OK 2 srv=" + serverURI + " reconn=" + reconnect);
                try {
                    String p = "ctfws/game/";
                    mqc.subscribe(p+"config"        , 2, null, subal, mCtfwscbs.onConfig);
                    mqc.subscribe(p+"endtime"       , 2, null, subal, mCtfwscbs.onEnd);
                    mqc.subscribe(p+"flags"         , 2, null, subal, mCtfwscbs.onFlags);
                    mqc.subscribe(p+"message"       , 2, null, subal, mCtfwscbs.onMessage);
                    mqc.subscribe(p+"message/player", 2, null, subal, mCtfwscbs.onPlayerMessage);
                } catch (MqttException e) {
                    Log.e("CtFwS", "Exn Sub", e);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.d("CtFwS", "Conn Lost", cause);
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
        });


        // Ahem.  Now then.  Connect with *more callbacks*, which will fire off our
        // subscription requests, which of course have *yet more* callbacks, which
        // react to messages sent to us.  Have we lost the thread yet?
        try {
            MqttConnectOptions mco = new MqttConnectOptions();
            mco.setAutomaticReconnect(true);
            mco.setKeepAliveInterval(180); // seconds

            mqc.connect(mco, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d("CtFwS", "Conn OK 1");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e("CtFws", "Conn Fail", exception);
                }
            });

        } catch (MqttException e) {
            Log.e("CtFwS", "Conn Exn", e);
        }

        synchronized (this) {
            if (BuildConfig.DEBUG && mMqc != null) { throw new AssertionError(); }
            mMqc = mqc;
        }
    }

    // Must hold strongly since Android only holds weakly once registered.
    private SharedPreferences.OnSharedPreferenceChangeListener mOSPCL
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch(key) {
                case "server":
                    doMqtt(sharedPreferences.getString("server",null));
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mCdl = new CtFwSDisplay(this, new Handler(), mCgs);
        mCtfwscbs = new CtFwSCallbacksMQTT(mCdl, mCgs);

        // TODO There really should be a UI thing for changing the server; we're all
        // set for when-/if-ever that happens.

        getPreferences(MODE_PRIVATE).registerOnSharedPreferenceChangeListener(mOSPCL);
        doMqtt(getPreferences(MODE_PRIVATE).getString("server","tcp://nwf1.xen.prgmr.com:1883"));
    }
}
