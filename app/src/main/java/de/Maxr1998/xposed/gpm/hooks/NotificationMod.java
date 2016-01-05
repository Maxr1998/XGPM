package de.Maxr1998.xposed.gpm.hooks;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.AdapterView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import de.Maxr1998.xposed.gpm.Common;
import de.Maxr1998.xposed.gpm.hooks.track.CustomTrackAdapter;
import de.Maxr1998.xposed.gpm.hooks.track.IntentView;
import de.Maxr1998.xposed.gpm.hooks.track.TrackItem;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static android.widget.RelativeLayout.TRUE;
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

    public static final int INTENT_VIEW_ID = 0x7f0f0200;
    public static final String INTENT_ACTION = "com.android.music.musicservicecommand.queue";
    public static final String SEEK_COUNT_EXTRA = "new_queue_position";
    public static final String CURRENT_PLAYING_POSITION_EXTRA = "current_queue_position";
    public static final String TRACK_INFO_EXTRA = "track_data";
    public static final String REPLY_INTENT_EXTRA = "reply";
    private static Runnable META_DATA_RELOADER;
    private static Object ART_LOADER_COMPLETION_LISTENER;
    private static ArrayList<TrackItem> TRACKS = new ArrayList<>();

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // TRACK SELECTION
            // Edit notification
            findAndHookMethod(GPM + ".playback.MusicPlaybackService", lPParam.classLoader, "buildLNotification", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context mContext = (Context) param.thisObject;
                    Notification mNotification = (Notification) param.getResult();
                    Intent data = new Intent();
                    data.putExtra(CURRENT_PLAYING_POSITION_EXTRA, getIntField(getObjectField(param.thisObject, "mDevicePlayback"), "mPlayPos"));
                    data.putParcelableArrayListExtra(TRACK_INFO_EXTRA, TRACKS);
                    Intent reply = new Intent(INTENT_ACTION).setClass(mContext, mContext.getClass());
                    data.putExtra(REPLY_INTENT_EXTRA, PendingIntent.getService(mContext, 0, reply, PendingIntent.FLAG_UPDATE_CURRENT));
                    mNotification.bigContentView.setIntent(INTENT_VIEW_ID, "resolveIntent", data);
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

                                final Object mArtTypeNotification = getStaticObjectField(findClass(GPM + ".art.ArtType", lPParam.classLoader), "NOTIFICATION");
                                final Constructor mArtDescriptorConstructor = findClass(GPM + ".art.DocumentArtDescriptor", lPParam.classLoader)
                                        .getConstructor(mArtTypeNotification.getClass(), int.class,
                                                float.class, findClass(GPM + ".ui.cardlib.model.Document", lPParam.classLoader));
                                final String url;
                                if (findClass(GPM + ".medialist.ExternalSongList", lPParam.classLoader).isInstance(mSongList)) {
                                    url = callMethod(mSongList, "getAlbumArtUrl", mService).toString();
                                } else {
                                    url = null;
                                }
                                TRACKS.clear();

                                // Loading data
                                log("RENDERER: Starting…");
                                cursor.moveToPosition(0);
                                do {
                                    TrackItem track = new TrackItem()
                                            .setTitle(cursor.getString(0))
                                            .setArtist(cursor.getString(3))
                                            .setDuration(callStaticMethod(findClass(GPM + ".utils.MusicUtils", lPParam.classLoader), "makeTimeString", mService, cursor.getInt(4) / 1000).toString());
                                    String mMetajamId = cursor.getPosition() < cursor.getCount() ? cursor.getString(1) : null;
                                    long mAlbumId = cursor.getPosition() < cursor.getCount() ? cursor.getLong(2) : 0;
                                    if (mMetajamId != null && mAlbumId != 0) {
                                        Object mDocument = callStaticMethod(findClass(GPM + ".utils.NowPlayingUtils", lPParam.classLoader), "createNowPlayingArtDocument", mMetajamId, mAlbumId, url);
                                        Object mDescriptor = mArtDescriptorConstructor.newInstance(mArtTypeNotification, (int) (mService.getResources().getDisplayMetrics().density * 48), 1.0f, mDocument);
                                        Object mArtResolver = callStaticMethod(findClass(GPM + ".art.ArtResolver", lPParam.classLoader), "getInstance", mService);
                                        Object mRequest = callMethod(mArtResolver, "getAndRetainArtIfAvailable", mDescriptor);
                                        boolean doRequest = true;
                                        if (mRequest != null) {
                                            if ((boolean) callMethod(mRequest, "didRenderSuccessfully")) {
                                                track.setArt((Bitmap) callMethod(mRequest, "getResultBitmap"));
                                                doRequest = false;
                                            }
                                            callMethod(mRequest, "release");
                                        }
                                        if (doRequest) {
                                            mRequest = callMethod(mArtResolver, "getArt", mDescriptor, ART_LOADER_COMPLETION_LISTENER);
                                            track.albumId = mAlbumId;
                                            callMethod(mRequest, "retain");
                                        }
                                    }
                                    TRACKS.add(track);
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
                            Object mRequest = args[0];
                            long mId = getLongField(getObjectField(getObjectField(mRequest, "mDescriptor"), "identifier"), "mId");
                            for (int i = 0; i < TRACKS.size(); i++) {
                                if (mId == TRACKS.get(i).albumId) {
                                    if ((boolean) callMethod(mRequest, "didRenderSuccessfully")) {
                                        TRACKS.get(i).setArt((Bitmap) callMethod(mRequest, "getResultBitmap"));
                                    }
                                    callMethod(mRequest, "release");
                                }
                            }
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
                    if (intent != null && intent.getAction() != null && intent.getAction().equals(INTENT_ACTION) && intent.hasExtra(SEEK_COUNT_EXTRA)) {
                        int count = intent.getIntExtra(SEEK_COUNT_EXTRA, 0);
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

    public static void initUI(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Edit notification view
            findAndHookMethod("android.widget.RemoteViews", lPParam.classLoader, "performApply", View.class, ViewGroup.class, findClass("android.widget.RemoteViews.OnClickHandler", lPParam.classLoader), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    View v = (View) param.args[0];
                    if (v instanceof RelativeLayout) {
                        final RelativeLayout root = (RelativeLayout) v;
                        if ((root.getChildCount() == 4
                                && (root.getChildAt(0) instanceof FrameLayout || root.getChildAt(0) instanceof ImageView)
                                && root.getChildAt(1) instanceof LinearLayout
                                && root.getChildAt(2) instanceof LinearLayout
                                && root.getChildAt(3) instanceof ImageView)
                                || root.getTag().toString().matches("bigMedia(Narrow)?")) {
                            root.setTag("xgpmBigMedia");
                            final Resources res = root.getResources();
                            final float density = res.getDisplayMetrics().density;
                            final ViewGroup.LayoutParams rootParams = root.getLayoutParams();
                            final ImageButton queueButton = new ImageButton(root.getContext());
                            final ListView queueLayout = new ListView(root.getContext());
                            final View.OnClickListener close = new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    // Close
                                    Animator anim = ViewAnimationUtils.createCircularReveal(queueLayout, (int) (queueButton.getX() + queueButton.getWidth() / 2), (int) (queueButton.getY() + queueButton.getHeight() / 2), density * 416, density * 24);
                                    anim.addListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            super.onAnimationEnd(animation);
                                            queueLayout.setVisibility(View.GONE);
                                            queueButton.setImageDrawable(res.getDrawable(res.getIdentifier("ic_queue_dark", "drawable", GPM), null));
                                            queueButton.setColorFilter(Color.WHITE);
                                        }
                                    });
                                    anim.start();
                                    Animation collapse = new Animation() {
                                        @Override
                                        protected void applyTransformation(float interpolatedTime, Transformation t) {
                                            rootParams.height = (int) (density * 128 * (2f - interpolatedTime));
                                            root.requestLayout();
                                        }

                                        @Override
                                        public boolean willChangeBounds() {
                                            return true;
                                        }
                                    };
                                    collapse.setDuration(300);
                                    root.startAnimation(collapse);
                                }
                            };
                            final View.OnClickListener toggle = new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    if (queueLayout.getVisibility() == View.VISIBLE) {
                                        close.onClick(v);
                                        return;
                                    }
                                    Animation expand = new Animation() {
                                        @Override
                                        protected void applyTransformation(float interpolatedTime, Transformation t) {
                                            rootParams.height = (int) (density * 128 * (1f + interpolatedTime));
                                            root.requestLayout();
                                        }

                                        @Override
                                        public boolean willChangeBounds() {
                                            return true;
                                        }
                                    };
                                    expand.setDuration(300);
                                    root.startAnimation(expand);
                                    queueLayout.setVisibility(View.VISIBLE);
                                    Animator reveal = ViewAnimationUtils.createCircularReveal(queueLayout, (int) (v.getX() + v.getWidth() / 2), (int) (v.getY() + v.getHeight() / 2), density * 24, density * 416);
                                    reveal.addListener(new AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(Animator animation) {
                                            super.onAnimationEnd(animation);
                                            queueButton.setImageDrawable(res.getDrawable(res.getIdentifier("btn_close_medium", "drawable", GPM), null));
                                            queueButton.setColorFilter(Color.parseColor("#ff212121"));
                                        }
                                    });
                                    reveal.start();
                                }
                            };
                            // Queue button
                            queueButton.setImageDrawable(res.getDrawable(res.getIdentifier("ic_queue_dark", "drawable", GPM), null));
                            queueButton.setColorFilter(Color.WHITE);
                            queueButton.setBackground(null);
                            RelativeLayout.LayoutParams buttonParams = new RelativeLayout.LayoutParams((int) (density * 48), (int) (density * 48));
                            buttonParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, TRUE);
                            buttonParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, TRUE);
                            buttonParams.addRule(RelativeLayout.ALIGN_PARENT_END, TRUE);
                            queueButton.setLayoutParams(buttonParams);
                            queueButton.setOnClickListener(toggle);
                            // Prevent text overlapping queue button
                            ViewGroup.MarginLayoutParams titleContainerParams = (ViewGroup.MarginLayoutParams) root.getChildAt(1).getLayoutParams();
                            titleContainerParams.rightMargin = (int) (density * 48);
                            titleContainerParams.setMarginEnd(titleContainerParams.rightMargin);
                            // Track container
                            queueLayout.setBackgroundColor(Color.WHITE);
                            queueLayout.setClickable(true);
                            queueLayout.setVisibility(View.GONE);
                            queueLayout.setOnTouchListener(new View.OnTouchListener() {
                                @Override
                                public boolean onTouch(View v, MotionEvent event) {
                                    ViewParent mScrollLayout = v.getParent().getParent().getParent().getParent();
                                    mScrollLayout.requestDisallowInterceptTouchEvent(true);
                                    callMethod(mScrollLayout, "removeLongPressCallback");
                                    return false;
                                }
                            });
                            queueLayout.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                @Override
                                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                                    int mCurrentPosition = ((CustomTrackAdapter) parent.getAdapter()).getCurrentPosition();
                                    if (position != mCurrentPosition) {
                                        Intent intent = new Intent();
                                        intent.putExtra(SEEK_COUNT_EXTRA, position - mCurrentPosition);
                                        try {
                                            ((CustomTrackAdapter) parent.getAdapter()).reply().send(view.getContext(), 0, intent);
                                        } catch (PendingIntent.CanceledException e) {
                                            log(e);
                                        }
                                    }
                                    close.onClick(view);
                                }
                            });
                            IntentView intent = new IntentView(root.getContext());
                            intent.setChildList(queueLayout);
                            intent.setVisibility(View.GONE);
                            //noinspection ResourceType
                            intent.setId(INTENT_VIEW_ID);
                            root.addView(intent);
                            root.addView(queueLayout, root.getChildCount(), new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                            root.addView(queueButton, root.getChildCount());
                        }
                    }
                }
            });
            // Allow resolveIntent to be called as RemoteView
            findAndHookMethod("java.lang.reflect.Method", lPParam.classLoader, "isAnnotationPresent", Class.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (((Method) param.thisObject).getName().equals("resolveIntent") && ((Class) param.args[0]).getName().equals("android.view.RemotableViewMethod")) {
                        param.setResult(true);
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }
}