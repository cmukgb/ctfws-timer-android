package com.acmetensortoys.ctfwstimer;

import android.util.Log;

import com.acmetensortoys.ctfwstimer.lib.CtFwSGameStateManager;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;

class CtFwSCallbacksMQTT {
    private CtFwSGameStateManager mCgs;

    CtFwSCallbacksMQTT(CtFwSGameStateManager cgs) {
        mCgs = cgs;
    }

    final public void dispose() {
        mCgs = null;
    }

    final IMqttMessageListener onConfig = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) {
            String tm = message.toString().trim();
            Log.d("CtFwS", "Message(Config): " + tm);
            mCgs.fromMqttConfigMessage(tm);
        }
    };

    final IMqttMessageListener onEnd = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) {
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
        public void messageArrived(String topic, MqttMessage message) {
            String tm = message.toString().trim();
            Log.d("CtFwS", "Message(Flags): " + tm);
            mCgs.fromMqttFlagsMessage(tm);
        }
    };

    final IMqttMessageListener onMessage = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) {
            String str = message.toString();
            Log.d("CtFwS", "Message(Broadcast): " + str);
            mCgs.onNewMessage(str);
        }
    };

    final IMqttMessageListener onPlayerMessage = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) {
            String str = message.toString();
            Log.d("CtFwS", "Message(Players): " + str);
            mCgs.onNewMessage(str);
        }
    };

    final IMqttMessageListener onMessageReset = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) {
            String str = message.toString();
            Log.d("CtFwS", "Message(Reset): " + str);
            long before;
            try {
                before = Long.parseLong(message.toString());
            } catch (NumberFormatException e) {
                return;
            }
            mCgs.onMessageReset(before);
        }
    };
}
