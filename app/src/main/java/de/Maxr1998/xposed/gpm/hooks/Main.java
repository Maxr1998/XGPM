package de.Maxr1998.xposed.gpm.hooks;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.crossbowffs.remotepreferences.RemotePreferences;

import de.Maxr1998.xposed.gpm.BuildConfig;
import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

@SuppressWarnings("RedundantThrows")
public class Main implements IXposedHookZygoteInit, IXposedHookLoadPackage, IXposedHookInitPackageResources {

    static String MODULE_PATH = null;
    static SharedPreferences PREFS;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        MODULE_PATH = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lPParam) throws Throwable {
        if (lPParam.packageName.equals(GPM)) {
            findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    PREFS = new RemotePreferences((Context) param.args[0], Common.PREFERENCE_PROVIDER_AUTHORITY, Common.XGPM + "_preferences");
                    doHooks(lPParam);
                }
            });
        }
    }

    private void doHooks(XC_LoadPackage.LoadPackageParam lPParam) {
        // UI
        //Features.init(lPParam);
        MainStage.init(lPParam);
        MyLibrary.init(lPParam);
        //NavigationDrawer.init(lPParam);
        //NowPlaying.init(lPParam);
        //TrackList.init(lPParam);
        Misc.init(lPParam);

        // External
        //ArtReplacer.init(lPParam);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //NotificationMod.init(lPParam);
        }

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
        /*if (resParam.packageName.equals(GPM)) {
            NowPlaying.initResources(resParam);
        }*/
    }
}