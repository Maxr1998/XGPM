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
import android.support.v4.util.ArrayMap;

import java.util.ArrayList;
import java.util.HashSet;
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
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class NotificationMod {

    private static Runnable META_DATA_RELOADER;
    private static ArrayMap<Long, Bundle> TRACKS_COMPAT = new ArrayMap<>();
    private static ArrayMap<Long, Bitmap> bitmaps = new ArrayMap<>();
    private static ArrayList<Bundle> TRACKS_COMPAT_TEMP = new ArrayList<>();
    private static Bitmap grayBitmap = null;
    private final static int limit = 40;

    public static Uri getMediaStoreAlbumArt(long n) {
        return Uri.parse("content://media/external/audio/albumart/" + n);
    }

    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both height and width larger than the
            // requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth)
                inSampleSize *= 2;
        }
        return inSampleSize;
    }

    public static Bitmap decodeSampledBitmapFromFile(String pathName, int reqWidth, int reqHeight) {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inPreferQualityOverSpeed = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inDither = true;
        BitmapFactory.decodeFile(pathName, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(pathName, options);
    }

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            //Create gray bitmap
            grayBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565);
            grayBitmap.eraseColor(Color.LTGRAY);
            // TRACK SELECTION
            // Edit notification
            findAndHookMethod(GPM + ".playback.MusicPlaybackService", lPParam.classLoader, "buildLNotification", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context mContext = (Context) param.thisObject;
                    Notification mNotification = (Notification) param.getResult();
                    if (NotificationHelper.isSupported(mNotification))
                        NotificationHelper.insertToNotification(mNotification, TRACKS_COMPAT_TEMP, mContext, getIntField(getObjectField(param.thisObject, "mDevicePlayback"), "mPlayPos"));
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
                                Cursor cursor = (Cursor) callMethod(mSongList, "createSyncCursor", new Class[]{Context.class, String[].class},
                                        mService, new String[]{"title", "SongId", "artist", "duration", "AlbumArtLocation"});
                                cursor.moveToPosition(0);
                                //Initialize vars
                                TRACKS_COMPAT_TEMP = new ArrayList<>();
                                bitmaps = new ArrayMap<>();
                                int i = 0;
                                // Loading data
                                ContentResolver cr = mService.getContentResolver();
                                Set<Long> ids = new HashSet<>();
                                PREFS.reload();
                                boolean noArt = PREFS.getBoolean(Common.NP_NO_ALBUM_ART, false);
                                do {
                                    if(i>limit && !noArt)
                                        break;
                                    long trackID = cursor.getLong(1);
                                    Bundle preTrack = TRACKS_COMPAT.get(trackID);
                                    if(preTrack!=null) {
                                        i++;
                                        TRACKS_COMPAT_TEMP.add(preTrack);
                                        continue;
                                    }
                                    TrackItem track = new TrackItem();
                                    track.id = trackID;
                                    if (!ids.contains(track.id)) {
                                        track.setTitle(cursor.getString(0))
                                                .setArtist(cursor.getString(2))
                                                .setDuration(callStaticMethod(findClass(GPM + ".utils.StringUtils", lPParam.classLoader),
                                                        "makeTimeString", mService, cursor.getInt(3)).toString());
                                        if(!noArt) {
                                            long res = Long.parseLong(cursor.getString(4).substring("mediastore:".length()));
                                            Bitmap bitmap = bitmaps.get(res);
                                            if (bitmap != null)
                                                track.setArt(bitmap);
                                            else {
                                                Uri uri = getMediaStoreAlbumArt(res);
                                                Cursor cur = cr.query(uri, new String[]{"_data"}, null, null, null);
                                                if (cur != null) {
                                                    cur.moveToFirst();
                                                    bitmap = decodeSampledBitmapFromFile(cur.getString(0), 64, 64);
                                                    cur.close();
                                                }
                                                if (bitmap == null)
                                                    bitmap = grayBitmap;
                                                bitmaps.put(res, bitmap);
                                                track.setArt(bitmap);
                                            }
                                        }
                                        else
                                            track.setArt(grayBitmap);
                                        ids.add(track.id);
                                        preTrack = track.get();
                                        TRACKS_COMPAT.put(trackID, preTrack);
                                        TRACKS_COMPAT_TEMP.add(preTrack);
                                        i++;
                                    }
                                } while (cursor.moveToNext());
                                cursor.close();
                            } catch (Throwable t) {
                                log(t);
                            }
                        }
                    };
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
