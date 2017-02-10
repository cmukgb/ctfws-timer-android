package com.acmetensortoys.ctfwstimer;

import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.NoSuchElementException;
import java.util.Scanner;

class CtFwSCallbacksMQTT {
    final private CtFwSDisplay mCdl;
    final private CtFwSGameState mCgs;

    CtFwSCallbacksMQTT(CtFwSDisplay cdl, CtFwSGameState cgs) {
        mCdl = cdl;
        mCgs = cgs;
    }

    IMqttMessageListener onConfig = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            String tm = message.toString().trim();
            Log.d("CtFwS", "Message(Config): " + tm);

            switch (tm) {
                case "none":
                    mCgs.configured = false;
                    break;
                default:
                    try {
                        Scanner s = new Scanner(tm);
                        mCgs.startT    =  s.nextLong();
                        mCgs.setupD     = s.nextInt();
                        mCgs.rounds     = s.nextInt();
                        mCgs.roundD     = s.nextInt();
                        mCgs.flagsTotal = s.nextInt();
                        mCgs.configured = true;
                    } catch (NoSuchElementException e) {
                        mCgs.configured = false;
                    }
                    break;
            }
            mCdl.notifyGameState();
        }
    };

    IMqttMessageListener onEnd = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            Log.d("CtFwS", "Message(End): " + message);
            try {
                mCgs.endT = Long.parseLong(message.toString());
            } catch (NumberFormatException e) {
                mCgs.endT = 0;
            }
            mCdl.notifyGameState();
        }
    };

    IMqttMessageListener onFlags = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            String tm = message.toString().trim();
            Log.d("CtFwS", "Message(Flags): " + tm);

            switch(tm) {
                case "?":
                    mCgs.setFlags(false);
                    break;
                default:
                    Scanner s = new Scanner(tm);
                    try {
                        mCgs.setFlags(true);
                        mCgs.setFlags(s.nextInt(),s.nextInt());
                    } catch (NumberFormatException e) {
                        mCgs.setFlags(false);
                    }
            }
            mCdl.notifyFlags();
        }
    };

    private long lastMsgTimestamp = 0;
    private void onMessageCommon(String str) {
        Scanner s = new Scanner(str);
        try {
            long t = s.nextLong();
            // If there is no configuration, assume the message is new enough
            // If there *is* a configuration, check the time.
            if ((!mCgs.configured  || t >= mCgs.startT) && (lastMsgTimestamp <= t)) {
                s.useDelimiter("\\z");
                lastMsgTimestamp = t;
                mCdl.notifyMessage(t, s.next().trim());
            }
        } catch (NoSuchElementException nse) {
            // Maybe they forgot a time stamp.  That's not ideal, but... fake it?
            lastMsgTimestamp = System.currentTimeMillis()/1000;
            mCdl.notifyMessage(lastMsgTimestamp, str);
            lastMsgTimestamp -= 30; // XXX Back off a bit, for time sync reasons
        }
    }

    IMqttMessageListener onMessage = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            String str = message.toString();
            Log.d("CtFwS", "Message(Broadcast): " + str);
            onMessageCommon(str);
        }
    };

    IMqttMessageListener onPlayerMessage = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            String str = message.toString();
            Log.d("CtFwS", "Message(Players): " + str);
            onMessageCommon(str);
        }
    };
}
