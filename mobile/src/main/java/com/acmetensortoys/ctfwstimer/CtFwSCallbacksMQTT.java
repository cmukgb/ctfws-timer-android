package com.acmetensortoys.ctfwstimer;

import android.util.Log;

import com.acmetensortoys.ctfwstimer.lib.CtFwSGameState;
import com.acmetensortoys.ctfwstimer.lib.CtFwSMessageFilter;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.NoSuchElementException;
import java.util.Scanner;

class CtFwSCallbacksMQTT {
    final private CtFwSDisplay mCdl;
    final private CtFwSGameState mCgs;
    final private CtFwSMessageFilter mCmf;

    CtFwSCallbacksMQTT(CtFwSDisplay cdl, CtFwSGameState cgs) {
        mCdl = cdl;
        mCgs = cgs;
        mCmf = new CtFwSMessageFilter(mCgs);
    }

    IMqttMessageListener onConfig = new IMqttMessageListener() {
        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
            String tm = message.toString().trim();
            Log.d("CtFwS", "Message(Config): " + tm);
            mCgs.mqttConfigMessage(tm);
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
            mCgs.mqttFlagsMessage(tm);
            mCdl.notifyFlags();
        }
    };

    private void onMessageCommon(String str) {
        CtFwSMessageFilter.Msg m = mCmf.filter(str);
        if (m != null) {
            mCdl.notifyMessage(m.when, m.msg);
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
