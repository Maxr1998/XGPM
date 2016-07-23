package de.Maxr1998.xposed.gpm.hooks;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.setIntField;
import static de.robv.android.xposed.XposedHelpers.setStaticObjectField;

public class NavigationDrawer {

    @SuppressWarnings("unchecked")
    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Enable new adaptive home
            findAndHookMethod(GPM + ".Feature", lPParam.classLoader, "isAdaptiveHomeEnabled", Context.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    PREFS.reload();
                    return PREFS.getBoolean(Common.DRAWER_ENABLE_ADAPTIVE_HOME, false);
                }
            });
            setStaticObjectField(findClass(GPM + ".sync.api.MusicUrl", lPParam.classLoader), "MUSIC_PA_URL_HOST", "https://mclients.googleapis.com/music");

            // Constant classes
            final Class screenClass = findClass(GPM + ".ui.HomeActivity.Screen", lPParam.classLoader);
            final Class homeMenuScreensClass = findClass(GPM + ".ui.HomeMenuScreens", lPParam.classLoader);

            // Make playlist item show playlist fragment
            findAndHookMethod(GPM + ".ui.BaseActivity", lPParam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Resources res = ((Activity) param.thisObject).getResources();
                    Object playlistsItem = Enum.valueOf(screenClass, "NO_CONTENT");
                    setIntField(playlistsItem, "mTitleResId", res.getIdentifier("top_menu_playlists", "string", GPM));
                    setIntField(playlistsItem, "mIconResourceId", res.getIdentifier("ic_instant_mix_black", "drawable", GPM));
                    setIntField(playlistsItem, "mSelectedIconResourceId", res.getIdentifier("ic_instant_mix_orange", "drawable", GPM));
                }
            });

            findAndHookMethod(screenClass, "addCommonFragments", Map.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Map<Object, Class<?>> map = (Map<Object, Class<?>>) param.args[0];
                    Object playlistsItem = Enum.valueOf(screenClass, "NO_CONTENT");
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
            findAndHookMethod(homeMenuScreensClass, "getMenuScreens", Context.class, new XC_MethodHook() {
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

            // Enable podcasts
            findAndHookMethod(GPM + ".utils.ConfigUtils", lPParam.classLoader, "isPodcastsEnabled", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return true;
                }
            });

            findAndHookMethod(GPM + ".utils.ConfigUtils", lPParam.classLoader, "isPodcastSyncEnabled", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return true;
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }
}
