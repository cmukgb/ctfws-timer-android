package com.acmetensortoys.ctfwstimer.lib;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;

public class CtFwSGameState {

    private final TimerProvider mT;

    public CtFwSGameState (TimerProvider t) {
        mT = t;
    }

    // Game time
    private boolean configured = false;
    private long startT;     // POSIX seconds for game start
    private int  setupD;
    private int  rounds;
    private int  roundD;
    private int  gameIx;
    private long endT = 0;   // POSIX seconds for game end (if >= startT)

    public synchronized void fromMqttConfigMessage(String st) {
        String tm = st.trim();
        switch (tm) {
            case "none":
                this.configured = false;
                break;
            default:
                try {
                    Scanner s = new Scanner(tm);
                    this.startT = s.nextLong();
                    this.setupD = s.nextInt();
                    this.rounds = s.nextInt();
                    this.roundD = s.nextInt();
                    this.flagsTotal = s.nextInt();
                    this.gameIx = s.nextInt();
                    this.configured = true;
                } catch (NoSuchElementException e) {
                    this.configured = false;
                }
                break;
        }
        notifyConfigEtAl();
    }
    public synchronized String toMqttConfigMessage() {
        if (!configured) {
            return "none";
        }

        return String.format(Locale.ROOT, "%d %d %d %d %d",
                startT, setupD, rounds, roundD, flagsTotal);
    }
    public synchronized void deconfigure() {
        this.configured = false;
        notifyConfigEtAl();
    }
    public synchronized void setEndT(long endT) {
            this.endT = endT;
            notifyConfigEtAl();
    }

    public class Now {
        public String rationale = null; // null if game is in play, otherwise other fields invalid
        public boolean stop = false;
        public boolean past = false;
        public int round = 0;  // 0 for setup
        public long roundStart = 0, roundEnd = 0; // POSIX seconds

        public long wallMS; // timestamp at object creation: POSIX time * 1000 (i.e. msec)
    }

    public Now getNow(long wallMS) {
        Now res = new Now();
        res.wallMS = wallMS;
        long now = wallMS/1000;

        synchronized (this) {
            if (!configured) {
                res.rationale = "Game not configured!";
                res.stop = true;
            } else if (endT >= startT) {
                res.rationale = "Game declared over!";
                res.stop = true;
                res.past = true;
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
            res.round = (int) (elapsed / roundD);
            if (res.round >= rounds) {
                res.rationale = "Game time up!";
                res.stop = true;
                res.past = true;
                return res;
            }
            res.roundStart = startT + setupD + (res.round * roundD);
            res.roundEnd = res.roundStart + roundD;
            res.round += 1;
            return res;
        }
    }

    // Callbacks used for observers, which run in synchronized(this) context.  Generally
    // unwise to call these outside such a context.
    public boolean isConfigured(){ return configured; }
    public int getGameIx() { return gameIx; }
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
    public synchronized void fromMqttFlagsMessage(String st) {
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
    public synchronized String toMqttFlagsMessage() {
        if (!configured || !flagsVisible) {
            return "?";
        }

        return String.format(Locale.ROOT, "%d %d", flagsRed, flagsYel);
    }

    // Informative messages handling
    public class Msg {
        public final long when;
        public final String msg;

        Msg(long when, String msg) {
            this.when = when;
            this.msg  = msg;
        }
    }
    private final List<Msg> msgs = new ArrayList<>();
    private long lastMsgTimestamp;
    public void onNewMessage(String str) {
        Scanner s = new Scanner(str);
        long t;

        try {
            t = s.nextLong();
        } catch (NoSuchElementException nse) {
            // Maybe they forgot a time stamp.  That's not ideal, but... fake it?
            // XXX Back off a bit, for time sync reasons
            synchronized (this) {
                lastMsgTimestamp = mT.wallMS() / 1000 - 30;
                msgs.add(new Msg(lastMsgTimestamp, str));
                notifyMessages();
                return;
            }
        }

        s.useDelimiter("\\z");
        String msg = s.next().trim();

        synchronized (this) {
            // If there is no configuration, assume the message is new enough
            // If there *is* a configuration, check the time.
            if (isMessageTimeWithin(t) && (lastMsgTimestamp <= t)) {
                lastMsgTimestamp = t;
                msgs.add(new Msg(t, msg));
                notifyMessages();
            }
        }
    }

    // Observer interface
    public interface Observer {
        // Called when the game configuration parameters change
        void onCtFwSConfigure(CtFwSGameState game);
        // Called when the game parameters change and at round boundaries during the game;
        // this is an excellent thing to hook for UI update (including updating one's own,
        // more finely-grained timers).  The Now argument captures the state of the game
        // immediately prior to the dispatch of callbacks.
        void onCtFwSNow(CtFwSGameState game, Now now);
        // Called when a flag message arrives.
        void onCtFwSFlags(CtFwSGameState game);
        // Called when a human-readable message arrives or when a new game starts (to
        // empty the list), and is given the entire set of messages received this game
        // (or since the last), even though usually one only cares about the most recent
        // entry on the list.  We reserve the right to trim this list in the future, but
        // at the moment we do not.  Callees should not alter the list in any way.
        void onCtFwSMessage(CtFwSGameState game, List<Msg> msgs);
    }
    final private Set<Observer> mObsvs = new HashSet<>();
    private synchronized void notifyFlags() {
        for (Observer o : mObsvs) { o.onCtFwSFlags(this); }
    }
    private synchronized void notifyMessages() {
        for (Observer o : mObsvs) { o.onCtFwSMessage(this, msgs); }
    }
    private synchronized void notifyConfigEtAl() {
        if (!isMessageTimeWithin(lastMsgTimestamp)) {
            msgs.clear();
            notifyMessages();
        }
        for (Observer o : mObsvs) { o.onCtFwSConfigure(this); }
        notifyNow();
    }
    private final Runnable futureNotifyNow = new Runnable() {
        @Override
        public void run() {
            notifyNow();
        }
    };

    private synchronized void notifyNow() {
        mT.cancelPost(futureNotifyNow);
        Now n = getNow(mT.wallMS());
        if (n.rationale == null || !n.stop) {
            mT.postDelay(futureNotifyNow, n.roundEnd*1000 - n.wallMS);
        }
        for (Observer o : mObsvs) { o.onCtFwSNow(this, n); }
    }
    public synchronized void registerObserver(Observer d) {
        if (mObsvs.add(d)) {
            // Synchronize observer with game state as of right now.
            d.onCtFwSConfigure(this);
            d.onCtFwSMessage(this, msgs);
            if (configured) {
                d.onCtFwSFlags(this);
                d.onCtFwSNow(this, getNow(mT.wallMS()));
            }
        }
    }
    public synchronized void unregisterObserver(Observer d) { mObsvs.remove(d); }
}
