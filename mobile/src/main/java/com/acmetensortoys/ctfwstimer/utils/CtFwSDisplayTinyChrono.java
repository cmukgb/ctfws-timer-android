package com.acmetensortoys.ctfwstimer.utils;

import android.content.res.Resources;
import android.os.Build;
import android.os.SystemClock;
import android.widget.Chronometer;

import com.acmetensortoys.ctfwstimer.R;
import com.acmetensortoys.ctfwstimer.lib.CtFwSGameStateManager;

import java.util.SortedSet;

/*
 * For our non-primary displays, we probably want a simple count-up timer
 * in the headers.  This provides that for us.
 */

public class CtFwSDisplayTinyChrono implements CtFwSGameStateManager.Observer {

    private final Chronometer mCh;
    private final Resources mRes;

    public CtFwSDisplayTinyChrono(Resources res, Chronometer ch) {
        mCh = ch;
        mRes = res;
    }

    @Override
    public void onCtFwSConfigure(final CtFwSGameStateManager gs) {
        if (!gs.isConfigured()) {
            mCh.post(() -> mCh.setText(R.string.ctfws_chrono_nogame));
        }
    }

    @Override
    public void onCtFwSNow(final CtFwSGameStateManager gs, final CtFwSGameStateManager.Now now) {
        final long tbcf = System.currentTimeMillis() - SystemClock.elapsedRealtime();

        mCh.post(() -> {
            mCh.stop();

            if (now.rationale != CtFwSGameStateManager.NowRationale.NR_GAME_IN_PROGRESS) {
                int rid = R.string.ctfws_chrono_nogame;
                switch(now.rationale) {
                    case NR_TIME_UP:
                    case NR_EXPLICIT_END:
                        rid = R.string.ctfws_chrono_over;
                        break;
                    case NR_START_FUTURE:
                        rid = R.string.ctfws_chrono_future;
                        break;
                    case NR_NOT_CONFIG:
                        rid = R.string.ctfws_chrono_nogame;
                        break;
                }
                mCh.setText(rid);
                return;
            }

            if (now.round == 0) {
                mCh.setFormat(mRes.getString(R.string.ctfws_chrono_gamestart));
            } else if (now.round == gs.getRounds()) {
                mCh.setFormat(mRes.getString(R.string.ctfws_chrono_gameend));
            } else {
                mCh.setFormat(String.format(mRes.getString(R.string.ctfws_chrono_jailbreak),
                                now.round));
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mCh.setCountDown(true);
            }
            mCh.setBase(now.roundEnd * 1000 - tbcf);
            mCh.start();
        });
    }

    @Override
    public void onCtFwSFlags(CtFwSGameStateManager gs) { }

    @Override
    public void onCtFwSMessage(CtFwSGameStateManager gs, SortedSet<CtFwSGameStateManager.Msg> msgs) { }

}