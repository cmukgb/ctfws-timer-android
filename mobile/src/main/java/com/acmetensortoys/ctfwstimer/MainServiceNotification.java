package com.acmetensortoys.ctfwstimer;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import com.acmetensortoys.ctfwstimer.lib.CtFwSGameState;

import java.util.List;

class MainServiceNotification {
    final private MainService mService;
    private NotificationCompat.Builder userNoteBuilder;

    MainServiceNotification(MainService ms, CtFwSGameState game){
        mService = ms;

        Intent ni = new Intent(ms, MainActivity.class);
        ni.setAction(Intent.ACTION_MAIN);
        ni.addCategory(Intent.CATEGORY_LAUNCHER);
        ni.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        userNoteBuilder = new NotificationCompat.Builder(ms)
                .setOnlyAlertOnce(false)
                .setSmallIcon(R.drawable.shield1)
                .setContentIntent(PendingIntent.getActivity(ms, 0, ni, 0));

        game.registerObserver(new CtFwSGameState.Observer() {
            @Override
            public void onCtFwSConfigure(CtFwSGameState game) { }

            @Override
            public void onCtFwSNow(CtFwSGameState game, CtFwSGameState.Now now) {
                userNoteBuilder.setWhen((now.roundEnd + 1) * 1000);
                userNoteBuilder.setUsesChronometer(true);
                if (now.rationale == null || !now.stop) {
                    // game is afoot or in the future!
                    Resources rs = mService.getResources();

                    if (now.rationale == null) {
                        userNoteBuilder.setSubText(rs.getString(R.string.notify_afoot));
                        if (now.round == 0) {
                            userNoteBuilder.setContentTitle(rs.getString(R.string.notify_gamestart));
                        } else if (now.round == game.getRounds()) {
                            userNoteBuilder.setContentTitle(rs.getString(R.string.notify_gameend));
                        } else {
                            userNoteBuilder.setContentTitle(
                                    String.format(rs.getString(R.string.notify_jailbreak),
                                            now.round, game.getRounds() - 1));
                        }
                    } else {
                        userNoteBuilder.setSubText(now.rationale);
                    }

                    vibrate(VIBRATE_PATTERN_NOW);
                    ensureNotification();
                } else {
                    // game no longer afoot
                    ensureNoNotification();
                }
            }

            @Override
            public void onCtFwSFlags(CtFwSGameState game) {
                // If flags are hidden or there aren't any captured (e.g. this is a notification
                // of a reset to 0), don't do anything.
                if (game.flagsVisible && (game.flagsRed + game.flagsYel > 0)) {
                    vibrate(VIBRATE_PATTERN_FLAG);
                    userNoteBuilder.setContentText(
                            String.format(mService.getResources().getString(R.string.notify_flags),
                                    game.flagsRed, game.flagsYel));
                    refreshNotification();
                }
            }

            @Override
            public void onCtFwSMessage(CtFwSGameState game, List<CtFwSGameState.Msg> msgs) {
                // Only do anything if we aren't clearing the message list
                int s = msgs.size();
                if (s != 0) {
                    vibrate(VIBRATE_PATTERN_MSG);
                    userNoteBuilder.setContentText(msgs.get(s - 1).msg);
                    refreshNotification();
                }
            }
        });
    }

    private long[] VIBRATE_PATTERN_NOW  = {0, 100, 100, 300, 100, 300, 100, 300}; // 'J' = .---
    private long[] VIBRATE_PATTERN_FLAG = {0, 100, 100, 100, 100, 300, 100, 100}; // 'F' = ..-.
    private long[] VIBRATE_PATTERN_MSG  = {0, 300, 100, 300};                     // 'M' = --

    private void vibrate(long[] pattern) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mService.getBaseContext());
        // Cam: default value is "false" because we really don't want to be vibrating if we
        //      accidentally lose our preferences somehow
        if (sp.getBoolean("prf_vibr", false)) {
            userNoteBuilder.setVibrate(pattern);
        }
        else {
            userNoteBuilder.setVibrate(null);
        }
    }

    private ServiceConnection userNoteSC;
    private void refreshNotification() {
        synchronized (this) {
            if (userNoteSC != null) {
                mService.startForeground(MainService.NOTE_ID_USER, userNoteBuilder.build());
            }
        }
    }
    private void ensureNotification() {
        synchronized(this) {
            if (userNoteSC == null) {
                userNoteSC = new ServiceConnection() {
                    @Override
                    public void onServiceConnected(ComponentName name, IBinder service) {
                    }

                    @Override
                    public void onServiceDisconnected(ComponentName name) {
                    }
                };
                // Ensure that the service stays alive while the notification is active
                mService.bindService(new Intent(mService, MainService.class), userNoteSC,
                        Context.BIND_AUTO_CREATE);
            }
            mService.startForeground(MainService.NOTE_ID_USER, userNoteBuilder.build());
        }
    }
    private void ensureNoNotification() {
        synchronized (this) {
            if (userNoteSC != null) {
                mService.stopForeground(true);
                mService.unbindService(userNoteSC);
                userNoteSC = null;
            }
        }
    }

}
