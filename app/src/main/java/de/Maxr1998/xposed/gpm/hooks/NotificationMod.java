package de.Maxr1998.xposed.gpm.hooks;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import de.Maxr1998.xposed.gpm.Common;
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
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class NotificationMod {

    public static final int TITLE_LAYOUT_BASE_ID = 0x7f0f0200;
    public static final int TEXT_BASE_ID = TITLE_LAYOUT_BASE_ID + 10;
    public static final int IMAGE_BASE_ID = TEXT_BASE_ID + 10;
    public static final int CLICK_BASE_ID = IMAGE_BASE_ID + 10;
    public static final String INTENT_ACTION = "com.android.music.musicservicecommand.queue";
    public static final String SEEK_COUNT_INTENT_EXTRA = "queue_position";
    private static Runnable META_DATA_RELOADER;
    private static Object ART_LOADER_COMPLETION_LISTENER;
    private static int CURRENT_TRACK_NR;
    private static String[] TITLES = new String[8];
    private static Object[] ART_REQUESTS = new Object[8];
    private static Bitmap[] ART_BITMAPS = new Bitmap[8];

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Track selection
            findAndHookMethod(GPM + ".playback.MusicPlaybackService", lPParam.classLoader, "buildLNotification", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = (Context) param.thisObject;
                    Notification mNotification = (Notification) param.getResult();
                    for (int i = 0; i < 8; i++) {
                        mNotification.bigContentView.setTextViewText(TEXT_BASE_ID + i, TITLES[i] != null ? i == CURRENT_TRACK_NR ? getBoldString(TITLES[i]) : TITLES[i] : "");
                        if (TITLES[i] != null && ART_BITMAPS[i] != null) {
                            mNotification.bigContentView.setImageViewBitmap(IMAGE_BASE_ID + i, ART_BITMAPS[i]);
                        } else {
                            mNotification.bigContentView.setImageViewResource(IMAGE_BASE_ID + i, TITLES[i] != null ? context.getResources().getIdentifier("bg_default_album_art", "drawable", GPM) : android.R.color.transparent);
                        }
                        Intent queue = new Intent(INTENT_ACTION).setClass(context, context.getClass());
                        queue.putExtra(SEEK_COUNT_INTENT_EXTRA, TITLES[i] != null ? i - CURRENT_TRACK_NR : 0xff);
                        mNotification.bigContentView.setOnClickPendingIntent(CLICK_BASE_ID + i, PendingIntent.getService(context, (int) System.currentTimeMillis() + i, queue, PendingIntent.FLAG_UPDATE_CURRENT));
                    }
                }
            });
            findAndHookMethod(GPM + ".playback.MusicPlaybackService", lPParam.classLoader, "onStartCommand", Intent.class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Intent intent = (Intent) param.args[0];
                    if (intent != null && intent.getAction() != null && intent.getAction().equals(INTENT_ACTION) && intent.hasExtra(SEEK_COUNT_INTENT_EXTRA)) {
                        int count = intent.getIntExtra(SEEK_COUNT_INTENT_EXTRA, 0);
                        if (count != 0xff) {
                            Object devicePlayback = getObjectField(param.thisObject, "mDevicePlayback");
                            callMethod(devicePlayback, "seek", 0L);
                            AtomicInteger seekCount = (AtomicInteger) getObjectField(devicePlayback, "mPendingMediaButtonSeekCount");
                            seekCount.addAndGet(count);
                            callMethod(devicePlayback, "handleMediaButtonSeek", new Class[]{boolean.class, int.class}, true, 4);
                        }
                        param.setResult(Service.START_STICKY);
                    }
                }
            });

            findAndHookConstructor(GPM + ".playback.MusicPlaybackService", lPParam.classLoader, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    META_DATA_RELOADER = new Runnable() {
                        @SuppressWarnings("unchecked")
                        @Override
                        public void run() {
                            try {
                                Object mDevicePlayback = getObjectField(param.thisObject, "mDevicePlayback");
                                int position = getIntField(mDevicePlayback, "mPlayPos");
                                Object mSongList = callMethod(mDevicePlayback, "getMediaList");
                                Cursor cursor = (Cursor) callMethod(mSongList, "createSyncCursor",
                                        new Class[]{Context.class, String[].class, String.class}, param.thisObject, new String[]{"title", "Nid", "album_id"}, "");

                                final Object mArtTypeNotification = getStaticObjectField(findClass(GPM + ".art.ArtType", lPParam.classLoader), "NOTIFICATION");
                                final Constructor mArtDescriptorConstructor = findClass(GPM + ".art.DocumentArtDescriptor", lPParam.classLoader)
                                        .getConstructor(mArtTypeNotification.getClass(), int.class,
                                                float.class, findClass(GPM + ".ui.cardlib.model.Document", lPParam.classLoader));
                                final String url;
                                if (findClass(GPM + ".medialist.ExternalSongList", lPParam.classLoader).isInstance(mSongList)) {
                                    url = callMethod(mSongList, "getAlbumArtUrl", param.thisObject).toString();
                                } else {
                                    url = null;
                                }

                                // Detect positions
                                CURRENT_TRACK_NR = 2;
                                int start = position - 2;
                                if (position < 3 || cursor.getCount() < 8) {
                                    CURRENT_TRACK_NR = position;
                                    start = 0;
                                } else if (cursor.getCount() - position < 6) {
                                    CURRENT_TRACK_NR = 8 - (cursor.getCount() - position);
                                    start = cursor.getCount() - 8;
                                }

                                // Loading data
                                cursor.moveToPosition(start);
                                for (int i = 0; i < 8; i++) {
                                    TITLES[i] = cursor.getPosition() < cursor.getCount() ? cursor.getString(0) : null;
                                    if (ART_REQUESTS[i] != null) {
                                        callMethod(ART_REQUESTS[i], "cancelRequest");
                                        callMethod(ART_REQUESTS[i], "release");
                                        ART_REQUESTS[i] = null;
                                    }
                                    String mMetajamId = cursor.getPosition() < cursor.getCount() ? cursor.getString(1) : null;
                                    long mAlbumId = cursor.getPosition() < cursor.getCount() ? cursor.getLong(2) : 0;
                                    if (mMetajamId != null && mAlbumId != 0) {
                                        log("RENDERER: Starting " + i);
                                        Object mDocument = callStaticMethod(findClass(GPM + ".utils.NowPlayingUtils", lPParam.classLoader), "createNowPlayingArtDocument", mMetajamId, mAlbumId, url);
                                        Object mDescriptor = mArtDescriptorConstructor.newInstance(mArtTypeNotification, getObjectField(param.thisObject, "mArtSizePixels"), 1.0f, mDocument);
                                        ART_REQUESTS[i] = callMethod(callStaticMethod(findClass(GPM + ".art.ArtResolver", lPParam.classLoader), "getInstance", param.thisObject), "getArt", mDescriptor, i == 7 ? ART_LOADER_COMPLETION_LISTENER : null);
                                        callMethod(ART_REQUESTS[i], "retain");
                                    }
                                    cursor.moveToNext();
                                }
                            } catch (Throwable t) {
                                log(t);
                            }
                        }
                    };
                    ART_LOADER_COMPLETION_LISTENER = Proxy.newProxyInstance(lPParam.classLoader, new Class[]{
                            findClass(GPM + ".art.ArtResolver.RequestListener", lPParam.classLoader)}, new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                            for (int i = 0; i < ART_REQUESTS.length; i++) {
                                if (ART_REQUESTS[i] != null && (boolean) callMethod(ART_REQUESTS[i], "didRenderSuccessfully")) {
                                    log("RENDERER: Bitmap " + i + " rendered successfully");
                                    ART_BITMAPS[i] = (Bitmap) callMethod(ART_REQUESTS[i], "getResultBitmap");
                                } else {
                                    ART_BITMAPS[i] = null;
                                }
                            }
                            return null;
                        }
                    });
                }
            });

            findAndHookMethod(GPM + ".playback.MusicPlaybackService", lPParam.classLoader, "updateNotificationAndMediaSessionMetadataAsync", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Class async = findClass(GPM + ".utils.async.AsyncWorkers", lPParam.classLoader);
                    Object sBackendServiceWorker = getStaticObjectField(async, "sBackendServiceWorker");
                    callStaticMethod(async, "runAsync", sBackendServiceWorker, META_DATA_RELOADER);
                }
            });

            // Switch to old design
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
            findAndHookMethod("android.app.Notification.Builder", lPParam.classLoader, "build", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    Notification.Builder builder = (Notification.Builder) param.thisObject;
                    if (builder.getExtras().getInt("xgpm") == 1) {
                        // Prevent force close
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
            findAndHookMethod("android.widget.RemoteViews", lPParam.classLoader, "performApply", View.class, ViewGroup.class, findClass("android.widget.RemoteViews.OnClickHandler", lPParam.classLoader), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
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
                            final LinearLayout queueLayout = new LinearLayout(root.getContext());
                            final View.OnClickListener closeAndMaybeSwitch = new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    // Switch title if a text item was clicked
                                    if (v instanceof LinearLayout) {
                                        root.findViewById(v.getId() + (CLICK_BASE_ID - TITLE_LAYOUT_BASE_ID)).performClick();
                                    }
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
                                        closeAndMaybeSwitch.onClick(v);
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
                                            queueButton.setColorFilter(Color.BLACK);
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
                            titleContainerParams.setMarginEnd((int) (density * 48));
                            // Text container
                            queueLayout.setOrientation(LinearLayout.VERTICAL);
                            queueLayout.setBackgroundColor(Color.WHITE);
                            queueLayout.setClickable(true);
                            queueLayout.setVisibility(View.GONE);
                            // Music items
                            for (int i = 0; i < 8; i++) {
                                // Titles container
                                LinearLayout titlesLayout = new LinearLayout(root.getContext());
                                titlesLayout.setId(TITLE_LAYOUT_BASE_ID + i);
                                titlesLayout.setOrientation(LinearLayout.HORIZONTAL);
                                ShapeDrawable mask = new ShapeDrawable(new RectShape());
                                mask.getPaint().setColor(Color.WHITE);
                                titlesLayout.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#1f000000")), null, mask));
                                LinearLayout.LayoutParams textContainerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (density * 32));
                                titlesLayout.setLayoutParams(textContainerParams);
                                titlesLayout.setClickable(true);
                                titlesLayout.setOnClickListener(closeAndMaybeSwitch);
                                // Album art
                                ImageView albumArt = new ImageView(root.getContext());
                                albumArt.setId(IMAGE_BASE_ID + i);
                                albumArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams((int) (density * 32), (int) (density * 32));
                                // Title text
                                TextView titleText = new TextView(root.getContext());
                                titleText.setId(TEXT_BASE_ID + i);
                                titleText.setTextColor(Color.BLACK);
                                titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
                                titleText.setSingleLine();
                                titleText.setMaxLines(1);
                                titleText.setEllipsize(TextUtils.TruncateAt.END);
                                titleText.setGravity(Gravity.CENTER_VERTICAL);
                                titleText.setPadding((int) (density * 12), 0, (int) (density * 16), 0);
                                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (density * 32));
                                // Click view
                                View clickView = new View(root.getContext());
                                clickView.setId(CLICK_BASE_ID + i);
                                clickView.setVisibility(View.GONE);
                                clickView.setClickable(true);
                                // Add views
                                titlesLayout.addView(albumArt, imageParams);
                                titlesLayout.addView(titleText, textParams);
                                titlesLayout.addView(clickView);
                                queueLayout.addView(titlesLayout);
                            }
                            root.addView(queueLayout, root.getChildCount(),
                                    new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                            root.addView(queueButton, root.getChildCount());
                        }
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }

    public static SpannableString getBoldString(String toBold) {
        SpannableString sp = new SpannableString(toBold);
        sp.setSpan(new StyleSpan(Typeface.BOLD), 0, sp.length(), 0);
        return sp;
    }
}