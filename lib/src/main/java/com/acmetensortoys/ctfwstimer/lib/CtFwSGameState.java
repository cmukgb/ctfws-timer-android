package com.acmetensortoys.ctfwstimer.lib;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;

public class CtFwSGameState {

    public interface TimerProvider {
        long wallMS();
        void postDelay(Runnable r, long delayMS);
        void cancelPost(Runnable r);
    }
    private TimerProvider mT;

    public CtFwSGameState (TimerProvider t) {
        mT = t;
    }

    // Game time

    private boolean configured = false;
    private long startT;     // NTP seconds for game start
    private int  setupD;
    private int  rounds;
    private int  roundD;
    private long endT = 0;   // NTP seconds for game end (if >= startT)

    public void fromMqttConfigMessage(String st) {
        String tm = st.trim();
        switch (tm) {
            case "none":
                this.configured = false;
                break;
            default:
                try {
                    Scanner s = new Scanner(tm);
                    this.startT    =  s.nextLong();
                    this.setupD     = s.nextInt();
                    this.rounds     = s.nextInt();
                    this.roundD     = s.nextInt();
                    this.flagsTotal = s.nextInt();
                    this.configured = true;
                } catch (NoSuchElementException e) {
                    this.configured = false;
                }
                break;
        }
        notifyConfigEtAl();
    }
    public String toMqttConfigMessage() {
        if (!configured) {
            return "none";
        }

        return String.format(Locale.ROOT, "%d %d %d %d %d", startT, setupD, rounds, roundD, flagsTotal);
    }
    public void deconfigure() {
        this.configured = false;
        notifyConfigEtAl();
    }
    public void setEndT(long endT) {
        this.endT = endT;
        notifyConfigEtAl();
    }

    public class Now {
        public String rationale = null; // null if game is in play, otherwise other fields invalid
        public boolean stop = false;
        public int round = 0;  // 0 for setup
        public long roundStart = 0, roundEnd = 0; // NTP seconds

        public long wallMS; // timestamp at object creation: NTP time * 1000 (i.e. msec)
    }
    public Now getNow(long wallMS) {
        Now res = new Now();
        res.wallMS = wallMS;

        long now = wallMS/1000;
        if (!configured) {
            res.rationale = "Game not configured!";
            res.stop = true;
        } else if (endT >= startT) {
            res.rationale = "Game over!";
            res.stop = true;
        } else if (now < startT) {
            res.rationale = "Start time in the future!";
            res.roundStart = res.roundEnd = startT;
        }
        if (res.rationale != null) {
            return res;
        }
        long elapsed = now - startT;
        if (elapsed < setupD) {
            res.round = 0;
            res.roundStart = startT;
            res.roundEnd = startT + setupD;
            return res;
        }
        elapsed -= setupD;
        res.round = (int)(elapsed / roundD);
        if (res.round >= rounds) {
            res.rationale = "Game over!";
            res.stop = true;
            return res;
        }
        res.roundStart = startT + setupD + (res.round * roundD);
        res.roundEnd = res.roundStart + roundD;
        res.round += 1;
        return res;
    }
    public boolean isConfigured(){
        return configured;
    }
    public long getStartT() { return startT; }
    public long getFirstRoundStartT() { return startT + setupD; }
    public int getRounds() { return rounds; }
    public int getComputedGameDuration() { return rounds * roundD ; }

    // Leaves off the natural endT comparison so that messages can be posted after the
    // game ends and still count as part of this one (i.e. still be displayed).
    private boolean isMessageTimeWithin(long time) {
        return !configured  || time >= startT;
    }

    // Game score

    public int  flagsTotal;
    public boolean flagsVisible = false;
    public int  flagsRed = 0;
    public int  flagsYel = 0;

    public void fromMqttFlagsMessage(String st) {
        String tm = st.trim();
        switch(tm) {
            case "?":
                flagsVisible = false;
                break;
            default:
                Scanner s = new Scanner(tm);
                try {
                    flagsVisible = true;
                    int red = s.nextInt();
                    int yel = s.nextInt();
                    flagsRed = red;
                    flagsYel = yel;
                } catch (NumberFormatException e) {
                    flagsVisible = false;
                }
        }
        notifyFlags();
    }
    public String toMqttFlagsMessage() {
        if (!configured || !flagsVisible) {
            return "?";
        }

        return String.format(Locale.ROOT, "%d %d", flagsRed, flagsYel);
    }

    // Informative messages handling

    public class Msg {
        public long when;
        public String msg;

        Msg(long when, String msg) {
            this.when = when;
            this.msg  = msg;
        }
    }
    private List<Msg> msgs = new ArrayList<>();
    private long lastMsgTimestamp;

    public void onNewMessage(String str) {
        Scanner s = new Scanner(str);
        long t;

        try {
            t = s.nextLong();
        } catch (NoSuchElementException nse) {
            // Maybe they forgot a time stamp.  That's not ideal, but... fake it?
            // XXX Back off a bit, for time sync reasons
            lastMsgTimestamp = mT.wallMS()/1000 - 30;
            msgs.add(new Msg(lastMsgTimestamp, str));
            notifyMessages();
            return;
        }

        // If there is no configuration, assume the message is new enough
        // If there *is* a configuration, check the time.
        if (isMessageTimeWithin(t) && (lastMsgTimestamp <= t)) {
            s.useDelimiter("\\z");
            lastMsgTimestamp = t;
            msgs.add(new Msg(lastMsgTimestamp, s.next().trim()));
            notifyMessages();
        }
    }

    // Observer interface

    public interface Observer {
        void onCtFwSConfigure(CtFwSGameState game);
        void onCtFwSNow(CtFwSGameState game, Now now);
        void onCtFwSFlags(CtFwSGameState game);
        void onCtFwSMessage(CtFwSGameState game, List<Msg> msgs);
    }
    final private Set<Observer> mObsvs = new HashSet<>();
    private void notifyFlags() {
        synchronized(this) {
            for (Observer o : mObsvs) { o.onCtFwSFlags(this); }
        }
    }
    private void notifyMessages() {
        synchronized(this) {
            for (Observer o : mObsvs) { o.onCtFwSMessage(this, msgs); }
        }
    }
    private void notifyConfigEtAl() {
        if (!isMessageTimeWithin(lastMsgTimestamp)) {
            msgs.clear();
            notifyMessages();
        }
        synchronized(this) {
            for (Observer o : mObsvs) { o.onCtFwSConfigure(this); }
        }
        notifyNow();
    }
    private final Runnable futureNotifyNow = new Runnable() {
        @Override
        public void run() {
            notifyNow();
        }
    };
    private void notifyNow() {
        mT.cancelPost(futureNotifyNow);
        Now n = getNow(mT.wallMS());
        synchronized(this) {
            for (Observer o : mObsvs) {
                o.onCtFwSNow(this, n);
            }
            if (n.rationale == null || !n.stop) {
                mT.postDelay(futureNotifyNow, n.roundEnd*1000 - n.wallMS);
            }
        }
    }
    public void registerObserver(Observer d) {
        synchronized(this) { mObsvs.add(d); }
    }
    public void unregisterObserver(Observer d) {
        synchronized(this) { mObsvs.remove(d); }
    }
}
