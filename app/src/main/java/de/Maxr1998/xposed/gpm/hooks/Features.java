package de.Maxr1998.xposed.gpm.hooks;

import android.content.ContentResolver;
import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

class Features {

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
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

            // Gservices overrides
            findAndHookMethod("com.google.android.gsf.Gservices", lPParam.classLoader, "getBoolean", ContentResolver.class, String.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    switch ((String) param.args[1]) {
                        case "music_use_system_media_notificaion":
                            param.setResult(true);
                            break;
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }
}