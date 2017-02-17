package com.acmetensortoys.ctfwstimer;

import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.os.Bundle;

// TODO (Cam): changing the server doesn't actually work yet
public class SettingsActivity extends PreferenceActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceBundle) {
            super.onCreate(savedInstanceBundle);
            addPreferencesFromResource(R.xml.preferences);
        }
    }
}
