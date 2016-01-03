package de.Maxr1998.xposed.gpm.hooks;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Build;
import android.os.Handler;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.hooks.NotificationMod.CLICK_BASE_ID;
import static de.Maxr1998.xposed.gpm.hooks.NotificationMod.IMAGE_BASE_ID;
import static de.Maxr1998.xposed.gpm.hooks.NotificationMod.INTENT_ACTION;
import static de.Maxr1998.xposed.gpm.hooks.NotificationMod.SEEK_COUNT_INTENT_EXTRA;
import static de.Maxr1998.xposed.gpm.hooks.NotificationMod.TEXT_BASE_ID;
import static de.Maxr1998.xposed.gpm.hooks.NotificationMod.getBoldString;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

@SuppressWarnings("unused")
public class NotificationModNext {

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            findAndHookMethod(GPM + ".playback2.MusicPlaybackService", lPParam.classLoader, "updateNotification", int.class, new XC_MethodReplacement() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    Service mService = (Service) param.thisObject;
                    Notification mNotification = (Notification) callStaticMethod(findClass(GPM + ".playback2.NotificationUtils", lPParam.classLoader), "buildNotification", param.thisObject, param.args[0]);
                    int position = getIntField(mService, "position");// TODO
                    Object songList = new Object(); // TODO
                    Cursor cursor = (Cursor) callMethod(callMethod(songList, "getWrappedSongList"), "createSyncCursor",
                            new Class[]{Context.class, String[].class, String.class}, mService, new String[]{"title", "VThumbnailUrl"}, "");

                    int activeTitle = 2, start = position - 2;

                    if (position < 3 || cursor.getCount() < 8) {
                        activeTitle = position;
                        start = 0;
                    } else if (cursor.getCount() - position < 6) {
                        activeTitle = 8 - (cursor.getCount() - position);
                        start = cursor.getCount() - 8;
                    }
                    cursor.moveToPosition(start);
                    String[] titles = new String[8];
                    for (int i = 0; i < 8; i++) {
                        titles[i] = cursor.getPosition() < cursor.getCount() ? cursor.getString(0) : null;
                        cursor.moveToNext();
                    }
                    for (int i = 0; i < 8; i++) {
                        mNotification.bigContentView.setTextViewText(TEXT_BASE_ID + i, titles[i] != null ? i == activeTitle ? getBoldString(titles[i]) : titles[i] : "");
                        mNotification.bigContentView.setImageViewResource(IMAGE_BASE_ID + i, titles[i] != null ? mService.getResources().getIdentifier("bg_default_album_art", "drawable", GPM) : android.R.color.transparent);
                        Intent queue = new Intent(INTENT_ACTION).setClass(mService, mService.getClass());
                        queue.putExtra(SEEK_COUNT_INTENT_EXTRA, titles[i] != null ? i - activeTitle : 0xff);
                        mNotification.bigContentView.setOnClickPendingIntent(CLICK_BASE_ID + i, PendingIntent.getService(mService, (int) System.currentTimeMillis() + i, queue, PendingIntent.FLAG_UPDATE_CURRENT));
                    }
                    if (((boolean) callMethod(mService, "isForeground"))) {
                        callMethod(mService, "startForegroundService", 1, mNotification);
                    } else {
                        ((NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, mNotification);
                    }
                    return null;
                }
            });
            findAndHookMethod(GPM + ".playback2.MusicPlaybackService", lPParam.classLoader, "onStartCommand", Intent.class, int.class, int.class, new XC_MethodHook() {
                @SuppressWarnings("unchecked")
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Intent intent = (Intent) param.args[0];
                    if (intent != null && intent.getAction() != null && intent.getAction().equals(INTENT_ACTION) && intent.hasExtra(SEEK_COUNT_INTENT_EXTRA)) {
                        int count = intent.getIntExtra(SEEK_COUNT_INTENT_EXTRA, 0);
                        if (count != 0xff) {
                            callMethod(param.thisObject, "Switching to count " + count);
                            // EXECUTION CONTEXT
                            Class executionContextClass = findClass(GPM + ".playback2.ExecutionContext", lPParam.classLoader);
                            Object mTaskExecutor = getObjectField(param.thisObject, "mTaskExecutor");
                            Handler mHandler = (Handler) getObjectField(param.thisObject, "mHandler");
                            Object mAsyncWakeLock = getObjectField(param.thisObject, "mAsyncWakeLock");
                            Object mLocalPlayQueueManager = getObjectField(param.thisObject, "mLocalPlayQueueManager");
                            Object executionContext = executionContextClass
                                    .getConstructor(Context.class, mTaskExecutor.getClass(), mHandler.getClass(), mAsyncWakeLock.getClass(), mLocalPlayQueueManager.getClass())
                                    .newInstance(param.thisObject, mTaskExecutor, mHandler, mAsyncWakeLock, mLocalPlayQueueManager);
                            // TASKS
                            List<Object> mPendingTasks = (List<Object>) getObjectField(param.thisObject, "mPendingTasks");
                            // TASK 1
                            Class requestClass = findClass(GPM + ".playback2.tasks.PlayItemInQueue.Request", lPParam.classLoader);
                            Object mStreamingClient = getObjectField(param.thisObject, "mStreamingClient");
                            Object mDownloadProgressListener = getObjectField(param.thisObject, "mDownloadProgressListener");
                            Object request = requestClass.getConstructor(int.class, mStreamingClient.getClass(), mDownloadProgressListener.getClass())
                                    .newInstance(0, mStreamingClient, mDownloadProgressListener);
                            mPendingTasks.add(requestClass.getDeclaringClass().getConstructor(executionContextClass, requestClass)
                                    .newInstance(executionContext, request));
                            // TASK 2
                            Class request2Class = findClass(GPM + ".playback2.tasks.FeedQueueTask.Request", lPParam.classLoader);
                            Object mMix = getObjectField(param.thisObject, "mMix");
                            Object mPlayQueueFeeder = getObjectField(param.thisObject, "mPlayQueueFeeder");
                            Object request2 = request2Class.getConstructor(mMix.getClass(), mPlayQueueFeeder.getClass()).newInstance(mMix, mPlayQueueFeeder);
                            mPendingTasks.add(request2Class.getDeclaringClass().getConstructor(executionContextClass, request2Class)
                                    .newInstance(executionContext, request2));
                            // START
                            callMethod(mHandler, "sendStartTask");
                        }
                        param.setResult(Service.START_STICKY);
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }

}