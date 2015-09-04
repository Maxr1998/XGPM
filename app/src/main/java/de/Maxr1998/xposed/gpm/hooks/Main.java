package de.Maxr1998.xposed.gpm.hooks;

import de.Maxr1998.xposed.gpm.BuildConfig;
import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.Common.MODULE_PATH;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class Main implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    public static XSharedPreferences PREFS;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        PREFS = new XSharedPreferences(Common.XGPM, Common.XGPM + "_preferences");
        PREFS.makeWorldReadable();
        MODULE_PATH = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lPParam) throws Throwable {
        if (lPParam.packageName.equals(GPM)) {
            // UI
            MainStage.init(lPParam);
            NavigationDrawer.init(lPParam);
            TrackList.init(lPParam);

            // External
            NotificationMod.init(lPParam);
            ArtReplacer.init(lPParam);

            // Debug
            if (BuildConfig.DEBUG) {
                findAndHookMethod(GPM + ".utils.DebugUtils", lPParam.classLoader, "isLoggable", findClass(GPM + ".utils.DebugUtils.MusicTag", lPParam.classLoader), new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        return true;
                    }
                });
            }
        } else if (lPParam.packageName.equals("com.android.systemui")) {
            NotificationMod.initUI(lPParam);
        }
    }
}