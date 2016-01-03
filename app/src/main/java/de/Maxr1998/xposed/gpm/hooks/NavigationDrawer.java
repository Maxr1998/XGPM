package de.Maxr1998.xposed.gpm.hooks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.setIntField;

public class NavigationDrawer {

    @SuppressWarnings("unchecked")
    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Constant classes
            final Class screenClass = findClass(GPM + ".ui.HomeActivity.Screen", lPParam.classLoader);
            final Class homeMenuScreensClass = findClass(GPM + ".ui.HomeMenuScreens", lPParam.classLoader);

            // Make playlist item show playlist fragment
            findAndHookMethod(screenClass, "addCommonFragments", Map.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Map<Object, Class<?>> map = (Map<Object, Class<?>>) param.args[0];
                    Object playlistsItem = Enum.valueOf(screenClass, "NO_CONTENT");
                    setIntField(playlistsItem, "mTitleResId", 0x7f0b0395); // top_menu_playlists
                    setIntField(playlistsItem, "mIconResourceId", 0x7f02014f); // ic_instant_mix_gray
                    setIntField(playlistsItem, "mSelectedIconResourceId", 0x7f020150); // ic_instant_mix_orange
                    map.put(playlistsItem, findClass(GPM + ".ui.mylibrary.PlaylistRecyclerFragment", lPParam.classLoader));
                }
            });

            // Make playlist item a primary item
            findAndHookMethod(screenClass, "isPrimary", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.thisObject == Enum.valueOf(screenClass, "NO_CONTENT")) {
                        param.setResult(true);
                    }
                }
            });

            // Remove disabled drawer items
            findAndHookMethod(homeMenuScreensClass, "getMenuScreens", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    PREFS.reload();
                    ArrayList<Object> screens = new ArrayList<>(Arrays.asList((Object[]) param.getResult()));
                    for (int i = 0; i < screens.size(); i++) {
                        String tag = (String) XposedHelpers.callMethod(screens.get(i), "getTag");
                        if (PREFS.getStringSet(Common.NAV_DRAWER_HIDDEN_ITEMS, Collections.<String>emptySet()).contains(tag)) {
                            screens.remove(i);
                            i--;
                        } else if (tag.equals("library")) {
                            // Required to show playlist item
                            screens.add(i + 1, Enum.valueOf(screenClass, "NO_CONTENT"));
                        }
                    }
                    screens.trimToSize();
                    param.setResult(Arrays.copyOf(screens.toArray(), screens.size(), (Class<? extends Object[]>) findClass(GPM + ".ui.HomeActivity.Screen[]", lPParam.classLoader)));
                }
            });

            // Remove "Downloaded only" item
            findAndHookMethod(homeMenuScreensClass, "getDownloadSwitchConfig", GPM + ".ui.BaseActivity", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    PREFS.reload();
                    if (PREFS.getStringSet(Common.NAV_DRAWER_HIDDEN_ITEMS, Collections.<String>emptySet()).contains("downloaded_only")) {
                        param.setResult(null);
                    }
                }
            });

            // Remove "Get Unlimited Music"
            final String playDrawerLayout = "com.google.android.play.drawer.PlayDrawerLayout";
            findAndHookMethod(playDrawerLayout, lPParam.classLoader, "updateDockedAction", playDrawerLayout + ".PlayDrawerDockedAction", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    PREFS.reload();
                    if (PREFS.getBoolean(Common.DRAWER_HIDE_UNLIMITED, false)) {
                        param.args[0] = null;
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }
}
