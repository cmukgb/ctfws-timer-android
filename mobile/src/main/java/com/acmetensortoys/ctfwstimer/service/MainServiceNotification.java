package com.acmetensortoys.ctfwstimer.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import androidx.core.app.NotificationCompat;
import android.util.Log;

import com.acmetensortoys.ctfwstimer.activity.MainActivity;
import com.acmetensortoys.ctfwstimer.R;
import com.acmetensortoys.ctfwstimer.lib.CtFwSGameStateManager;

import java.text.NumberFormat;
import java.util.SortedSet;

class MainServiceNotification {
    final public String CTFWS_GAME_CHANNEL_ID = "GAME";

    final private MainService mService;
    private final NotificationCompat.Builder userNoteBuilder;

    private long lastVibrateTime;

    private enum NotificationSource { NONE, BREAK, FLAG, MESG }
    private enum LastContentTextSource { NONE, FLAG, MESG }
    private LastContentTextSource lastContextTextSource = LastContentTextSource.NONE;

    MainServiceNotification(MainService ms, CtFwSGameStateManager game){
        mService = ms;

        Intent ni = new Intent(ms, MainActivity.class);
        ni.setAction(Intent.ACTION_MAIN);
        ni.addCategory(Intent.CATEGORY_LAUNCHER);
        ni.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel nc = new NotificationChannel(
                    CTFWS_GAME_CHANNEL_ID,
                    "Game Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT);
            nc.enableVibration(true);
            nc.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager nm = (NotificationManager)
                    ms.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.createNotificationChannel(nc);
        }

        userNoteBuilder = new NotificationCompat.Builder(ms, CTFWS_GAME_CHANNEL_ID)
                .setOnlyAlertOnce(false)
                .setSmallIcon(R.drawable.ic_hammer_and_sickle)
                .setContentIntent(PendingIntent.getActivity(ms, 0, ni, 0));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            userNoteBuilder.setVisibility(Notification.VISIBILITY_PUBLIC);
        }

        game.registerObserver(new CtFwSGameStateManager.Observer() {
            @Override
            public void onCtFwSConfigure(CtFwSGameStateManager game) { }

            @Override
            public void onCtFwSNow(CtFwSGameStateManager game, CtFwSGameStateManager.Now now) {
                if (now.rationale == CtFwSGameStateManager.NowRationale.NR_GAME_IN_PROGRESS
                        || now.rationale == CtFwSGameStateManager.NowRationale.NR_START_FUTURE) {
                    // game is afoot or in the future!

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        userNoteBuilder.setWhen((now.roundEnd + 1) * 1000);
                    } else {
                        userNoteBuilder.setWhen(now.roundStart * 1000);
                    }
                    userNoteBuilder.setUsesChronometer(true);

                    Resources rs = mService.getResources();

                    if (now.rationale == CtFwSGameStateManager.NowRationale.NR_GAME_IN_PROGRESS) {
                        userNoteBuilder.setSubText(rs.getString(R.string.notify_game_afoot));
                        String ct;
                        if (now.round == 0) {
                            ct = rs.getString(R.string.notify_game_setup);
                        } else if (now.round == game.getRounds()) {
                            ct = rs.getString(R.string.notify_game_end_soon);
                        } else {
                            ct = String.format(rs.getString(R.string.notify_jailbreak),
                                    now.round, game.getRounds() - 1);
                        }
                        userNoteBuilder.setContentTitle(ct);
                    } else {
                        userNoteBuilder.setSubText(rs.getString(R.string.notify_start_future));
                    }

                    notifyUserSomehow(NotificationSource.BREAK);
                    ensureNotification();
                } else {
                    String txt;
                    switch(now.rationale) {
                        default:
                            txt = "";
                            break;
                        case NR_TIME_UP:
                        case NR_EXPLICIT_END:
                            txt = mService.getResources().getString(R.string.notify_game_over);
                            break;
                        case NR_NOT_CONFIG:
                            txt = mService.getResources().getString(R.string.notify_not_config);
                            break;
                    }

                    // game no longer afoot
                    if (now.past) {
                        notifyUserSomehow(NotificationSource.BREAK);
                        userNoteBuilder.setUsesChronometer(false);
                        userNoteBuilder.setShowWhen(false);
                        userNoteBuilder.setContentTitle(txt);
                        userNoteBuilder.setSubText(txt);
                        refreshNotification();
                    }
                    ensureNoNotification(!now.past);
                }
            }

            @Override
            public void onCtFwSFlags(CtFwSGameStateManager game) {
                // If flags are hidden or there aren't any captured (e.g. this is a notification
                // of a reset to 0), don't do anything, unless the flags were the last thing
                // asserted, in which case, we allow a correction.
                if (game.getFlagsVisible()
                        && ((lastContextTextSource == LastContentTextSource.FLAG)
                            || (game.getFlagsRed() + game.getFlagsYel() > 0))) {
                    notifyUserSomehow(NotificationSource.FLAG);
                    lastContextTextSource = LastContentTextSource.FLAG;
                    NumberFormat nf = NumberFormat.getIntegerInstance();
                    userNoteBuilder.setContentText(
                            String.format(mService.getResources().getString(R.string.notify_flags),
                                    nf.format(game.getFlagsRed()), nf.format(game.getFlagsYel())));
                    refreshNotification();
                }
            }

            private int lastMsgIx = 0;

            @Override
            public void onCtFwSMessage(CtFwSGameStateManager game, SortedSet<CtFwSGameStateManager.Msg> msgs) {
                // Only do anything if we have added something to the list since last we looked
                // and if it's in (or after) the current game.
                // Always update the length in case this is a reset to zero.
                int s = msgs.size();
                Log.d("CtFwSNotify", "on msg s=" + s + " lastix=" + lastMsgIx);
                if (s > lastMsgIx) {
                    CtFwSGameStateManager.Msg m = msgs.last();
                    Log.d("CtFwsNotify", "msg gst=" + game.getStartT() + " when=" + m.when);
                    if (game.isConfigured() && m.when >= game.getStartT()) {
                        notifyUserSomehow(NotificationSource.MESG);
                        lastContextTextSource = LastContentTextSource.MESG;
                        userNoteBuilder.setContentText(m.msg);
                        refreshNotification();
                    }
                } else {
                    // This is a message reset event that pruned something.  It might
                    // have emptied the list, and if so, we should clear out the notification's
                    // content text.  If the list isn't empty, whatever we put up last is
                    // just fine to stay there.
                    if (lastContextTextSource == LastContentTextSource.MESG && s == 0) {
                        lastContextTextSource = LastContentTextSource.NONE;
                        userNoteBuilder.setContentText(null);
                        // Suppress vibe, we're just updating the text
                        notifyUserSomehow(NotificationSource.NONE);
                        refreshNotification();
                    }
                }
                lastMsgIx = s;
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
            lastContextTextSource = LastContentTextSource.NONE;
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
