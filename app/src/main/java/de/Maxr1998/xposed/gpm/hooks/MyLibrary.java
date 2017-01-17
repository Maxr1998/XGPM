package de.Maxr1998.xposed.gpm.hooks;

import java.util.Collections;
import java.util.List;

import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

class MyLibrary {

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            findAndHookMethod(GPM + ".ui.explore.DynamicTabbedFragment", lPParam.classLoader, "initializeTabs", List.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject.getClass().getSimpleName().equals("MyLibraryFragment")) {
                        PREFS.reload();
                        List tabs = (List) param.args[0];
                        for (int i = 0; i < tabs.size(); i++) {
                            String className = ((Class) getObjectField(getObjectField(tabs.get(i), "mFragmentInfo"), "mFragmentClass")).getSimpleName();
                            if (PREFS.getStringSet(Common.MY_LIBRARY_HIDDEN_TABS, Collections.<String>emptySet()).contains(className)) {
                                tabs.remove(i--);
                            }
                        }
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }
}