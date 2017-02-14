package com.acmetensortoys.ctfwstimer;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

public class StringSettingDialogFragment extends DialogFragment
    implements SharedPreferences.OnSharedPreferenceChangeListener
{
    private final static String ARG_LRES_IX = "lres"; // layout id
    private final static String ARG_VRES_IX = "vres"; // text view id
    private final static String ARG_PREF_IX = "pref"; // preference name
    private final static String ARG_DEFL_IX = "def";   // optional default

    private TextView mTv;

    public static StringSettingDialogFragment newInstance(int lres, int vres, String pref, String def) {
        StringSettingDialogFragment ssdf = new StringSettingDialogFragment();
        Bundle args = new Bundle();
        args.putInt   (ARG_LRES_IX, lres);
        args.putInt   (ARG_VRES_IX, vres);
        args.putString(ARG_PREF_IX, pref);
        if (def != null) { args.putString(ARG_DEFL_IX, def); }
        ssdf.setArguments(args);
        return ssdf;
    }

    @NonNull @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle a = getArguments();
        AlertDialog.Builder adb = new AlertDialog.Builder(getActivity());
        LayoutInflater li = getActivity().getLayoutInflater();
        View v = li.inflate(a.getInt(ARG_LRES_IX), null);

        mTv = (TextView)v.findViewById(a.getInt(ARG_VRES_IX));
        final SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getActivity());
        sp.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(sp,a.getString(ARG_PREF_IX));

        adb.setView(v)
                .setPositiveButton(R.string.dialog_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sp.edit().putString("server", mTv.getText().toString()).apply();
                    }
                })
                .setNegativeButton(R.string.dialog_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // NOP
                    }
                });

        final String def = a.getString(ARG_DEFL_IX);
        if (def != null) {
            adb.setNeutralButton(R.string.dialog_reset, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mTv.post(new Runnable() {
                        @Override
                        public void run() {
                            mTv.setText(def);
                        }
                    });
                }
            });
        }

        return adb.create();
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences sp, final String p) {
        if (p != null && getArguments().getString(ARG_PREF_IX).equals(p)) {
            mTv.post(new Runnable() {
                @Override
                public void run() {
                    mTv.setText(sp.getString(p, ""));
                }
            });
        }
    }
}
