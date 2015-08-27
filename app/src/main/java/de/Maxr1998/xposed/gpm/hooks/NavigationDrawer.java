package de.Maxr1998.xposed.gpm.hooks;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;

import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;

public class NavigationDrawer implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lPParam) throws Throwable {
        if (!lPParam.packageName.equals(GPM))
            return;

        // Remove "Get Unlimited Music"
        findAndHookMethod("com.google.android.play.drawer.PlayDrawerLayout", lPParam.classLoader, "updateDockedAction",
                "com.google.android.play.drawer.PlayDrawerLayout.PlayDrawerDockedAction", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        PREFS.reload();
                        if (PREFS.getBoolean(Common.DRAWER_HIDE_UNLIMITED, false)) {
                            param.args[0] = null;
                        }
                    }
                });


        // Remove "Shop", "Help" and "Feedback" items
        findAndHookMethod(GPM + ".ui.HomeMenuScreens", lPParam.classLoader, "getMenuScreens", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                PREFS.reload();
                ArrayList<?> screens = new ArrayList<>(Arrays.asList((Object[]) param.getResult()));
                for (int i = 0; i < screens.size(); i++) {
                    String tag = ((String) XposedHelpers.callMethod(screens.get(i), "getTag")).toLowerCase();
                    if ((tag.equals("shop") && PREFS.getBoolean(Common.DRAWER_HIDE_SHOP, false)) ||
                            (tag.equals("help") && PREFS.getBoolean(Common.DRAWER_HIDE_HELP, false)) ||
                            (tag.equals("feedback") && PREFS.getBoolean(Common.DRAWER_HIDE_FEEDBACK, false))) {
                        screens.remove(i);
                        i--;
                    }
                }
                screens.trimToSize();
                param.setResult(
                        Arrays.copyOf(screens.toArray(), screens.size(),
                                (Class<? extends Object[]>) findClass(GPM + ".ui.HomeActivity.Screen[]", lPParam.classLoader)));
            }
        });
    }
}
