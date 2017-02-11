package com.acmetensortoys.ctfwstimer.lib;

import java.util.NoSuchElementException;
import java.util.Scanner;

public class CtFwSGameState {
    public boolean configured;
    public long startT;     // NTP seconds for game start
    public int  setupD;
    public int  rounds;
    public int  roundD;
    public long endT = 0;   // NTP seconds for game end (if >= startT)

    public int  flagsTotal;
    public boolean flagsVisible = false;
    public int  flagsRed = 0;
    public int  flagsYel = 0;

    public void setFlags(boolean visible) {
        flagsVisible = visible;
    }
    public void setFlags(int red, int yel) {
        flagsRed = red; flagsYel = yel;
    }

    public void mqttConfigMessage(String st) {
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
    }
    public void mqttFlagsMessage(String st) {
        String tm = st.trim();
        switch(tm) {
            case "?":
                this.setFlags(false);
                break;
            default:
                Scanner s = new Scanner(tm);
                try {
                    this.setFlags(true);
                    this.setFlags(s.nextInt(),s.nextInt());
                } catch (NumberFormatException e) {
                    this.setFlags(false);
                }
        }
    }

    public class Now {
        public String rationale = null; // null if game is in play, otherwise other fields invalid
        public int round = 0;  // 0 for setup
        public long roundStart = 0, roundEnd = 0; // NTP seconds
        public boolean stop = false;
    }
    public Now getNow(long now) {
        Now res = new Now();
        if (!configured) {
            res.rationale = "Game not configured";
            res.stop = true;
        } else if (endT >= startT) {
            res.rationale = "Game over!";
            res.stop = true;
        } else if (now <= startT) {
            res.rationale = "Start time in the future!";
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
}
