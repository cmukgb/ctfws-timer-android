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

    IMqttMessageListener onMessage = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            Log.d("CtFwS", "Message(Broadcast): " + message);
            mCdl.notifyMessage(message.toString());
        }
    };

    IMqttMessageListener onPlayerMessage = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            Log.d("CtFwS", "Message(Players): " + message);
            mCdl.notifyMessage(message.toString());
        }
    };
}
