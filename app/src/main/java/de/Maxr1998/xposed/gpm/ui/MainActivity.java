package de.Maxr1998.xposed.gpm.ui;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.preference.TwoStatePreference;
import android.provider.Settings;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add("Open GPM")
                .setIcon(R.drawable.ic_album_white_24dp)
                .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
                .setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        startActivity(getPackageManager().getLaunchIntentForPackage(Common.GPM));
                        return true;
                    }
                });
        return true;
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

            mainComponent = new ComponentName(getActivity(), getActivity().getApplication().getPackageName() + ".Main");
            ((TwoStatePreference) findPreference(Common.HIDE_APP_FROM_LAUNCHER)).setChecked(getActivity().getPackageManager()
                    .getComponentEnabledSetting(mainComponent) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED);
        }

        @Override
        public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
            switch (preference.getKey()) {
                case Common.DEFAULT_MY_LIBRARY:
                case Common.UNIVERSAL_ART_REPLACER:
                case Common.NP_REMOVE_DROP_SHADOW:
                    Snackbar.make(getActivity().findViewById(R.id.preference_frame), R.string.force_stop_required, Snackbar.LENGTH_LONG)
                            .setActionTextColor(ContextCompat.getColor(getActivity(), R.color.accent))
                            .setAction(R.string.action_show_app_details, new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    intent.setData(Uri.parse("package:" + Common.GPM));
                                    startActivity(intent);
                                }
                            }).show();
                    return false;
                case Common.HIDE_APP_FROM_LAUNCHER:
                    TwoStatePreference hideApp = (TwoStatePreference) preference;
                    if (hideApp.isChecked()) {
                        getActivity().getPackageManager().setComponentEnabledSetting(mainComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                    } else {
                        getActivity().getPackageManager().setComponentEnabledSetting(mainComponent, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
                    }
                    return true;
                default:
                    return super.onPreferenceTreeClick(preferenceScreen, preference);

            }
        }
    }
}
