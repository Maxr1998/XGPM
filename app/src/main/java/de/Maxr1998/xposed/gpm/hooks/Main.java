package de.Maxr1998.xposed.gpm.hooks;

import de.Maxr1998.xposed.gpm.BuildConfig;
import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class Main implements IXposedHookZygoteInit, IXposedHookLoadPackage, IXposedHookInitPackageResources {

    public static String MODULE_PATH = null;
    public static XSharedPreferences PREFS;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
        PREFS = new XSharedPreferences(Common.XGPM, Common.XGPM + "_preferences");
        PREFS.makeWorldReadable();
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lPParam) throws Throwable {
        if (lPParam.packageName.equals(GPM)) {
            PREFS.reload();
            // UI
            MainStage.init(lPParam);
            NavigationDrawer.init(lPParam);
            NowPlaying.init(lPParam);
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

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resParam) throws Throwable {
        if (resParam.packageName.equals(GPM)) {
            PREFS.reload();
            NowPlaying.initResources(resParam);
        }
    }
}