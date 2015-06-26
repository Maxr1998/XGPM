package de.Maxr1998.xposed.gpm.hooks;

import java.lang.reflect.Field;

import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class MainStage implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lPParam) throws Throwable {
        if (!lPParam.packageName.equals(GPM))
            return;

        // Change default pane
        if (PREFS.getBoolean(Common.DEFAULT_MY_LIBRARY, false)) {
            Field defaultScreen = findClass(GPM + ".ui.HomeActivity", lPParam.classLoader).getDeclaredField("DEFAULT_SCREEN");
            defaultScreen.setAccessible(true);
            defaultScreen.set(null, XposedHelpers.getStaticObjectField(findClass(GPM + ".ui.HomeActivity.Screen", lPParam.classLoader), "MY_LIBRARY"));
        }

        // 3 columns
        findAndHookMethod(GPM + ".ui.common.GridFragment", lPParam.classLoader, "getScreenColumns", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                PREFS.reload();
                if (PREFS.getBoolean(Common.ALBUM_GRID_THREE_COLUMNS, false) && findClass(GPM + ".ui.common.AlbumGridFragment", lPParam.classLoader).isInstance(param.thisObject)) {
                    param.setResult((int) param.getResult() + 1);
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
    }
}