package de.Maxr1998.xposed.gpm.hooks;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
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
class NotificationMod {

    private static Runnable META_DATA_RELOADER;
    private static Object ART_LOADER_COMPLETION_LISTENER;
    private static LinkedHashMap<Long, TrackItem> TRACK_ITEMS_CACHE = new LinkedHashMap<>();
    private static ArrayList<TrackItem> TRACK_ITEMS = new ArrayList<>();
    private static Bitmap GRAY_BITMAP;

    @SuppressWarnings("deprecation")
    private static Bitmap decodeSampledBitmapFromFile(String pathName, int size) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

        // Test-run to get out values
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(pathName, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, size);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(pathName, options);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int size) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > size && width > size) {
            // Calculate the largest inSampleSize value that is a power of 2 and keeps both height and width larger than the
            // requested height and width.
            while ((height / (inSampleSize * 2)) > size && (width / (inSampleSize * 2)) > size)
                inSampleSize *= 2;
        }
        return inSampleSize;
    }

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            //Create gray bitmap
            GRAY_BITMAP = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
            GRAY_BITMAP.eraseColor(Color.LTGRAY);
            // TRACK SELECTION
            // Edit notification
            findAndHookMethod(GPM + ".playback.MusicPlaybackService", lPParam.classLoader, "buildLNotification", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context mContext = (Context) param.thisObject;
                    Notification mNotification = (Notification) param.getResult();
                    PREFS.reload();
                    if (NotificationHelper.isSupported(mNotification)) {
                        ArrayList<Bundle> tracks = new ArrayList<>();
                        for (int i = 0; i < TRACK_ITEMS.size(); i++)
                            tracks.add(TRACK_ITEMS.get(i).get());
                        TRACK_ITEMS.clear();
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
                                if (mSongList == null)
                                    return;
                                PREFS.reload();

                                // Create database Cursor from current playlist
                                Cursor cursor = (Cursor) callMethod(mSongList, "createSyncCursor", new Class[]{Context.class, String[].class},
                                        mService, new String[]{"SongId", "title", "artist", "duration", "Nid", "album_id", "AlbumArtLocation"});

                                final String url;
                                if (findClass(GPM + ".medialist.ExternalSongList", lPParam.classLoader).isInstance(mSongList))
                                    url = callMethod(mSongList, "getAlbumArtUrl", mService).toString();
                                else
                                    url = null;

                                TRACK_ITEMS.clear();

                                // Loading data
                                Set<Long> loadedAlbumIds = new HashSet<>();
                                ContentResolver contentResolver = mService.getContentResolver();
                                cursor.moveToPosition(0);
                                int position = getIntField(mDevicePlayback, "mPlayPos");
                                do {
                                    TrackItem track = TRACK_ITEMS_CACHE.get(cursor.getLong(0));
                                    String artLocation = cursor.getString(6);
                                    boolean artAvailableAndDesired = artLocation != null && !PREFS.getBoolean(Common.NP_NO_ALBUM_ART, false);
                                    if (track == null || (track.getArt() == GRAY_BITMAP && artAvailableAndDesired /* true if loading cover failed previously*/)) {
                                        track = new TrackItem()
                                                .setTitle(cursor.getString(1))
                                                .setArtist(cursor.getString(2))
                                                .setDuration(callStaticMethod(findClass(GPM + ".utils.StringUtils", lPParam.classLoader),
                                                        "makeTimeString", mService, cursor.getInt(3)).toString())
                                                .setArt(GRAY_BITMAP);
                                        // Load cover if available, desired and allowed
                                        if (artAvailableAndDesired && Math.abs(position - cursor.getPosition()) <= 40) {
                                            // Load from mediastore if local file
                                            if (artLocation.startsWith("mediastore:")) {
                                                long mediaStoreId;
                                                try {
                                                    mediaStoreId = Long.parseLong(artLocation.substring("mediastore:".length()));
                                                    Uri uri = Uri.parse("content://media/external/audio/albumart/" + mediaStoreId);
                                                    Cursor mediaStoreCursor = contentResolver.query(uri, new String[]{"_data"}, null, null, null);
                                                    if (mediaStoreCursor != null) {
                                                        mediaStoreCursor.moveToFirst();
                                                        Bitmap mediaStoreArt = decodeSampledBitmapFromFile(mediaStoreCursor.getString(0), (int) (mService.getResources().getDisplayMetrics().density * 48));
                                                        mediaStoreCursor.close();
                                                        track.setArt(mediaStoreArt);
                                                    }
                                                } catch (NumberFormatException e) {
                                                    // do nothing
                                                }
                                            } else {
                                                // Load from GPM cache if cloud file
                                                String mMetajamId = cursor.getString(4);
                                                long mAlbumId = cursor.getLong(5);
                                                if (mMetajamId != null && mAlbumId != 0) {
                                                    Object mDocument = callStaticMethod(findClass(GPM + ".utils.NowPlayingUtils", lPParam.classLoader),
                                                            "createNowPlayingArtDocument", mMetajamId, mAlbumId, url);
                                                    Object mDescriptor = callMethod(callStaticMethod(findClass(GPM + ".Factory", lPParam.classLoader),
                                                            "getArtDescriptorFactory"),
                                                            "createArtDescriptor", getStaticObjectField(findClass(GPM + ".art.ArtType", lPParam.classLoader), "NOTIFICATION"),
                                                            (int) (mService.getResources().getDisplayMetrics().density * 48), 1.0f, mDocument);
                                                    Object mArtResolver = callStaticMethod(findClass(GPM + ".Factory", lPParam.classLoader),
                                                            "getArtResolver", mService);
                                                    Object mRequest = callMethod(mArtResolver, "getAndRetainArtIfAvailable", mDescriptor);
                                                    if (mRequest != null && (boolean) callMethod(mRequest, "didRenderSuccessfully")) {
                                                        track.setArt((Bitmap) callMethod(mRequest, "getResultBitmap"));
                                                    } else {
                                                        track.id = mAlbumId;
                                                        if (!loadedAlbumIds.contains(track.id)) {
                                                            loadedAlbumIds.add(track.id);
                                                            mRequest = callMethod(mArtResolver, "getArt", mDescriptor, ART_LOADER_COMPLETION_LISTENER);
                                                            callMethod(mRequest, "retain");
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        TRACK_ITEMS_CACHE.put(cursor.getLong(0), track);
                                    }
                                    TRACK_ITEMS.add(track);
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
                            if (TRACK_ITEMS.isEmpty()) {
                                return null;
                            }
                            Object mRequest = args[0];
                            long mId = getLongField(getObjectField(getObjectField(mRequest, "mDescriptor"), "identifier"), "mId");
                            for (int i = 0; i < TRACK_ITEMS.size(); i++) {
                                if (mId == TRACK_ITEMS.get(i).id) {
                                    TRACK_ITEMS.get(i).setArt((boolean) callMethod(mRequest, "didRenderSuccessfully") ? (Bitmap) callMethod(mRequest, "getResultBitmap") : GRAY_BITMAP);
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
                            callMethod(mDevicePlayback, "handleMediaButtonSeek", callStaticMethod(findClass(GPM + ".playback.PlaybackJustification", lPParam.classLoader), "userNext"));
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