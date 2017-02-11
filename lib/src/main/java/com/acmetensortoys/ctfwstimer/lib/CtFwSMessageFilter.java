package com.acmetensortoys.ctfwstimer.lib;

import java.util.NoSuchElementException;
import java.util.Scanner;

public class CtFwSMessageFilter {
    final private CtFwSGameState mCgs;
    public CtFwSMessageFilter(CtFwSGameState cgs) {
        mCgs = cgs;
    }

    public class Msg {
        public long when;
        public String msg;

        public Msg(long when, String msg) {
            this.when = when;
            this.msg  = msg;
        }
    }

    private long lastMsgTimestamp = 0;

    public Msg filter(String str) {
        Scanner s = new Scanner(str);
        long t;
        try {
            t = s.nextLong();
        } catch (NoSuchElementException nse) {
            // Maybe they forgot a time stamp.  That's not ideal, but... fake it?
            // XXX Back off a bit, for time sync reasons
            lastMsgTimestamp = System.currentTimeMillis()/1000 - 30;
            return new Msg(lastMsgTimestamp, str);
        }

        // If there is no configuration, assume the message is new enough
        // If there *is* a configuration, check the time.
        if ((!mCgs.configured  || t >= mCgs.startT) && (lastMsgTimestamp <= t)) {
            s.useDelimiter("\\z");
            lastMsgTimestamp = t;
            return new Msg(lastMsgTimestamp, s.next().trim());
        }

        return null;
    }
}
