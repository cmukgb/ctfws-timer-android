package com.acmetensortoys.ctfwstimer;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import com.acmetensortoys.ctfwstimer.lib.CtFwSGameState;

import java.util.List;

class MainServiceNotification {
    final private MainService mService;
    private final NotificationCompat.Builder userNoteBuilder;

    private long lastVibrateTime;

    private enum NotificationSource { NONE, BREAK, FLAG, MESG }
    private enum LastContentTextSource { NONE, FLAG, MESG }
    private LastContentTextSource lastContextTextSource = LastContentTextSource.NONE;

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
                if (now.rationale == null || !now.stop) {
                    // game is afoot or in the future!

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        userNoteBuilder.setWhen((now.roundEnd + 1) * 1000);
                    } else {
                        userNoteBuilder.setWhen(now.roundStart * 1000);
                    }
                    userNoteBuilder.setUsesChronometer(true);

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

                    notifyUserSomehow(NotificationSource.BREAK);
                    ensureNotification();
                } else {
                    // game no longer afoot
                    if (now.past) {
                        userNoteBuilder.setUsesChronometer(false);
                        userNoteBuilder.setShowWhen(false);
                        userNoteBuilder.setContentTitle(now.rationale);
                        userNoteBuilder.setSubText(now.rationale);
                        refreshNotification();
                    }
                    ensureNoNotification(!now.past);
                }
            }

            @Override
            public void onCtFwSFlags(CtFwSGameState game) {
                // If flags are hidden or there aren't any captured (e.g. this is a notification
                // of a reset to 0), don't do anything, unless the flags were the last thing
                // asserted, in which case, we allow a correction.
                if (game.flagsVisible
                        && ((lastContextTextSource == LastContentTextSource.FLAG)
                            || (game.flagsRed + game.flagsYel > 0))) {
                    notifyUserSomehow(NotificationSource.FLAG);
                    lastContextTextSource = LastContentTextSource.FLAG;
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
                    notifyUserSomehow(NotificationSource.MESG);
                    lastContextTextSource = LastContentTextSource.MESG;
                    userNoteBuilder.setContentText(msgs.get(s - 1).msg);
                    refreshNotification();
                }
            }
        });
    }

    // TODO make all of these configurable?
    private final long NOTIFY_SUPPRESS_THRESHOLD = 5000; // suppress rapid-fire buzzing

    private final long[] VIBRATE_PATTERN_NOW  = {0, 100, 100, 300, 100, 300, 100, 300}; // 'J' = .---
    private final long[] VIBRATE_PATTERN_FLAG = {0, 100, 100, 100, 100, 300, 100, 100}; // 'F' = ..-.
    private final long[] VIBRATE_PATTERN_MSG  = {0, 300, 100, 300};                     // 'M' = --


    private void notifyUserSomehow(NotificationSource vs) {
        long now = System.currentTimeMillis();

        // Clobber the vibration request if we probably recently did such a thing
        if ((now - lastVibrateTime < NOTIFY_SUPPRESS_THRESHOLD)) {
            vs = NotificationSource.NONE;
        }

        String vpref;
        long[] vpattern;

        String spref;
        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        switch(vs) {
            case BREAK:
                vpref = "prf_vibr_jb";
                spref = "prf_sound_jb";
                vpattern = VIBRATE_PATTERN_NOW;
                break;
            case FLAG:
                vpref = "prf_vibr_flag";
                spref = "prf_sound_flag";
                vpattern = VIBRATE_PATTERN_FLAG;
                break;
            case MESG:
                vpref = "prf_vibr_mesg";
                spref = "prf_sound_mesg";
                vpattern = VIBRATE_PATTERN_MSG;
                break;
            case NONE:
            default:
                userNoteBuilder.setVibrate(null);
                return;
        }

        // Cam: default value is "false" because we really don't want to be vibrating if we
        //      accidentally lose our preferences somehow
        if (PreferenceManager.getDefaultSharedPreferences(mService.getBaseContext())
                .getBoolean(vpref, false)) {
            userNoteBuilder.setVibrate(vpattern);
            lastVibrateTime = now;
        }
        else {
            userNoteBuilder.setVibrate(null);
        }

        if (PreferenceManager.getDefaultSharedPreferences(mService.getBaseContext())
            .getBoolean(spref, false)) {
            userNoteBuilder.setSound(soundUri);
        } else {
            userNoteBuilder.setSound(null);
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
            lastContextTextSource = LastContentTextSource.FLAG;
            userNoteBuilder.setContentText(null);
            refreshNotification();
        }
    }
    void ensureNoNotification(boolean remove) {
        synchronized (this) {
            if (userNoteSC != null) {
                mService.stopForeground(remove);
                mService.unbindService(userNoteSC);
                userNoteSC = null;
            }
        }
    }

}
