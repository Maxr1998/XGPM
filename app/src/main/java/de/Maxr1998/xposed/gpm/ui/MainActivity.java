package de.Maxr1998.xposed.gpm.ui;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import de.Maxr1998.xposed.gpm.Common;
import de.Maxr1998.xposed.gpm.R;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        getFragmentManager().beginTransaction().replace(R.id.preference_frame, new MainFragment()).commit();
    }

    public static class MainFragment extends PreferenceFragment {

        private ComponentName mainComponent;

        @SuppressLint("WorldReadableFiles")
        @SuppressWarnings("deprecation")
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesMode(Context.MODE_WORLD_READABLE);
            addPreferencesFromResource(R.xml.preferences);

            mainComponent = new ComponentName(getActivity(), MainActivity.class.getName());
            ((TwoStatePreference) findPreference(Common.HIDE_APP_FROM_LAUNCHER)).setChecked(getActivity().getPackageManager()
                    .getComponentEnabledSetting(mainComponent) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            if (preference == findPreference(Common.HIDE_APP_FROM_LAUNCHER)) {
                TwoStatePreference hideApp = (TwoStatePreference) preference;
                if (hideApp.isChecked()) {
                    getActivity().getPackageManager().setComponentEnabledSetting(mainComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                } else {
                    getActivity().getPackageManager().setComponentEnabledSetting(mainComponent, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
                }
                return true;
            }
            return super.onPreferenceTreeClick(preferenceScreen, preference);
        }
    }
}
