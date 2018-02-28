package de.Maxr1998.xposed.gpm.hooks;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;

import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@SuppressWarnings("RedundantThrows")
class NotificationMod {

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // SWITCH TO OLD DESIGN
            findAndHookMethod(GPM + ".playback.MusicPlaybackService", lPParam.classLoader, "addLNotificationAction", Notification.Builder.class, Notification.WearableExtender.class, int.class, int.class, int.class, PendingIntent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (PREFS.getBoolean(Common.NOTIFICATION_NARROW, false)) {
                        // Set flag
                        Bundle extra = new Bundle(1);
                        extra.putInt("xgpm", 1);
                        ((Notification.Builder) param.args[0]).addExtras(extra);
                        // Remove ThumbsUp/Down Actions
                        Resources res = ((Context) param.thisObject).getResources();
                        int accessibilityId = (int) param.args[4];
                        int thumbsUpId = res.getIdentifier("accessibility_thumbsUp", "string", GPM);
                        int thumbsDownId = res.getIdentifier("accessibility_thumbsDown", "string", GPM);
                        if (accessibilityId == thumbsUpId || accessibilityId == thumbsDownId) {
                            param.setResult(null);
                        }
                    }
                }
            });
            // Prevent force close
            findAndHookMethod("android.app.Notification.Builder", lPParam.classLoader, "build", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Notification.Builder builder = (Notification.Builder) param.thisObject;
                    if (builder.getExtras().getInt("xgpm") == 1) {
                        ((Notification.MediaStyle) getObjectField(builder, "mStyle")).setShowActionsInCompactView(0, 1, 2);
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }
}