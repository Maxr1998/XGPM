package de.Maxr1998.xposed.gpm.hooks;

import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

@SuppressWarnings("RedundantThrows")
class MainStage {

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Remove "Play Music for…"
            findAndHookMethod(GPM + ".ui.MaterialMainstageFragment.RecyclerAdapter", lPParam.classLoader, "showSituationCard", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (PREFS.getBoolean(Common.REMOVE_SITUATIONS, false)) {
                        param.setResult(false);
                    }
                }
            });

            // Remove recommendations
            findAndHookMethod(GPM + ".ui.MaterialMainstageFragment.RecyclerAdapter", lPParam.classLoader, "shouldShowRecommendationCluster", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (PREFS.getBoolean(Common.REMOVE_RECOMMENDATIONS, false)) {
                        param.setResult(false);
                    }
                }
            });

            /*/ 3 columns
            findAndHookMethod(GPM + ".utils.ViewUtils", lPParam.classLoader, "getScreenColumnCount", Resources.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (PREFS.getBoolean(Common.ALBUM_GRID_THREE_COLUMNS, false)) {
                        param.setResult((int) param.getResult() + 1);
                    }
                }
            });

            findAndHookMethod(GPM + ".utils.ViewUtils", lPParam.classLoader, "getAdaptiveHomeScreenColumnCount", int.class, Resources.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if ((int) param.args[0] == 1) {
                        if (PREFS.getBoolean(Common.ALBUM_GRID_THREE_COLUMNS, false)) {
                            param.setResult((int) param.getResult() + 1);
                        }
                    }
                }
            });*/
        } catch (Throwable t) {
            log(t);
        }
    }
}