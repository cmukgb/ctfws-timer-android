package com.acmetensortoys.ctfwstimer;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Html;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.acmetensortoys.ctfwstimer.lib.CtFwSGameStateManager;

import java.util.List;
import java.util.ListIterator;

import static android.view.View.INVISIBLE;

// TODO nwf is bad at UI design; someone who isn't him should improve this
class CtFwSDisplayLocal implements CtFwSGameStateManager.Observer {
    final private Activity mAct;
    String gameStateLabelText;

    private StunTimer stun_short, stun_long;

    CtFwSDisplayLocal(Activity a) {
        mAct = a;
        gameStateLabelText = mAct.getResources().getString(R.string.header_gamestate0);

        stun_short = new StunTimer(
                (Chronometer)mAct.findViewById(R.id.ch_wait_short),
                (ProgressBar)mAct.findViewById(R.id.pb_wait_short),
                10000);
        wireTimer(R.id.btn_wait_short, stun_short);

        stun_long = new StunTimer(
                (Chronometer)mAct.findViewById(R.id.ch_wait_long),
                (ProgressBar)mAct.findViewById(R.id.pb_wait_long),
                60000);
        wireTimer(R.id.btn_wait_long, stun_long);
    }

    private void wireTimer(int vid, final StunTimer st) {
        mAct.findViewById(vid)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startTimer(st, System.currentTimeMillis());
                    }
                });
    }

    void timersToBundle(Bundle out, String key) {
        out.putLongArray(key, new long[]{ stun_short.wallEndMS, stun_long.wallEndMS });
    }
    void timersFromBundle(Bundle in, String key) {
        long[] es = in.getLongArray(key);
        if(es == null) { return; }
        if(es.length > 0) { resumeTimer(stun_short, es[0]); }
        if(es.length > 1) { resumeTimer(stun_long,  es[1]); }
    }

    private void doSetGameStateLabelText(final CtFwSGameStateManager gs,
                                         final CtFwSGameStateManager.Now now) {
        final Resources rs = mAct.getResources();
        int gameIndex = gs.getGameIx();
        CtFwSGameStateManager.NowRationale nr;

        if ((now == null) || !gs.isConfigured()) {
            nr = CtFwSGameStateManager.NowRationale.NR_NOT_CONFIG;
        } else {
            nr = now.rationale;
        }

        if (nr == CtFwSGameStateManager.NowRationale.NR_NOT_CONFIG || gameIndex == 0) {
            gameStateLabelText = String.format(rs.getString(R.string.header_gamestate0),
                    rs.getString(R.string.notify_not_config));
        } else {

            String sfx;
            switch (nr) {
                case NR_EXPLICIT_END:
                    sfx = rs.getString(R.string.notify_game_over);
                    break;
                case NR_TIME_UP:
                    sfx = rs.getString(R.string.notify_game_over);
                    break;
                case NR_START_FUTURE:
                    sfx = rs.getString(R.string.notify_start_future);
                    break;
                case NR_GAME_IN_PROGRESS:
                    if (now.round == 0) {
                        sfx = rs.getString(R.string.notify_game_setup);
                    } else if (now.round == gs.getRounds()) {
                        sfx = rs.getString(R.string.notify_game_end_soon);
                    } else {
                        sfx = rs.getString(R.string.notify_game_afoot);
                    }
                    break;
                case NR_NOT_CONFIG:
                    // Handled above; fallthru to placate static analysers
                default:
                    sfx = "";
            }

            gameStateLabelText = String.format(rs.getString(R.string.header_gamestateN),
                    gameIndex, sfx);
        }

        final TextView gstv = mAct.findViewById(R.id.header_gamestate);
        gstv.post(new Runnable() {
            @Override
            public void run() {
                gstv.setText(gameStateLabelText);
            }
        });
    }

    private Spanned htmlFromStrResId(int id) {
        if (Build.VERSION.SDK_INT >= 24) {
            return Html.fromHtml(mAct.getResources().getString(id), 0);
        } else {
            return Html.fromHtml(mAct.getResources().getString(id));
        }
    }

    private void doSetSidesText(final CtFwSGameStateManager gs) {
        final TextView stv = mAct.findViewById(R.id.header_sides);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                stv.setText("");
            }
        };

        if (gs.isConfigured()) {
            String ss = gs.getSides();
            if (ss != null) {
                final Spanned h;

                switch(ss) {
                    case "wd" : h = htmlFromStrResId(R.string.ctfws_sides_wd); break;
                    case "dw" : h = htmlFromStrResId(R.string.ctfws_sides_dw); break;
                    default   : h = htmlFromStrResId(R.string.ctfws_unknown_sides); break;
                }

                r = new Runnable() {
                    @Override
                    public void run() {
                        stv.setText(h);
                    }
                };
            }
        }

        stv.post(r);
    }

    @Override
    public void onCtFwSConfigure(final CtFwSGameStateManager gs) {
        doSetGameStateLabelText(gs, null);
        doSetSidesText(gs);
    }

    @Override
    public void onCtFwSNow(final CtFwSGameStateManager gs, final CtFwSGameStateManager.Now now) {
        // time base correction factor ("when we booted"-ish)
        final long tbcf = System.currentTimeMillis() - SystemClock.elapsedRealtime();

        Log.d("CtFwS", "Display game state; nowMS=" + now.wallMS + " r=" + now.round + " rs=" + now.roundStart + " re=" + now.roundEnd);

        doSetGameStateLabelText(gs, now);
        doSetSidesText(gs);

        if (now.rationale != CtFwSGameStateManager.NowRationale.NR_GAME_IN_PROGRESS) {
            Log.d("CtFwS", "Rationale: " + now.rationale + " stop=" + now.stop);
            doReset();
            return;
        }
        // Otherwise, it's game on!

        // Upper line text
        {
            final TextView tv_jb = mAct.findViewById(R.id.tv_jailbreak);
            tv_jb.post(new Runnable() {
                @Override
                public void run() {
                    if (now.round == 0) {
                        tv_jb.setText(R.string.ctfws_gamestart);
                    } else if (now.round == gs.getRounds()) {
                        tv_jb.setText(R.string.ctfws_gameend);
                    } else {
                        tv_jb.setText(
                                String.format(mAct.getResources().getString(R.string.ctfws_jailbreak),
                                        now.round, gs.getRounds() - 1));
                    }
                }
            });
        }

        // Upper progress bar and chronometer
        {
            final ProgressBar pb_jb = mAct.findViewById(R.id.pb_jailbreak);
            pb_jb.post(new Runnable() {
                @Override
                public void run() {
                    pb_jb.setIndeterminate(false);
                    pb_jb.setMax((int) (now.roundEnd - now.roundStart));
                    pb_jb.setProgress(0);
                }
            });

            final Chronometer ch_jb = mAct.findViewById(R.id.ch_jailbreak);
            ch_jb.post(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        ch_jb.setBase((now.roundEnd + 1) * 1000 - tbcf);
                        ch_jb.setCountDown(true);
                    } else {
                        ch_jb.setBase(now.roundStart * 1000 - tbcf);
                        ch_jb.setBackgroundColor(Color.BLACK);
                        ch_jb.setTextColor(Color.WHITE);
                    }
                    ch_jb.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
                        @Override
                        public void onChronometerTick(Chronometer c) {
                            pb_jb.setProgress((int) (now.roundEnd - System.currentTimeMillis() / 1000));
                        }
                    });
                    ch_jb.setVisibility(View.VISIBLE);
                    ch_jb.start();
                }
            });
        }

        // Lower progress bar and chronometer
        if (now.round > 0) {
            final ProgressBar pb_gp = mAct.findViewById(R.id.pb_gameProgress);
            pb_gp.post(new Runnable() {
                @Override
                public void run() {
                    pb_gp.setIndeterminate(false);
                    pb_gp.setMax(gs.getComputedGameDuration());
                    pb_gp.setProgress(0);
                }
            });

            final Chronometer ch_gp = mAct.findViewById(R.id.ch_gameProgress);
            ch_gp.post(new Runnable() {
                @Override
                public void run() {
                    ch_gp.setBase(gs.getFirstRoundStartT() * 1000 - tbcf);
                    ch_gp.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
                        @Override
                        public void onChronometerTick(Chronometer c) {
                            pb_gp.setProgress((int) (System.currentTimeMillis() / 1000
                                    - gs.getFirstRoundStartT()));
                        }
                    });
                    ch_gp.setVisibility(View.VISIBLE);
                    ch_gp.start();
                }
            });
        } else {
            final ProgressBar pb_gp = mAct.findViewById(R.id.pb_gameProgress);
            pb_gp.post(new Runnable() {
                @Override
                public void run() {
                    pb_gp.setIndeterminate(true);
                }
            });

            final Chronometer ch_gp = mAct.findViewById(R.id.ch_gameProgress);
            ch_gp.post(new Runnable() {
                @Override
                public void run() {
                    ch_gp.setOnChronometerTickListener(null);
                    ch_gp.stop();
                    ch_gp.setVisibility(INVISIBLE);
                }
            });
        }
        {
            final TextView tv_flags = mAct.findViewById(R.id.tv_flags_label);
            tv_flags.post(new Runnable() {
                @Override
                public void run() {
                    tv_flags.setText(mAct.getResources()
                            .getQuantityString(R.plurals.ctfws_flags,
                                    gs.getFlagsTotal(),
                                    gs.getFlagsTotal()));
                }
            });
        }
    }

    private void doReset() {
        Log.d("CtFwS", "Display Reset");

        {
            final Chronometer ch = mAct.findViewById(R.id.ch_jailbreak);
            ch.post(new Runnable() {
                @Override
                public void run() {
                    ch.setOnChronometerTickListener(null);
                    ch.setBase(SystemClock.elapsedRealtime());
                    ch.stop();
                    ch.setVisibility(View.INVISIBLE);
                }
            });
        }
        {
            final Chronometer ch = mAct.findViewById(R.id.ch_gameProgress);
            ch.post(new Runnable() {
                @Override
                public void run() {
                    ch.setOnChronometerTickListener(null);
                    ch.stop();
                    ch.setVisibility(View.INVISIBLE);
                }
            });
        }
        {
            final ProgressBar pb = mAct.findViewById(R.id.pb_jailbreak);
            pb.post(new Runnable() {
                @Override
                public void run() {
                    pb.setIndeterminate(true);
                }
            });
        }
        {
            final ProgressBar pb = mAct.findViewById(R.id.pb_gameProgress);
            pb.post(new Runnable() {
                @Override
                public void run() {
                    pb.setIndeterminate(true);
                }
            });
        }
    }

    @Override
    public void onCtFwSFlags(CtFwSGameStateManager gs) {
        // TODO: This stinks

        final StringBuffer sb = new StringBuffer();
        if (gs.isConfigured()) {
            if (gs.getFlagsVisible()) {
                sb.append("r=");
                sb.append(gs.getFlagsRed());
                sb.append(" y=");
                sb.append(gs.getFlagsYel());
            } else {
                sb.append("r=? y=?");
            }
        }

        final TextView msgs = mAct.findViewById(R.id.tv_flags);
        msgs.post(new Runnable() {
            @Override
            public void run() {
                msgs.setText(sb);
            }
        });
    }

    private CtFwSGameStateManager.Msg lastMsg;
    @Override
    public void onCtFwSMessage(CtFwSGameStateManager gs, List<CtFwSGameStateManager.Msg> msgs) {
        final TextView msgstv = mAct.findViewById(R.id.msgs);
        int s = msgs.size();

        if (s == 0) {
            msgstv.post(new Runnable() {
                @Override
                public void run() {
                    msgstv.setText("");
                }
            });
            return;
        }

        int ix;
        if (lastMsg == null) {
            ix = 0;
        } else {
            ix = msgs.indexOf(lastMsg);
            if (ix == -1) {
                ix = 0;
            } else if (ix == s) {
                return;
            } else {
                ix = ix + 1;
            }
        }
        final StringBuffer sb = new StringBuffer();
        for (ListIterator<CtFwSGameStateManager.Msg> news = msgs.listIterator(ix);
                news.hasNext(); ) {

            CtFwSGameStateManager.Msg m = news.next();

            long td = (m.when == 0) ? 0 : (gs.isConfigured()) ? m.when - gs.getStartT() : 0;

            sb.append(DateUtils.formatElapsedTime(td));
            sb.append(": ");
            sb.append(m.msg);
            sb.append("\n");

            lastMsg = m;
        }

        msgstv.post(new Runnable() {
            @Override
            public void run() {
                msgstv.append(sb);
            }
        });
    }

    // Stun timers
    private class StunTimer {
        final Chronometer ch;
        final ProgressBar pb;
        final int ms;
        long wallEndMS = 0;

        StunTimer(Chronometer ch, ProgressBar pb, int ms) {
            this.ch = ch;
            this.pb = pb;
            this.ms = ms;
        }
    }

    private void startTimer(StunTimer st, long wallStart) {
        resumeTimer(st, wallStart + st.ms);
    }

    private void hideTimer(final StunTimer st) {
        st.ch.setOnChronometerTickListener(null);
        st.ch.setVisibility(View.INVISIBLE);
        st.pb.setVisibility(View.INVISIBLE);
    }

    private void resumeTimer(final StunTimer st, final long wallEnd) {
        Log.d("CtFwS", "Timer start: " + st.ms);
        st.wallEndMS = wallEnd;

        final long nowWall = System.currentTimeMillis();
        if (wallEnd < nowWall) {
            Log.d("CtFwS", "Timer finished in past");
            hideTimer(st);
            return;
        }

        final long nowEla = SystemClock.elapsedRealtime();
        final long tbcf = nowWall - nowEla;

        st.ch.setBase(wallEnd - st.ms - tbcf);
        st.ch.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
            @Override
            public void onChronometerTick(Chronometer chronometer) {
                final long nowAbsCB = System.currentTimeMillis();
                st.pb.setProgress((int) (wallEnd - nowAbsCB));
                if (wallEnd < nowAbsCB) {
                    hideTimer(st);
                }
            }
        });

        st.pb.setMax((int) (wallEnd - nowWall));
        st.ch.start();
        st.ch.setVisibility(View.VISIBLE);
        st.pb.setVisibility(View.VISIBLE);
    }
}