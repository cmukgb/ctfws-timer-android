package com.acmetensortoys.ctfwstimer;

import android.util.Log;

import com.acmetensortoys.ctfwstimer.lib.CtFwSGameState;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;

class CtFwSCallbacksMQTT {
    private CtFwSGameState mCgs;

    CtFwSCallbacksMQTT(CtFwSGameState cgs) {
        mCgs = cgs;
    }

    final public void dispose() {
        mCgs = null;
    }

    final IMqttMessageListener onConfig = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            String tm = message.toString().trim();
            Log.d("CtFwS", "Message(Config): " + tm);
            mCgs.fromMqttConfigMessage(tm);
        }
    };

    final IMqttMessageListener onEnd = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            Log.d("CtFwS", "Message(End): " + message);
            long endT;
            try {
                endT = Long.parseLong(message.toString());
            } catch (NumberFormatException e) {
                endT = 0;
            }
            mCgs.setEndT(endT);
        }
    };

    final IMqttMessageListener onFlags = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            String tm = message.toString().trim();
            Log.d("CtFwS", "Message(Flags): " + tm);
            mCgs.fromMqttFlagsMessage(tm);
        }
    };

    final IMqttMessageListener onMessage = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            String str = message.toString();
            Log.d("CtFwS", "Message(Broadcast): " + str);
            mCgs.onNewMessage(str);
        }
    };

    final IMqttMessageListener onPlayerMessage = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            String str = message.toString();
            Log.d("CtFwS", "Message(Players): " + str);
            mCgs.onNewMessage(str);
        }
    };
}
