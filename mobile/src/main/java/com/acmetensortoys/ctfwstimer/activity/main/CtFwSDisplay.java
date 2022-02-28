package com.acmetensortoys.ctfwstimer.activity.main;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Spanned;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.preference.PreferenceManager;

import com.acmetensortoys.ctfwstimer.R;
import com.acmetensortoys.ctfwstimer.lib.CtFwSGameStateManager;
import com.acmetensortoys.ctfwstimer.utils.AndroidResourceUtils;

import java.text.NumberFormat;
import java.util.SortedSet;

// TODO nwf is bad at UI design; someone who isn't him should improve this
public class CtFwSDisplay implements CtFwSGameStateManager.Observer {
    final private Activity mAct;
    String gameStateLabelText;

    private final StunTimer stun_short, stun_long;

    CtFwSDisplay(Activity a) {
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

        if (nr == CtFwSGameStateManager.NowRationale.NR_NOT_CONFIG) {
            gameStateLabelText = String.format(rs.getString(R.string.header_gamestate0),
                    rs.getString(R.string.notify_not_config));
        } else {

            String sfx;
            switch (nr) {
                case NR_EXPLICIT_END:
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
                default:
                    sfx = "";
            }

            if (gameIndex == 0) {
                gameStateLabelText = String.format(rs.getString(R.string.header_gamestate0),
                        sfx);
            } else {
                gameStateLabelText = String.format(rs.getString(R.string.header_gamestateN),
                        gameIndex, sfx);
            }
        }

        final TextView gstv = mAct.findViewById(R.id.header_gamestate);
        gstv.post(new Runnable() {
            @Override
            public void run() {
                gstv.setText(gameStateLabelText);
            }
        });
    }

    private void doSetSidesText(final CtFwSGameStateManager gs) {
        final TextView stv = mAct.findViewById(R.id.header_sides);
        Resources rs = mAct.getResources();
        Runnable r = new Runnable() {
            @Override
            public void run() {
                stv.setText("");
            }
        };

        out: if (gs.isConfigured()) {
            String ss = gs.getSides();
            if (ss != null) {
                final Spanned h;

                switch(ss) {
                    case "-"  : break out;
                    case "wd" : h = AndroidResourceUtils.htmlFromStrResId(rs, R.string.ctfws_sides_wd); break;
                    case "dw" : h = AndroidResourceUtils.htmlFromStrResId(rs, R.string.ctfws_sides_dw); break;
                    default   : h = AndroidResourceUtils.htmlFromStrResId(rs, R.string.ctfws_unknown_sides); break;
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

    private void doSetFlagsLabel(final CtFwSGameStateManager gs) {
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

    @Override
    public void onCtFwSConfigure(final CtFwSGameStateManager gs) {
        doSetGameStateLabelText(gs, null);
        doSetSidesText(gs);
        doSetFlagsLabel(gs);
        if (!gs.isConfigured()) {
            doReset();
            // otherwise there's a onCtFwSNow headed our way momentarily.
        }
        onCtFwSFlags(gs);   /* Populate the flags field to some default */
    }

    @Override
    public void onCtFwSNow(final CtFwSGameStateManager gs, final CtFwSGameStateManager.Now now) {
        // time base correction factor ("when we booted"-ish)
        final long tbcf = System.currentTimeMillis() - SystemClock.elapsedRealtime();

        Log.d("CtFwS", "Display game state; nowMS=" + now.wallMS + " r=" + now.round + " rs=" + now.roundStart + " re=" + now.roundEnd);

        doSetGameStateLabelText(gs, now);
        doSetSidesText(gs);
        doSetFlagsLabel(gs);

        if (now.rationale != CtFwSGameStateManager.NowRationale.NR_GAME_IN_PROGRESS) {
            Log.d("CtFwS", "Rationale: " + now.rationale + " stop=" + now.stop);
            doReset();
            return;
        }
        // Otherwise, it's game on!

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mAct);
        if (sp.getBoolean("screen_on_when_game", false)) {
            Log.d("CtFwS", "Requesting screen on");
            mAct.runOnUiThread(() -> mAct.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON));
        }

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
                    pb_jb.setVisibility(View.VISIBLE);
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
                    pb_gp.setVisibility(View.VISIBLE);
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
                    pb_gp.setVisibility(View.INVISIBLE);
                    pb_gp.setIndeterminate(true);
                }
            });

            final Chronometer ch_gp = mAct.findViewById(R.id.ch_gameProgress);
            ch_gp.post(new Runnable() {
                @Override
                public void run() {
                    ch_gp.setOnChronometerTickListener(null);
                    ch_gp.stop();
                    ch_gp.setVisibility(View.INVISIBLE);
                }
            });
        }
    }

    private void resetWindow() {
        mAct.runOnUiThread(() -> resetWindow(mAct));
    }

    public static void resetWindow(Activity act) {
        Log.d("CtFwS", "window reset");
        act.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    private void doReset() {
        Log.d("CtFwS", "Display Reset");

        resetWindow();

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
                    pb.setVisibility(View.INVISIBLE);
                    pb.setIndeterminate(true);
                }
            });
        }
        {
            final ProgressBar pb = mAct.findViewById(R.id.pb_gameProgress);
            pb.post(new Runnable() {
                @Override
                public void run() {
                    pb.setVisibility(View.INVISIBLE);
                    pb.setIndeterminate(true);
                }
            });
        }
    }

    @Override
    public void onCtFwSFlags(CtFwSGameStateManager gs) {
        final Spanned h;
        Resources rs = mAct.getResources();

        if (gs.getFlagsVisible()) {
            NumberFormat nf = NumberFormat.getIntegerInstance();
            h = AndroidResourceUtils.htmlFromStrResId(rs, R.string.flags_viz_fmt,
                    nf.format(gs.getFlagsRed()), nf.format(gs.getFlagsYel()));
        } else {
            h = AndroidResourceUtils.htmlFromStrResId(rs, R.string.flags_noviz);
        }

        final TextView msgs = mAct.findViewById(R.id.tv_flags);
        msgs.post(new Runnable() {
            @Override
            public void run() {
                msgs.setText(h);
            }
        });
    }

    @Override
    public void onCtFwSMessage(CtFwSGameStateManager gs, SortedSet<CtFwSGameStateManager.Msg> msgs) {
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

        final StringBuffer sb = new StringBuffer();
        for (CtFwSGameStateManager.Msg m : msgs) {

            //noinspection StatementWithEmptyBody
            if (m.when == 0 || !gs.isConfigured()) {
                // leave out the time stamp
            } else if (m.when <= gs.getFirstRoundStartT()) {
                sb.append("Setup+");
                sb.append(DateUtils.formatElapsedTime(m.when - gs.getStartT()));
                sb.append(": ");
            } else {
                sb.append("Game+");
                sb.append(DateUtils.formatElapsedTime(m.when - gs.getFirstRoundStartT()));
                sb.append(": ");
            }

            sb.append(m.msg);
            sb.append("\n");
        }

        msgstv.post(new Runnable() {
            @Override
            public void run() {
                msgstv.setText(sb);
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
