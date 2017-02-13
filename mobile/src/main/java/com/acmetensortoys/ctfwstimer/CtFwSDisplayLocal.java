package com.acmetensortoys.ctfwstimer;

import android.app.Activity;
import android.os.Handler;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.acmetensortoys.ctfwstimer.lib.CtFwSGameState;

import java.util.List;

import static android.view.View.INVISIBLE;

// TODO nwf is bad at UI design; someone who isn't him should improve this
class CtFwSDisplayLocal implements CtFwSGameState.Observer {
    final private Activity mAct;
    final private Handler mHandler;

    CtFwSDisplayLocal(Activity a, Handler h) {
        mAct = a;
        mHandler = h;
    }

    private Runnable mProber;

    @Override
    public void onCtFwSConfigure(final CtFwSGameState gs) {
        final long nowMS = System.currentTimeMillis();
        long nowET = SystemClock.elapsedRealtime(); // Chronometer timebase
        final long tbcf = nowMS - nowET; // time base correction factor ("when we booted"-ish)

        final CtFwSGameState.Now now = gs.getNow(nowMS / 1000);
        final Runnable prober = new Runnable() {
            @Override
            public void run() {
                onCtFwSConfigure(gs);
            }
        };


        Log.d("CtFwS", "Display game state; nowMS=" + nowMS + " r=" + now.round + " rs=" + now.roundStart + " re=" + now.roundEnd);

        if (now.rationale != null) {
            Log.d("CtFwS", "Rationale: " + now.rationale + " stop=" + now.stop);

            // TODO: display rationale somewhere, probably by hiding the game state!

            doReset();

            if (mProber != null) {
                mHandler.removeCallbacks(mProber);
            }
            if (!now.stop) {
                mProber = prober;
                mHandler.postDelayed(mProber, now.roundStart*1000 - nowMS);
            }

            return;
        }

        // Otherwise, it's game on!

        // Schedule a callback around the time of the next round; if we're early,
        // that's fine, we'll schedule it again.  If we're late, it'll be glitchy,
        // but that's fine.
        mHandler.removeCallbacks(mProber);
        mProber = prober;
        mHandler.postDelayed(mProber, now.roundEnd * 1000 - nowMS);

        {
            final TextView tv_jb = (TextView) (mAct.findViewById(R.id.tv_jailbreak));
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
                                now.round));
                    }
                }
            });

            final ProgressBar pb_jb = (ProgressBar) (mAct.findViewById(R.id.pb_jailbreak));
            pb_jb.post(new Runnable() {
                @Override
                public void run() {
                    pb_jb.setIndeterminate(false);
                    pb_jb.setMax((int)(now.roundEnd - now.roundStart));
                    pb_jb.setProgress(0);
                }
            });

            final Chronometer ch_jb = (Chronometer) (mAct.findViewById(R.id.ch_jailbreak));
            ch_jb.post(new Runnable() {
                @Override
                public void run() {
                    ch_jb.setBase((now.roundEnd + 1) * 1000 - tbcf);
                    ch_jb.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
                        @Override
                        public void onChronometerTick(Chronometer c) {
                            pb_jb.setProgress((int)(now.roundEnd - System.currentTimeMillis()/1000));
                        }
                    });
                    ch_jb.start();
                }
            });
        }
        if (now.round > 0){
            final ProgressBar pb_gp = (ProgressBar) (mAct.findViewById(R.id.pb_gameProgress));
            pb_gp.post(new Runnable() {
                @Override
                public void run() {
                    pb_gp.setIndeterminate(false);
                    pb_gp.setMax(gs.getComputedGameDuration());
                    pb_gp.setProgress(0);
                }
            });

            final Chronometer ch_gp = (Chronometer) (mAct.findViewById(R.id.ch_gameProgress));
            ch_gp.post(new Runnable() {
                @Override
                public void run() {
                    ch_gp.setBase(gs.getFirstRoundStartT() * 1000 - tbcf);
                    ch_gp.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
                        @Override
                        public void onChronometerTick(Chronometer c) {
                            pb_gp.setProgress((int)(System.currentTimeMillis()/1000
                                    - gs.getFirstRoundStartT()));
                        }
                    });
                    ch_gp.setVisibility(View.VISIBLE);
                    ch_gp.start();
                }
            });
        } else {
            final ProgressBar pb_gp = (ProgressBar) (mAct.findViewById(R.id.pb_gameProgress));
            pb_gp.post(new Runnable() {
                @Override
                public void run() {
                    pb_gp.setIndeterminate(true);
                }
            });

            final Chronometer ch_gp = (Chronometer) (mAct.findViewById(R.id.ch_gameProgress));
            ch_gp.post(new Runnable() {
                @Override
                public void run() {
                    ch_gp.stop();
                    ch_gp.setVisibility(INVISIBLE);
                }
            });
        }
        {
            final TextView tv_flags = (TextView) (mAct.findViewById(R.id.tv_flags_label));
            tv_flags.post(new Runnable() {
                @Override
                public void run() {
                    tv_flags.setText(
                            String.format(mAct.getResources().getString(R.string.ctfws_flags), gs.flagsTotal));
                }
            });
        }
    }

    private void doReset() {
        Log.d("CtFwS", "Display Reset");

        {
            final Chronometer ch = (Chronometer) (mAct.findViewById(R.id.ch_jailbreak));
            ch.post(new Runnable() {
                @Override
                public void run() {
                    ch.setOnChronometerTickListener(null);
                    ch.setBase(SystemClock.elapsedRealtime());
                    ch.stop();
                }
            });
        }
        {
            final Chronometer ch = (Chronometer) (mAct.findViewById(R.id.ch_gameProgress));
            ch.post(new Runnable() {
                @Override
                public void run() {
                    ch.stop();
                    ch.setVisibility(View.INVISIBLE);
                }});
        }
        {
            final ProgressBar pb = (ProgressBar) (mAct.findViewById(R.id.pb_jailbreak));
            pb.post(new Runnable() {
                @Override
                public void run() {
                    pb.setIndeterminate(true);
                }
            });
        }
        {
            final ProgressBar pb = (ProgressBar) (mAct.findViewById(R.id.pb_gameProgress));
            pb.post(new Runnable() {
                @Override
                public void run() {
                    pb.setIndeterminate(true);
                }
            });
        }
    }

    @Override
    public void onCtFwSFlags(CtFwSGameState gs) {
        // TODO: This stinks

        final StringBuffer sb = new StringBuffer();
        if (gs.isConfigured()) {
            if (gs.flagsVisible) {
                sb.append("r=");
                sb.append(gs.flagsRed);
                sb.append(" y=");
                sb.append(gs.flagsYel);
            } else {
                sb.append("r=? y=?");
            }
        }

        final TextView msgs = (TextView)(mAct.findViewById(R.id.tv_flags));
        msgs.post(new Runnable() {
            @Override
            public void run() {
                msgs.setText(sb);
            }
        });
    }

    @Override
    public void onCtFwSMessage(CtFwSGameState gs, List<CtFwSGameState.Msg> msgs) {
        final TextView msgstv = (TextView)(mAct.findViewById(R.id.msgs));
        int s = msgs.size();

        if (s == 0) {
            msgstv.post(new Runnable() {
                @Override
                public void run() {
                    msgstv.setText("");
                }
            });
        } else {

            CtFwSGameState.Msg m = msgs.get(s - 1);

            long td = (m.when == 0) ? 0 : (gs.isConfigured()) ? m.when - gs.getStartT() : 0;

            final StringBuffer sb = new StringBuffer();
            sb.append(DateUtils.formatElapsedTime(td));
            sb.append(": ");
            sb.append(m.msg);
            sb.append("\n");

            msgstv.post(new Runnable() {
                @Override
                public void run() {
                    msgstv.append(sb);
                }
            });
        }
    }
}
