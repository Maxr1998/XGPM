package de.Maxr1998.xposed.gpm.hooks;

import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class MainStage implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lPParam) throws Throwable {
        if (!lPParam.packageName.equals(GPM))
            return;

        findAndHookMethod(GPM + ".ui.common.GridFragment", lPParam.classLoader, "getScreenColumns", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                PREFS.reload();
                if (PREFS.getBoolean(Common.ALBUM_GRID_THREE_COLUMNS, false) && XposedHelpers.findClass(GPM + ".ui.common.AlbumGridFragment", lPParam.classLoader).isInstance(param.thisObject)) {
                    param.setResult((int) param.getResult() + 1);
                }
            }
        });

        findAndHookMethod(GPM + ".ui.common.MediaListRecyclerFragment", lPParam.classLoader, "getScreenColumns", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                PREFS.reload();
                if (PREFS.getBoolean(Common.ALBUM_GRID_THREE_COLUMNS, false) && XposedHelpers.findClass(GPM + ".ui.MaterialRecentFragment", lPParam.classLoader).isInstance(param.thisObject)) {
                    param.setResult((int) param.getResult() + 1);
                }
            }
        });
    }
}