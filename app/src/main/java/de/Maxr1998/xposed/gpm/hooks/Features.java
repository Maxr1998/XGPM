package de.Maxr1998.xposed.gpm.hooks;

import android.content.ContentResolver;
import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;
import static de.robv.android.xposed.XposedHelpers.setStaticBooleanField;

@SuppressWarnings("RedundantThrows")
class Features {

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Features
            String[] noContextMethods = new String[]{"isFullWidthSearchEnabled", "isSleepTimerEnabled"};
            for (int i = 0; i < noContextMethods.length; i++) {
                findAndHookMethod(GPM + ".Feature", lPParam.classLoader, noContextMethods[i], new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                        return true;
                    }
                });
            }

            String[] contextMethods = new String[]{"isHeadphoneNotificationAvailableForUser", "isHeadphoneNotificationBroadcastReceiverEnabled", "isSoundSearchEnabled"};
            for (int i = 0; i < contextMethods.length; i++) {
                findAndHookMethod(GPM + ".Feature", lPParam.classLoader, contextMethods[i], Context.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                        return true;
                    }
                });
            }

            // To allow headphone notification
            setObjectField(getStaticObjectField(findClass(GPM + ".Feature", lPParam.classLoader), "head"), "buildFlag", true);

            // Experiments
            String[] experimentsMethods = new String[]{"canShowRemoteSearchToFreeUsersInSubscriptionCountries", "isAutoPlayLastSongEnabled",
                    "isFastFirstTrackControl", "isFastFirstTrackExperiment", "isTopChartsIcingForNonWoodstockCountriesEnabled",
                    "isTopChartsIcingForWoodstockCountriesEnabled", "shouldShowTopChartsAndNewReleasesToAllInSubscriptionCountries"};
            for (int i = 0; i < experimentsMethods.length; i++) {
                findAndHookMethod(GPM + ".experiments.AnalysisExperimentsManager", lPParam.classLoader, experimentsMethods[i], new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                        return true;
                    }
                });
            }

            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                findAndHookMethod(GPM + ".experiments.AnalysisExperimentsManager", lPParam.classLoader, "getFifeQualityBucket", new XC_MethodReplacement() {
                    @TargetApi(Build.VERSION_CODES.N)
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        //noinspection Since15
                        return java.util.Optional.of(2);
                    }
                });
            }*/

            // Flags: Just trying this out :P
            setStaticBooleanField(findClass(GPM + ".Flag", lPParam.classLoader), "UI_TEST_BUILD", true);

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