package com.acmetensortoys.ctfwstimer;

import android.app.Activity;
import android.os.Handler;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Chronometer;
import android.widget.ProgressBar;
import android.widget.TextView;

// TODO nwf is bad at UI design; someone who isn't him should improve this

class CtFwSDisplay {
    final private Activity mAct;
    final private Handler mHandler;
    final private CtFwSGameState mCgs;

    private long lastMsgTimeMS = 0;

    CtFwSDisplay(Activity a, Handler h, CtFwSGameState cgs) {
        mAct = a;
        mHandler = h;
        mCgs = cgs;
    }

    final private Runnable mProber = new Runnable() {
        @Override
        public void run() {
            notifyGameState();
        }
    };

    void notifyGameState() {
        final long nowMS = System.currentTimeMillis();
        long nowET = SystemClock.elapsedRealtime(); // Chronometer timebase
        final long tbcf = nowMS - nowET; // time base correction factor ("when we booted"-ish)

        final CtFwSGameState.Now now = mCgs.getNow(nowMS / 1000);

        Log.d("CtFwS", "Display game state; nowMS=" + nowMS + " r=" + now.round + " rs=" + now.roundStart + " re=" + now.roundEnd);

        if (now.rationale != null) {
            Log.d("CtFwS", "Rationale: " + now.rationale + " stop=" + now.stop);

            // TODO: display rationale somewhere, probably by hiding the game state!

            doReset();

            mHandler.removeCallbacks(mProber);
            if (!now.stop) {
                mHandler.postDelayed(mProber, mCgs.startT*1000 - nowMS);
            }

            return;
        }

        // Otherwise, it's game on!

        // Clear the mesage log if it looks like it's a new game in play
        if (lastMsgTimeMS < mCgs.startT * 1000) {
            clearMsgs();
        }

        mHandler.removeCallbacks(mProber);
        mHandler.postDelayed(mProber, now.roundEnd * 1000 - nowMS);

        {
            final TextView tv_jb = (TextView) (mAct.findViewById(R.id.tv_jailbreak));
            tv_jb.post(new Runnable() {
                @Override
                public void run() {
                    if (now.round == 0) {
                        tv_jb.setText(R.string.ctfws_gamestart);
                    } else if (now.round == mCgs.rounds) {
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
                    if (now.round == 0) {
                        pb_jb.setMax(mCgs.setupD - 1);
                    } else {
                        pb_jb.setMax(mCgs.roundD - 1);
                    }
                    pb_jb.setProgress(0);
                }
            });

            final Chronometer ch_jb = (Chronometer) (mAct.findViewById(R.id.ch_jailbreak));
            ch_jb.post(new Runnable() {
                @Override
                public void run() {
                    ch_jb.setBase(now.roundEnd * 1000 - tbcf);
                    ch_jb.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
                        @Override
                        public void onChronometerTick(Chronometer c) {
                            pb_jb.setProgress((int)(now.roundEnd - System.currentTimeMillis()/1000) - 1);
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
                    pb_gp.setMax(mCgs.rounds * mCgs.roundD - 1);
                    pb_gp.setProgress(0);
                }
            });

            final Chronometer ch_gp = (Chronometer) (mAct.findViewById(R.id.ch_gameProgress));
            ch_gp.post(new Runnable() {
                @Override
                public void run() {
                    ch_gp.setBase((mCgs.startT + mCgs.setupD) * 1000 - tbcf);
                    ch_gp.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
                        @Override
                        public void onChronometerTick(Chronometer c) {
                            pb_gp.setProgress((int)(System.currentTimeMillis()/1000
                                    - mCgs.startT - mCgs.setupD));
                        }
                    });
                    ch_gp.start();
                }
            });
        } else {
            final ProgressBar pb_gp = (ProgressBar) (mAct.findViewById(R.id.pb_gameProgress));
            pb_gp.post(new Runnable() {
                @Override
                public void run() {
                    pb_gp.setIndeterminate(false);
                    pb_gp.setMax(mCgs.rounds * mCgs.roundD - 1);
                    pb_gp.setProgress(0);
                }
            });

            final Chronometer ch_gp = (Chronometer) (mAct.findViewById(R.id.ch_gameProgress));
            ch_gp.post(new Runnable() {
                @Override
                public void run() {
                    ch_gp.setBase(nowMS - tbcf);
                    ch_gp.setOnChronometerTickListener(null);
                    ch_gp.stop();
                }
            });
        }
        {
            final TextView tv_flags = (TextView) (mAct.findViewById(R.id.tv_flags_label));
            tv_flags.post(new Runnable() {
                @Override
                public void run() {
                    tv_flags.setText(
                            String.format(mAct.getResources().getString(R.string.ctfws_flags),
                                    mCgs.flagsTotal));
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
                    ch.setOnChronometerTickListener(null);
                    ch.setBase(SystemClock.elapsedRealtime());
                    ch.stop();
                }
            });
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

    void notifyFlags() {
        // TODO: This stinks

        final StringBuffer sb = new StringBuffer();
        if (mCgs.configured) {
            if (mCgs.flagsVisible) {
                sb.append("r=");
                sb.append(mCgs.flagsRed);
                sb.append(" y=");
                sb.append(mCgs.flagsYel);
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

    void clearMsgs() {
        final TextView msgs = (TextView) (mAct.findViewById(R.id.msgs));
        msgs.post(new Runnable() {
            @Override
            public void run() {
                msgs.setText("");
            }
        });
    }

    void notifyMessage(String m) {
        lastMsgTimeMS = System.currentTimeMillis();

        final StringBuffer sb = new StringBuffer();
        long ts = lastMsgTimeMS/1000 - mCgs.startT;
        if (!mCgs.configured || ts < 0) { ts = 0; }
        sb.append(DateUtils.formatElapsedTime(ts));
        sb.append(": ");
        sb.append(m);
        sb.append("\n");

        final TextView msgs = (TextView)(mAct.findViewById(R.id.msgs));
        msgs.post(new Runnable() {
            @Override
            public void run() {
                msgs.append(sb);
            }
        });
    }
}
