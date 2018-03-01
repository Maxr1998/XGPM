package de.Maxr1998.xposed.gpm.hooks;

import android.content.ContentResolver;
import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

@SuppressWarnings("RedundantThrows")
class Features {

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Features
            final String[] noParamMethods = new String[]{
                    "isFeatureManagerUiEnabled",
                    "isFullWidthSearchEnabled",
                    "isMbsDownloadEnabled",
                    "isPlaybackSelectionEnabled",
                    "isSleepTimerEnabled"
            };
            for (int i = 0; i < noParamMethods.length; i++) {
                findAndHookMethod(GPM + ".Feature", lPParam.classLoader, noParamMethods[i], new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                        return true;
                    }
                });
            }

            String[] contextMethods = new String[]{
                    "isTopChartsAndNewReleasesEnabled",
                    "isYouTubeContentAvailable",
            };
            for (int i = 0; i < contextMethods.length; i++) {
                findAndHookMethod(GPM + ".Feature", lPParam.classLoader, contextMethods[i], Context.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                        return true;
                    }
                });
            }

            // Experiments
            String[] experimentsMethods = new String[]{
                    "isFastFirstTrackControl",
                    "isFastFirstTrackExperiment"
            };
            for (int i = 0; i < experimentsMethods.length; i++) {
                findAndHookMethod(GPM + ".experiments.AnalysisExperimentsManager", lPParam.classLoader, experimentsMethods[i], new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                        return true;
                    }
                });
            }

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