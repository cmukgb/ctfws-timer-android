package com.acmetensortoys.ctfwstimer;

import java.util.NoSuchElementException;
import java.util.Scanner;

class CtFwSGameState {
    boolean configured;
    long startT;     // NTP seconds for game start
    int  setupD;
    int  rounds;
    int  roundD;
    long endT = 0;   // NTP seconds for game end (if >= startT)

    int  flagsTotal;
    boolean flagsVisible = false;
    int  flagsRed = 0;
    int  flagsYel = 0;

    void setFlags(boolean visible) {
        flagsVisible = visible;
    }
    void setFlags(int red, int yel) {
        flagsRed = red; flagsYel = yel;
    }

    class Now {
        String rationale = null; // null if game is in play, otherwise other fields invalid
        int round = 0;  // 0 for setup
        long roundStart = 0, roundEnd = 0; // NTP seconds
        boolean stop = false;
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
