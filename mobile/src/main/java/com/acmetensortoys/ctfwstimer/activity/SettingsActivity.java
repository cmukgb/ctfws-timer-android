package com.acmetensortoys.ctfwstimer.activity;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;

import com.acmetensortoys.ctfwstimer.R;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        private final SharedPreferences.OnSharedPreferenceChangeListener mOSPCL
                = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
                switch (key) {
                    case "server_def":
                        findPreference("server").setVisible(!sp.getBoolean(key, false));
                        break;
                }
            }
        };

        @Override
        public void onResume() {
            super.onResume();

            getPreferenceScreen()
                    .getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(mOSPCL);
        }

        @Override
        public void onPause() {
            getPreferenceScreen()
                    .getSharedPreferences()
                    .unregisterOnSharedPreferenceChangeListener(mOSPCL);

            super.onPause();
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceBundle, String rootKey) {
            setPreferencesFromResource(R.xml.preferences, rootKey);

            findPreference("server").setVisible(!
                    getPreferenceScreen()
                            .getSharedPreferences()
                            .getBoolean("server_def", false)
            );
        }
    }
}
