package de.Maxr1998.xposed.gpm.hooks;

import android.content.Context;

import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.setStaticObjectField;

public class Features {

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Enable new adaptive home
            findAndHookMethod(GPM + ".Feature", lPParam.classLoader, "isAdaptiveHomeEnabled", Context.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    PREFS.reload();
                    return PREFS.getBoolean(Common.DRAWER_ENABLE_ADAPTIVE_HOME, false);
                }
            });
            setStaticObjectField(findClass(GPM + ".sync.api.MusicUrl", lPParam.classLoader), "MUSIC_PA_URL_HOST", "https://mclients.googleapis.com/music");

            findAndHookMethod(GPM + ".Feature", lPParam.classLoader, "isFullWidthSearchEnabled", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    return true;
                }
            });

            findAndHookMethod(GPM + ".Feature", lPParam.classLoader, "isHeadphoneNotificationAvailableForUser", Context.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    return true;
                }
            });

            findAndHookMethod(GPM + ".Feature", lPParam.classLoader, "isHeadphoneNotificationBroadcastReceiverEnabled", Context.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    return true;
                }
            });

            findAndHookMethod(GPM + ".Feature", lPParam.classLoader, "isLocalMessageAsBottomSheetsEnabled", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    return true;
                }
            });

            findAndHookMethod(GPM + ".Feature", lPParam.classLoader, "isSleepTimerEnabled", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    return true;
                }
            });

            findAndHookMethod(GPM + ".Feature", lPParam.classLoader, "isSoundSearchEnabled", Context.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                    return true;
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }
}