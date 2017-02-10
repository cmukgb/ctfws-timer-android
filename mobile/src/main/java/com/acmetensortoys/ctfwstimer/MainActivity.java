package com.acmetensortoys.ctfwstimer;

import android.content.SharedPreferences;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

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

    private TextView mTvSU; // set in onCreate
    private TextView mTvSS; // set in onCreate
    private void setServerStateText(@StringRes final int resid) {
        mTvSS.post(new Runnable() {
            @Override
            public void run() { mTvSS.setText(resid); }
        });
    }

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

    private synchronized void doMqtt(@Nullable String server) {
        // Hang up on an existing connection, if we have one
        synchronized (this) {
            if (mMqc != null) {
                try { mMqc.disconnect(); } catch (MqttException me) { ; }
            }
            mMqc = null;
            mCgs.configured = false;
            mCdl.notifyGameState();
        }

        // If that's all we were told to do, we're done
        if (server == null) {
            Log.d("CtFwS", "doMqtt null");
            mTvSU.setText(R.string.string_null);
            return;
        }
        Log.d("CtFwS", "doMqtt not null:" + server);

        mTvSU.setText(server);

        // Make our MQTT client and grab callbacks on *everything in sight*
        final MqttAndroidClient mqc = new MqttAndroidClient(this,server,mqttClientId);
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
                    setServerStateText(R.string.mqtt_subbed);
                } catch (MqttException e) {
                    Log.e("CtFwS", "Exn Sub", e);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.d("CtFwS", "Conn Lost", cause);
                setServerStateText(R.string.mqtt_disconn);
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
            mco.setCleanSession(true);
            mco.setAutomaticReconnect(true);
            mco.setKeepAliveInterval(180); // seconds

            mqc.connect(mco, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d("CtFwS", "Conn OK 1");
                    setServerStateText(R.string.mqtt_conn);
                    mCdl.clearMsgs();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e("CtFws", "Conn Fail", exception);
                    setServerStateText(R.string.mqtt_disconn);
                    mCdl.clearMsgs();
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
                    String s = sharedPreferences.getString(key,null);
                    if (s != null) { doMqtt(s); }
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        String defserver = "tcp://nwf1.xen.prgmr.com:1883";

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvSU = (TextView) findViewById(R.id.tv_mqtt_server_uri);
        mTvSS = (TextView) findViewById(R.id.tv_mqtt_state);

        mCdl = new CtFwSDisplay(this, new Handler(), mCgs);
        mCtfwscbs = new CtFwSCallbacksMQTT(mCdl, mCgs);

        SharedPreferences sp = getPreferences(MODE_PRIVATE);
        if (sp.getString("server", null) == null) {
            sp.edit().putString("server", defserver).apply();
        }
        if (BuildConfig.DEBUG && sp.getString("server", null) == null) {
            throw new AssertionError("Shared Preferences not sticking!");
        }

        synchronized(this) {
            sp.registerOnSharedPreferenceChangeListener(mOSPCL);
            doMqtt(sp.getString("server", defserver));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {
        switch(mi.getItemId()) {
            case R.id.menu_mqtt :
                DialogFragment d =
                        StringSettingDialogFragment.newInstance(
                                R.layout.server_dialog, R.id.server_text, "server");
                d.show(getSupportFragmentManager(),"serverdialog");
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
