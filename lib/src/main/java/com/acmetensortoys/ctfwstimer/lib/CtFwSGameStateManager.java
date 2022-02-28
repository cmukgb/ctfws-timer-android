package com.acmetensortoys.ctfwstimer.lib;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.InputMismatchException;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class CtFwSGameStateManager {

    private final TimerProvider mT;

    public CtFwSGameStateManager(TimerProvider t) {
        mT = t;
    }

    // Objects.equals() is API 19. :(
    private static boolean carefulObjEq(Object a, Object b)
    {
        if (a == null) {
            return b == null;
        } else {
            if (b == null) {
                return false;
            } else {
                return a.equals(b);
            }
        }
    }

    private static class Game {
        // Game time
        private boolean configured = false;
        private long startT;     // POSIX seconds for game start
        private int setupD;
        private int rounds;
        private int roundD;
        private int gameIx;
        private long endT = 0;   // POSIX seconds for game end (if >= startT)

        public int  flagsTotal;
        public String sides_str;

        public boolean equals(Game g) {
            return     (this.configured == g.configured)
                    && (this.startT == g.startT)
                    && (this.setupD == g.setupD)
                    && (this.rounds == g.rounds)
                    && (this.roundD == g.roundD)
                    && (this.gameIx == g.gameIx)
                    && (this.endT == g.endT)
                    && (this.flagsTotal == g.flagsTotal)
                    && (carefulObjEq(this.sides_str, g.sides_str));
        }
    }
    private Game curstate = new Game();
    private String lastConfigMessage; // for debugging

    public synchronized void fromMqttConfigMessage(String st) {
        Game g = new Game();

        String tm = st.trim();
        switch (tm) {
            case "none":
                g.configured = false;
                break;
            default: {
                Scanner s = new Scanner(tm);
                try {
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

                try {
                    g.sides_str = s.next();
                } catch (NoSuchElementException e) {
                    g.sides_str = null;
                }
                break;
            }
        }
        lastConfigMessage = tm;
        if (!curstate.equals(g)) {
            curstate = g;
            notifyConfigEtAl();
        }
    }
    public String debugGetLastConfigMessage() { return lastConfigMessage; }
    public synchronized String toMqttConfigMessage() {
        if (!curstate.configured) {
            return "none";
        }

        return String.format(Locale.ROOT, "%d %d %d %d %d %d %s",
                curstate.startT, curstate.setupD, curstate.rounds,
                curstate.roundD, curstate.flagsTotal, curstate.gameIx,
                curstate.sides_str);
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

    public enum NowRationale {
        NR_GAME_IN_PROGRESS,
        NR_NOT_CONFIG,
        NR_EXPLICIT_END,
        NR_START_FUTURE,
        NR_TIME_UP,
    }

    public static class Now {
        public NowRationale rationale = NowRationale.NR_NOT_CONFIG;
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
                res.rationale = NowRationale.NR_NOT_CONFIG;
                res.stop = true;
                return res;
            } else if (curstate.endT >= curstate.startT) {
                res.rationale = NowRationale.NR_EXPLICIT_END;
                res.stop = true;
                res.past = true;
                return res;
            } else if (now < curstate.startT) {
                res.rationale = NowRationale.NR_START_FUTURE;
                res.roundStart = res.roundEnd = curstate.startT;
                return res;
            }
            long elapsed = now - curstate.startT;
            if (elapsed < curstate.setupD) {
                res.rationale = NowRationale.NR_GAME_IN_PROGRESS;
                res.round = 0;
                res.roundStart = curstate.startT;
                res.roundEnd = curstate.startT + curstate.setupD;
                return res;
            }
            elapsed -= curstate.setupD;
            res.round = (int) (elapsed / curstate.roundD);
            if (res.round >= curstate.rounds) {
                res.rationale = NowRationale.NR_TIME_UP;
                res.stop = true;
                res.past = true;
                return res;
            }
            res.rationale = NowRationale.NR_GAME_IN_PROGRESS;
            res.roundStart = curstate.startT + curstate.setupD + ((long)res.round * curstate.roundD);
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
    public String getSides() { return curstate.sides_str; }
    // Leaves off the natural endT comparison so that messages can be posted after the
    // game ends and still count as part of this one (i.e. still be displayed).
    private boolean isMessageTimeWithin(long time) {
        return !curstate.configured  || time >= curstate.startT;
    }

    // Game score
    private static class Flags {
        public boolean flagsVisible = false;
        public long flagsTime = 0;
        public BigInteger flagsRed = BigInteger.ZERO;
        public BigInteger flagsYel = BigInteger.ZERO;

        public boolean equals(Flags f) {
            return     (this.flagsVisible == f.flagsVisible)
                    && (carefulObjEq(this.flagsRed, f.flagsRed))
                    && (carefulObjEq(this.flagsYel, f.flagsYel));
        }
    }
    private Flags curflags = new Flags();
    public synchronized void fromMqttFlagsMessage(String st) {
        Flags f = new Flags();
        String tm = st.trim();
        Scanner s = new Scanner(tm);
        try {
            f.flagsTime = s.nextLong();
            f.flagsRed = s.nextBigInteger();
            f.flagsYel = s.nextBigInteger();
            f.flagsVisible = true;
        } catch (NumberFormatException | InputMismatchException e) {
            // XXX This isn't quite right.
            f.flagsVisible = false;
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

        return String.format(Locale.ROOT, "%d %d %d",
                curflags.flagsTime,
                curflags.flagsRed, curflags.flagsYel);
    }
    public boolean getFlagsVisible() { return curflags.flagsVisible; }
    // Only sensible if flags visible
    public BigInteger getFlagsRed() { return curflags.flagsRed; }
    public BigInteger getFlagsYel() { return curflags.flagsYel; }

    // Informative messages handling
    public static class Msg implements Comparable<Msg> {
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
            return Long.compare(this.when, m.when);
        }

        @Override
        public boolean equals(Object o) {
            if (! (o instanceof Msg)) {
                return false;
            }
            return 0 == this.compareTo((Msg)o);
        }
    }
    private SortedSet<Msg> msgs = new TreeSet<>();
    private long lastMsgTimestamp;
    public void onNewMessage(String str) {
        Msg m = null;

        Scanner s = new Scanner(str);
        long t = 0;

        try {
            t = s.nextLong();
            s.useDelimiter("\\z");
            m = new Msg(t, s.next().trim());
        } catch (NoSuchElementException nse) {
            // malformed message; shouldn't be a problem these days (while we used to
            // make up a timestamp for the entire string as the message, our tooling
            // always sends with timestamps now).
        }

        synchronized (this) {
            // Advance message clock monotonically; accept any message if there
            // is no configuration, or check the start time to suppress old
            // messages if we're just now coming online.
            //
            // XXX this is bogus
            if (isMessageTimeWithin(t) && (lastMsgTimestamp <= t)) {
                lastMsgTimestamp = t;
                if (msgs.add(m)) {
                    notifyMessages();
                }
            }
        }
    }
    public void onMessageReset(long before) {
        synchronized(this) {
            lastMsgTimestamp = Math.max(before, lastMsgTimestamp);
            if (!msgs.isEmpty() && msgs.first().when <= before) {
                msgs = msgs.tailSet(new Msg(before, ""));
            }
            notifyMessages();
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
        void onCtFwSMessage(CtFwSGameStateManager game, SortedSet<Msg> msgs);
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
        if (n.rationale == NowRationale.NR_GAME_IN_PROGRESS || !n.stop) {
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
