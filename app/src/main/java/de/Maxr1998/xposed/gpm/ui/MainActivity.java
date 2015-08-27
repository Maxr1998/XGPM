package de.Maxr1998.xposed.gpm.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

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

        @SuppressLint("WorldReadableFiles")
        @SuppressWarnings("deprecation")
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getPreferenceManager().setSharedPreferencesMode(Context.MODE_WORLD_READABLE);
            addPreferencesFromResource(R.xml.preferences);
        }
    }
}
