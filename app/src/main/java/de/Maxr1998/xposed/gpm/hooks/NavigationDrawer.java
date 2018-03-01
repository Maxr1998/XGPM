package de.Maxr1998.xposed.gpm.hooks;

import android.accounts.Account;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setIntField;

@SuppressWarnings("RedundantThrows")
class NavigationDrawer {

    @SuppressWarnings("unchecked")
    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Constant classes
            final Class screenClass = findClass(GPM + ".ui.HomeActivity.Screen", lPParam.classLoader);
            final Class homeMenuScreensClass = findClass(GPM + ".ui.HomeMenuScreens", lPParam.classLoader);

            // Remove disabled drawer items
            findAndHookMethod(homeMenuScreensClass, "getMenuScreens", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    ArrayList<Object> screens = (ArrayList<Object>) param.getResult();
                    screens.set(0, Enum.valueOf(screenClass, PREFS.getBoolean(Common.RESTORE_OLD_MAINSTAGE, false) ? "MAINSTAGE" : "ADAPTIVE_HOME"));
                    for (int i = 0; i < screens.size(); i++) {
                        String tag = (String) XposedHelpers.callMethod(screens.get(i), "getTag");
                        if (tag.equals("library")) {
                            // Required to show playlist item
                            screens.add(i + 1, Enum.valueOf(screenClass, "NO_CONTENT"));
                        } else if (PREFS.getStringSet(Common.NAV_DRAWER_HIDDEN_ITEMS, Collections.emptySet()).contains(tag)) {
                            screens.remove(i);
                            i--;
                        }
                    }
                    screens.trimToSize();
                }
            });

            // Override playlist Fragment class
            findAndHookMethod(screenClass, "addCommonFragments", Map.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Map<Object, Class<?>> map = (Map<Object, Class<?>>) param.args[0];
                    Object playlistsItem = Enum.valueOf(screenClass, "NO_CONTENT");
                    map.put(playlistsItem, findClass(GPM + ".ui.mylibrary.PlaylistRecyclerFragment", lPParam.classLoader));
                }
            });

            // Make playlist item show playlist title and icons
            findAndHookMethod(GPM + ".ui.BaseActivity", lPParam.classLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Resources res = ((Activity) param.thisObject).getResources();
                    Object playlistsItem = Enum.valueOf(screenClass, "NO_CONTENT");
                    setIntField(playlistsItem, "mTitleResId", res.getIdentifier("playlists_title", "string", GPM));
                    setIntField(playlistsItem, "mIconResourceId", res.getIdentifier("ic_reason_instantmix", "drawable", GPM));
                    setIntField(playlistsItem, "mSelectedIconResourceId", res.getIdentifier("ic_reason_instantmix", "drawable", GPM));
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

            // Remove "Downloaded only" item
            final String playDrawer = "com.google.android.play.drawer.";
            for (String hookedClass : new String[]{"PlayDrawerListViewAdapter", "PlayDrawerRecyclerViewAdapter"}) {
                findAndHookMethod(playDrawer + hookedClass, lPParam.classLoader, "updateContent", String.class, Account[].class,
                        List.class, playDrawer + "PlayDrawerLayout.PlayDrawerDownloadSwitchConfig", List.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                if (PREFS.getStringSet(Common.NAV_DRAWER_HIDDEN_ITEMS, Collections.emptySet()).contains("downloaded_only")) {
                                    param.args[3] = null;
                                }
                            }
                        });
            }

            // Remove "Get Unlimited Music"
            findAndHookMethod(GPM + ".ui.BaseActivity", lPParam.classLoader, "updateMusicDrawer", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (PREFS.getBoolean(Common.DRAWER_HIDE_UNLIMITED, false)) {
                        ((View) getObjectField(getObjectField(param.thisObject, "mPlayDrawerLayout"), "mDockedActionView")).setVisibility(View.GONE);
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }
}