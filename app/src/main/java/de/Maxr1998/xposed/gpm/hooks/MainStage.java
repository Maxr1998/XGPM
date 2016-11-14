package de.Maxr1998.xposed.gpm.hooks;

import android.content.Context;
import android.widget.ListAdapter;

import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

class MainStage {

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Remove "Play Music for…"
            findAndHookMethod(GPM + ".ui.MaterialMainstageFragment.RecyclerAdapter", lPParam.classLoader, "showSituationCard", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    PREFS.reload();
                    if (PREFS.getBoolean(Common.REMOVE_SITUATIONS, false)) {
                        param.setResult(false);
                    }
                }
            });

            // Remove recommendations
            findAndHookMethod(GPM + ".ui.MaterialMainstageFragment.RecyclerAdapter", lPParam.classLoader, "shouldShowRecommendationCluster", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    PREFS.reload();
                    if (PREFS.getBoolean(Common.REMOVE_RECOMMENDATIONS, false)) {
                        param.setResult(false);
                    }
                }
            });

            // 3 columns
            findAndHookConstructor(GPM + ".ui.GridAdapterWrapper", lPParam.classLoader, Context.class, ListAdapter.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    PREFS.reload();
                    if (PREFS.getBoolean(Common.ALBUM_GRID_THREE_COLUMNS, false) && findClass(GPM + ".ui.common.AlbumGridFragment", lPParam.classLoader).isInstance(param.thisObject)) {
                        param.args[2] = (int) param.args[2] + 1;
                    }
                }
            });

            // 3 columns
            findAndHookMethod(GPM + ".ui.common.MediaListRecyclerFragment", lPParam.classLoader, "getScreenColumns", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    PREFS.reload();
                    if (PREFS.getBoolean(Common.ALBUM_GRID_THREE_COLUMNS, false) && findClass(GPM + ".ui.MaterialRecentFragment", lPParam.classLoader).isInstance(param.thisObject)) {
                        param.setResult((int) param.getResult() + 1);
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }
}