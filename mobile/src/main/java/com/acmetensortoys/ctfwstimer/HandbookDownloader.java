package com.acmetensortoys.ctfwstimer;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.acmetensortoys.ctfwstimer.CheckedAsyncDownloader;

import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.File;
import java.net.URL;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class HandbookDownloader implements IMqttMessageListener {

    private static String TAG = "HandbookDownloader";

    private static final long HAND_MAX_LEN = 1024*1024; /* 1 MiB */

    private final Context mCtx;
    private final Runnable mDLFiniCB;
    private IMqttAsyncClient mMqc;
    private Handler mHdl;
    private Runnable nextSubRunnable;

    public HandbookDownloader(Context ctx, Handler hdl, Runnable dlfinicb) {
        this.mCtx = ctx;
        this.mHdl = hdl;
        this.mDLFiniCB = dlfinicb;
    }

    /* non-null if download is in progress; access synchronized on this */
    private CheckedAsyncDownloader downloader;
    private CheckedAsyncDownloader.DL download;
    private byte[] lastFetchedChecksum;

    /*
     * Alright, fetch time.  We're going to unsubscribe for the duration of the
     * transfer and resubscribe later.  This has two effects: it slightly reduces the
     * odds that we'll get another message while this transfer is in progress and it
     * will cause us to get another message at the end.  Hopefully, that later message
     * carries the checksum of the file we just downloaded, so we'll skip out without
     * redoing the transfer.
     *
     * Note that the CheckedAsyncDownloader also verifies the checksum of the file
     * present on disk, so at startup we might run one AsyncTask and then bail out.
     */
    private class Task extends CheckedAsyncDownloader {
        final IMqttAsyncClient mqc;

        public Task(IMqttAsyncClient mqc) {
            this.mqc = mqc;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            Log.d(TAG, "Pre-ex");
            synchronized (HandbookDownloader.this) {
                try {
                    unsubscribe(mqc);
                } catch (MqttException mqe) {
                    /* No real harm in the matter */
                    ;
                }
            }
        }

        private void fini() {
            /*
             * Try to resubscribe in a while.
             * This is a very crude kind of rate limiting.
             */
            synchronized (this) {
                if (nextSubRunnable != null) {
                    mHdl.removeCallbacks(nextSubRunnable);
                }
                nextSubRunnable = new Runnable() {
                    @Override
                    public void run() {
                        Log.d(TAG, "Resubscribing to handbook topic");
                        synchronized (HandbookDownloader.this) {
                            if (mqc == mMqc) {
                                try {
                                    subscribe(mqc);
                                    nextSubRunnable = null;
                                } catch (MqttException mqe) {
                                    /*
                                     * Well this stinks.  Presumably it is because
                                     * something has gone wrong somewhere else and
                                     * we will notice shortly.
                                     */
                                }
                            }
                        }
                    }
                };
                mHdl.postDelayed(nextSubRunnable, 60000);
            }

            HandbookDownloader.this.downloader = null;
            HandbookDownloader.this.download = null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            long result;
            synchronized (HandbookDownloader.this) {
                DL dl = HandbookDownloader.this.download;
                result = dl.result;
                if ((result >= 0) || (result == ERR_ALREADY)) {
                    HandbookDownloader.this.lastFetchedChecksum = dl.sha256;
                }
                fini();
            }
            Log.d(TAG, "Post Ex: " + result);
            if (result >= 0) {
                /* If we downloaded something new, go run the callback chain */
                HandbookDownloader.this.mDLFiniCB.run();
            }
        }

        @Override
        protected void onCancelled(Void aVoid) {
            super.onCancelled(aVoid);
            Log.d(TAG, "Cancel");
            synchronized (HandbookDownloader.this) {
                fini();
            }
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        /* Try to parse the message: URL timestamp checksum */
        String url, checksum_str;

        Log.d(TAG, "Begin processing download via '" + message.toString());
        if (this.downloader != null) {
            Log.d(TAG, "Download already in progress; ignoring message");
            return;
        }

        try {
            Scanner s = new Scanner(message.toString().trim());
            url = s.next();
            s.next(); /* discard timestamp */
            checksum_str = s.next();
        } catch (NoSuchElementException nsee) {
            /* Malformed message; give up */
            Log.d(TAG, "Malformed handbook message");
            return;
        }

        if (checksum_str.length() != 64) {
            /* Malformed message */
            Log.d(TAG, "Malformed checksum");
            return;
        }

        byte[] checksum = new byte[checksum_str.length() / 2];
        for (int i = 0; i < checksum.length; i++) {
            checksum[i] = (byte)((Character.digit(checksum_str.charAt(2*i), 16) << 4)
                               + (Character.digit(checksum_str.charAt(2*i+1),16)));
        }
        synchronized (this) {
            if (lastFetchedChecksum != null
                    && java.util.Arrays.equals(checksum, lastFetchedChecksum)) {
                /* Nothing to do */
                Log.d(TAG, "Checksum matches last fetch");
                return;
            }

            if (this.downloader != null) {
                Log.d(TAG, "Download already in progress; ignoring message");
                return;
            }

            this.downloader = new Task(mMqc);
            this.download = new CheckedAsyncDownloader.DL(new URL(url), checksum, HAND_MAX_LEN,
                    new File(mCtx.getFilesDir(), HandbookActivity.HAND_FILE_NAME));
            this.downloader.execute(this.download);
        }
    }

    private void subscribe(IMqttAsyncClient mqc) throws MqttException {
        mqc.subscribe("ctfws/rules/handbook/html", 2, this);
    }

    public void subscribeOn(IMqttAsyncClient mqc) throws MqttException {
        synchronized (this) {
            if (nextSubRunnable != null) {
                mHdl.removeCallbacks(nextSubRunnable);
            }
            mMqc = mqc;
            subscribe(mqc);
        }
    }

    private void unsubscribe(IMqttAsyncClient mqc) throws MqttException {
        mqc.unsubscribe("ctfws/rules/handbook/html");
    }

    public void unsubscribeOn(IMqttAsyncClient mqc) throws MqttException {
        synchronized (this) {
            if (nextSubRunnable != null) {
                mHdl.removeCallbacks(nextSubRunnable);
            }
            unsubscribe(mqc);
            mMqc = null;
        }
    }
}