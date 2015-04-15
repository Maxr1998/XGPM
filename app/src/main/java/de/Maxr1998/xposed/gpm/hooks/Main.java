package de.Maxr1998.xposed.gpm.hooks;

import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XSharedPreferences;

import static de.Maxr1998.xposed.gpm.Common.MODULE_PATH;

public class Main implements IXposedHookZygoteInit {

    public static XSharedPreferences PREFS;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        PREFS = new XSharedPreferences(Common.XGPM, Common.XGPM + "_preferences");
        PREFS.makeWorldReadable();
        MODULE_PATH = startupParam.modulePath;
    }
}