package de.Maxr1998.xposed.gpm.hooks;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import de.Maxr1998.xposed.gpm.BuildConfig;
import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.robv.android.xposed.XposedBridge.log;
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
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lPParam) throws Throwable {
        if (lPParam.packageName.equals(GPM)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                doHooks(lPParam);
            } else {
                findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
                        doHooks(lPParam);
                    }
                });
            }
        }
    }

    private void doHooks(XC_LoadPackage.LoadPackageParam lPParam) {
        PREFS.reload();
        // UI
        MainStage.init(lPParam);
        MyLibrary.init(lPParam);
        NavigationDrawer.init(lPParam);
        NowPlaying.init(lPParam);
        TrackList.init(lPParam);

        // External
        ArtReplacer.init(lPParam);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            NotificationMod.init(lPParam);
        }

        // Enable voice control
        findAndHookMethod(GPM + ".Feature", lPParam.classLoader, "isSnappleEnabled", Context.class, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                return true;
            }
        });

        // Debug
        if (BuildConfig.DEBUG) {
            try {
                findAndHookMethod(GPM + ".utils.DebugUtils", lPParam.classLoader, "isLoggable", findClass(GPM + ".utils.DebugUtils.MusicTag", lPParam.classLoader), new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam methodHookParam) throws Throwable {
                        return true;
                    }
                });
            } catch (Throwable t) {
                log(t);
            }
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