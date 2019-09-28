package com.acmetensortoys.ctfwstimer.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.view.View;

import com.acmetensortoys.ctfwstimer.R;

public class DefaultableEditTextPreference extends EditTextPreference {
    // GAH!  Our parent Preference has this as a private field.  Grrr Android!
    private String defText;

    public DefaultableEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        defText = a.getString(index);
        return super.onGetDefaultValue(a, index);
    }

    @Override
    public void setDefaultValue(Object defaultValue) {
        defText = (String) defaultValue;
        super.setDefaultValue(defaultValue);
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);

        // null callback is OK here because we're about to nuke the would-be caller below!
        builder.setNeutralButton(R.string.dialog_reset, null);
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        ((AlertDialog)getDialog())
                .getButton(AlertDialog.BUTTON_NEUTRAL)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getEditText().setText(defText);
                    }
                });

    }
}
