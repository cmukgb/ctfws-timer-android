package com.acmetensortoys.ctfwstimer.lib;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;

public class CtFwSGameStateManager {

    private final TimerProvider mT;

    public CtFwSGameStateManager(TimerProvider t) {
        mT = t;
    }

    private class Game {
        // Game time
        private boolean configured = false;
        private long startT;     // POSIX seconds for game start
        private int setupD;
        private int rounds;
        private int roundD;
        private int gameIx;
        private long endT = 0;   // POSIX seconds for game end (if >= startT)

        public int  flagsTotal;

        public boolean equals(Game g) {
            return     (this.configured == g.configured)
                    && (this.startT == g.startT)
                    && (this.setupD == g.setupD)
                    && (this.rounds == g.rounds)
                    && (this.roundD == g.roundD)
                    && (this.gameIx == g.gameIx)
                    && (this.endT == g.endT)
                    && (this.flagsTotal == g.flagsTotal);
        }
    };
    private Game curstate = new Game();

    public synchronized void fromMqttConfigMessage(String st) {
        Game g = new Game();

        String tm = st.trim();
        switch (tm) {
            case "none":
                g.configured = false;
                break;
            default:
                try {
                    Scanner s = new Scanner(tm);
                    g.startT = s.nextLong();
                    g.setupD = s.nextInt();
                    g.rounds = s.nextInt();
                    g.roundD = s.nextInt();
                    g.flagsTotal = s.nextInt();
                    g.gameIx = s.nextInt();
                    g.configured = true;
                } catch (NoSuchElementException e) {
                    g.configured = false;
                }
                break;
        }
        if (!curstate.equals(g)) {
            curstate = g;
            notifyConfigEtAl();
        }
    }
    public synchronized String toMqttConfigMessage() {
        if (!curstate.configured) {
            return "none";
        }

        return String.format(Locale.ROOT, "%d %d %d %d %d",
                curstate.startT, curstate.setupD, curstate.rounds,
                curstate.roundD, curstate.flagsTotal);
    }
    public synchronized void deconfigure() {
        curstate.configured = false;
        notifyConfigEtAl();
    }
    public synchronized void setEndT(long endT) {
            if (endT != curstate.endT) {
                curstate.endT = endT;
                notifyConfigEtAl();
            }
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
            if (!curstate.configured) {
                res.rationale = "Game not configured!";
                res.stop = true;
            } else if (curstate.endT >= curstate.startT) {
                res.rationale = "Game declared over!";
                res.stop = true;
                res.past = true;
            } else if (now < curstate.startT) {
                res.rationale = "Start time in the future!";
                res.roundStart = res.roundEnd = curstate.startT;
            }
            if (res.rationale != null) {
                return res;
            }
            long elapsed = now - curstate.startT;
            if (elapsed < curstate.setupD) {
                res.round = 0;
                res.roundStart = curstate.startT;
                res.roundEnd = curstate.startT + curstate.setupD;
                return res;
            }
            elapsed -= curstate.setupD;
            res.round = (int) (elapsed / curstate.roundD);
            if (res.round >= curstate.rounds) {
                res.rationale = "Game time up!";
                res.stop = true;
                res.past = true;
                return res;
            }
            res.roundStart = curstate.startT + curstate.setupD + (res.round * curstate.roundD);
            res.roundEnd = res.roundStart + curstate.roundD;
            res.round += 1;
            return res;
        }
    }

    // Callbacks used for observers, which run in synchronized(this) context.  Generally
    // unwise to call these outside such a context.
    public boolean isConfigured(){ return curstate.configured; }
    public int getGameIx() { return curstate.gameIx; }
    public long getStartT() { return curstate.startT; }
    public long getFirstRoundStartT() { return curstate.startT + curstate.setupD; }
    public int getRounds() { return curstate.rounds; }
    public int getComputedGameDuration() { return curstate.rounds * curstate.roundD ; }
    public int getFlagsTotal() { return curstate.flagsTotal; }
    // Leaves off the natural endT comparison so that messages can be posted after the
    // game ends and still count as part of this one (i.e. still be displayed).
    private boolean isMessageTimeWithin(long time) {
        return !curstate.configured  || time >= curstate.startT;
    }

    // Game score
    private class Flags {
        public boolean flagsVisible = false;
        public int  flagsRed = 0;
        public int  flagsYel = 0;

        public boolean equals(Flags f) {
            return     (this.flagsVisible == f.flagsVisible)
                    && (this.flagsRed == f.flagsRed)
                    && (this.flagsYel == f.flagsYel);
        }
    }
    private Flags curflags = new Flags();
    public synchronized void fromMqttFlagsMessage(String st) {
        Flags f = new Flags();
        String tm = st.trim();
        switch(tm) {
            case "?":
                f.flagsVisible = false;
                break;
            default:
                Scanner s = new Scanner(tm);
                try {
                    f.flagsVisible = true;
                    int red = s.nextInt();
                    int yel = s.nextInt();
                    f.flagsRed = red;
                    f.flagsYel = yel;
                } catch (NumberFormatException e) {
                    f.flagsVisible = false;
                }
        }
        if (!curflags.equals(f)) {
            curflags = f;
            notifyFlags();
        }
    }
    public synchronized String toMqttFlagsMessage() {
        if (!curstate.configured || !curflags.flagsVisible) {
            return "?";
        }

        return String.format(Locale.ROOT, "%d %d", curflags.flagsRed, curflags.flagsYel);
    }
    public boolean getFlagsVisible() { return curflags.flagsVisible; }
    // Only sensible if flags visible
    public int getFlagsRed() { return curflags.flagsRed; }
    public int getFlagsYel() { return curflags.flagsYel; }

    // Informative messages handling
    public class Msg implements Comparable<Msg> {
        public final long when;
        public final String msg;

        Msg(long when, String msg) {
            this.when = when;
            this.msg  = msg;
        }

        public int compareTo(Msg m) {
            if (this.when == m.when) {
                return this.msg.compareTo(m.msg);
            }
            return (Long.valueOf(when).compareTo(Long.valueOf(m.when)));
        }
    }
    private final List<Msg> msgs = new ArrayList<>();
    private long lastMsgTimestamp;
    public void onNewMessage(String str) {
        Msg m = null;

        Scanner s = new Scanner(str);
        long t = 0;

        try {
            t = s.nextLong();
        } catch (NoSuchElementException nse) {
            // Maybe they forgot a time stamp; use round start.
            // That's not ideal, but... fake it?
            // XXX Back off a bit, for time sync reasons
            synchronized (this) {
                lastMsgTimestamp = 0;
                m = new Msg(lastMsgTimestamp, str);
            }
        }

        if (m == null) {
            s.useDelimiter("\\z");
            new Msg(t, s.next().trim());
        }

        synchronized (this) {
            // If there is no configuration, assume the message is new enough
            // If there *is* a configuration, check the time.
            if (isMessageTimeWithin(t) && (lastMsgTimestamp <= t)) {
                lastMsgTimestamp = t;
                if (!msgs.contains(m)) {
                    msgs.add(m);
                    notifyMessages();
                }
            }
        }
    }

    // Observer interface
    public interface Observer {
        // Called when the game configuration parameters change
        void onCtFwSConfigure(CtFwSGameStateManager game);
        // Called when the game parameters change and at round boundaries during the game;
        // this is an excellent thing to hook for UI update (including updating one's own,
        // more finely-grained timers).  The Now argument captures the state of the game
        // immediately prior to the dispatch of callbacks.
        void onCtFwSNow(CtFwSGameStateManager game, Now now);
        // Called when a flag message arrives.
        void onCtFwSFlags(CtFwSGameStateManager game);
        // Called when a human-readable message arrives or when a new game starts (to
        // empty the list), and is given the entire set of messages received this game
        // (or since the last), even though usually one only cares about the most recent
        // entry on the list.  We reserve the right to trim this list in the future, but
        // at the moment we do not.  Callees should not alter the list in any way.
        void onCtFwSMessage(CtFwSGameStateManager game, List<Msg> msgs);
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
            if (curstate.configured) {
                d.onCtFwSFlags(this);
                d.onCtFwSNow(this, getNow(mT.wallMS()));
            }
        }
    }
    public synchronized void unregisterObserver(Observer d) { mObsvs.remove(d); }
}
