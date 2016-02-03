package de.Maxr1998.xposed.gpm.hooks;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import de.Maxr1998.trackselectorlib.NotificationHelper;
import de.Maxr1998.trackselectorlib.TrackItem;
import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getLongField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class NotificationMod {

    private static Runnable META_DATA_RELOADER;
    private static Object ART_LOADER_COMPLETION_LISTENER;

    private static ArrayList<TrackItem> TRACKS_COMPAT = new ArrayList<>();

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // TRACK SELECTION
            // Edit notification
            findAndHookMethod(GPM + ".playback.MusicPlaybackService", lPParam.classLoader, "buildLNotification", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context mContext = (Context) param.thisObject;
                    Notification mNotification = (Notification) param.getResult();
                    if (NotificationHelper.isSupported(mNotification)) {
                        ArrayList<Bundle> tracks = new ArrayList<>();
                        for (int i = 0; i < TRACKS_COMPAT.size(); i++) {
                            tracks.add(TRACKS_COMPAT.get(i).get());
                        }
                        TRACKS_COMPAT.clear();
                        NotificationHelper.insertToNotification(mNotification, tracks, mContext, getIntField(getObjectField(param.thisObject, "mDevicePlayback"), "mPlayPos"));
                    }
                }
            });
            // Initialize data loader
            findAndHookConstructor(GPM + ".playback.MusicPlaybackService", lPParam.classLoader, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    META_DATA_RELOADER = new Runnable() {
                        @SuppressWarnings("unchecked")
                        @Override
                        public void run() {
                            try {
                                Context mService = (Context) param.thisObject;
                                Object mDevicePlayback = getObjectField(mService, "mDevicePlayback");
                                Object mSongList = callMethod(mDevicePlayback, "getMediaList");
                                Cursor cursor = (Cursor) callMethod(mSongList, "createSyncCursor", new Class[]{Context.class, String[].class, String.class},
                                        mService, new String[]{"title", "Nid", "album_id", "artist", "duration"}, "");

                                final String url;
                                if (findClass(GPM + ".medialist.ExternalSongList", lPParam.classLoader).isInstance(mSongList)) {
                                    url = callMethod(mSongList, "getAlbumArtUrl", mService).toString();
                                } else {
                                    url = null;
                                }
                                TRACKS_COMPAT.clear();

                                // Loading data
                                log("RENDERER: Starting…");
                                cursor.moveToPosition(0);
                                do {
                                    TrackItem track = new TrackItem()
                                            .setTitle(cursor.getString(0))
                                            .setArtist(cursor.getString(3))
                                            .setDuration(callStaticMethod(findClass(GPM + ".utils.MusicUtils", lPParam.classLoader), "makeTimeString", mService, cursor.getInt(4) / 1000).toString());
                                    String mMetajamId = cursor.getString(1);
                                    long mAlbumId = cursor.getLong(2);
                                    if (mMetajamId != null && mAlbumId != 0) {
                                        Object mDocument = callStaticMethod(findClass(GPM + ".utils.NowPlayingUtils", lPParam.classLoader), "createNowPlayingArtDocument", mMetajamId, mAlbumId, url);
                                        Object mDescriptor = callMethod(callStaticMethod(findClass(GPM + ".Factory", lPParam.classLoader), "getArtDescriptorFactory"),
                                                "createArtDescriptor", getStaticObjectField(findClass(GPM + ".art.ArtType", lPParam.classLoader), "NOTIFICATION"),
                                                (int) (mService.getResources().getDisplayMetrics().density * 48), 1.0f, mDocument);
                                        Object mArtResolver = callStaticMethod(findClass(GPM + ".art.ArtResolver", lPParam.classLoader), "getInstance", mService);
                                        Object mRequest = callMethod(mArtResolver, "getAndRetainArtIfAvailable", mDescriptor);
                                        if (mRequest != null && (boolean) callMethod(mRequest, "didRenderSuccessfully")) {
                                            track.setArt((Bitmap) callMethod(mRequest, "getResultBitmap"));
                                        } else {
                                            mRequest = callMethod(mArtResolver, "getArt", mDescriptor, ART_LOADER_COMPLETION_LISTENER);
                                            track.id = mAlbumId;
                                            callMethod(mRequest, "retain");
                                        }
                                    }
                                    TRACKS_COMPAT.add(track);
                                } while (cursor.moveToNext());
                            } catch (Throwable t) {
                                log(t);
                            }
                        }
                    };
                    ART_LOADER_COMPLETION_LISTENER = Proxy.newProxyInstance(lPParam.classLoader, new Class[]{
                            findClass(GPM + ".art.ArtResolver.RequestListener", lPParam.classLoader)}, new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            if (TRACKS_COMPAT.isEmpty()) {
                                return null;
                            }
                            Object mRequest = args[0];
                            long mId = getLongField(getObjectField(getObjectField(mRequest, "mDescriptor"), "identifier"), "mId");
                            for (int i = 0; i < TRACKS_COMPAT.size(); i++) {
                                if (mId == TRACKS_COMPAT.get(i).id) {
                                    if ((boolean) callMethod(mRequest, "didRenderSuccessfully")) {
                                        TRACKS_COMPAT.get(i).setArt((Bitmap) callMethod(mRequest, "getResultBitmap"));
                                    }
                                }
                            }
                            callMethod(mRequest, "release");
                            return null;
                        }
                    });
                }
            });
            // Run data loader on update
            findAndHookMethod(GPM + ".playback.MusicPlaybackService", lPParam.classLoader, "updateNotificationAndMediaSessionMetadataAsync", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Class async = findClass(GPM + ".utils.async.AsyncWorkers", lPParam.classLoader);
                    Object sBackendServiceWorker = getStaticObjectField(async, "sBackendServiceWorker");
                    callStaticMethod(async, "runAsync", sBackendServiceWorker, META_DATA_RELOADER);
                }
            });
            // Handle callbacks from notification
            findAndHookMethod(GPM + ".playback.MusicPlaybackService", lPParam.classLoader, "onStartCommand", Intent.class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Intent intent = (Intent) param.args[0];
                    if (NotificationHelper.checkIntent(intent)) {
                        int count = NotificationHelper.getPosition(intent);
                        Object mDevicePlayback = getObjectField(param.thisObject, "mDevicePlayback");
                        if (count != 0) {
                            callMethod(mDevicePlayback, "seek", 0L);
                            AtomicInteger seekCount = (AtomicInteger) getObjectField(mDevicePlayback, "mPendingMediaButtonSeekCount");
                            seekCount.addAndGet(count);
                            callMethod(mDevicePlayback, "handleMediaButtonSeek", new Class[]{boolean.class, int.class}, true, 4);
                        } else {
                            if (!(boolean) callMethod(mDevicePlayback, "isPlaying")) {
                                callMethod(mDevicePlayback, "play");
                            }
                        }
                        param.setResult(Service.START_STICKY);
                    }
                }
            });

            // SWITCH TO OLD DESIGN
            findAndHookMethod(GPM + ".playback.MusicPlaybackService", lPParam.classLoader, "addLNotificationAction", Notification.Builder.class, Notification.WearableExtender.class, int.class, int.class, int.class, PendingIntent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    PREFS.reload();
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