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
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static android.widget.RelativeLayout.TRUE;
import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

public class NotificationMod {

    public static final int TITLE_LAYOUT_BASE_ID = 0x7f0f0200;
    public static final int TEXT_BASE_ID = TITLE_LAYOUT_BASE_ID + 10;
    public static final int IMAGE_BASE_ID = TEXT_BASE_ID + 10;
    public static final int CLICK_BASE_ID = IMAGE_BASE_ID + 10;
    public static final String INTENT_ACTION = "com.android.music.musicservicecommand.queue";
    public static final String SEEK_COUNT_INTENT_EXTRA = "queue_position";

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            findAndHookMethod(GPM + ".playback.MusicPlaybackService", lPParam.classLoader, "buildLNotification", new XC_MethodHook() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = (Context) param.thisObject;
                    Notification mNotification = (Notification) param.getResult();
                    Object playback = getObjectField(param.thisObject, "mDevicePlayback");
                    int position = getIntField(playback, "mPlayPos");
                    Cursor cursor = (Cursor) callMethod(getObjectField(playback, "mMediaList"), "createSyncCursor",
                            new Class[]{Context.class, String[].class, String.class}, context, new String[]{"title", "VThumbnailUrl"}, "");

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
                        mNotification.bigContentView.setImageViewResource(IMAGE_BASE_ID + i, titles[i] != null ? context.getResources().getIdentifier("bg_default_album_art", "drawable", GPM) : android.R.color.transparent);
                        Intent queue = new Intent(INTENT_ACTION).setClass(context, context.getClass());
                        queue.putExtra(SEEK_COUNT_INTENT_EXTRA, titles[i] != null ? i - activeTitle : 0xff);
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
                            AtomicInteger seekCount = (AtomicInteger) getObjectField(devicePlayback, "mPendingMediaButtonSeekCount");
                            seekCount.addAndGet(count);
                            callMethod(devicePlayback, "handleMediaButtonSeek", new Class[]{boolean.class}, true);
                        }
                        param.setResult(Service.START_STICKY);
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }

    public static void initUI(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                findAndHookMethod("android.widget.RemoteViews", lPParam.classLoader, "performApply", View.class, ViewGroup.class, findClass("android.widget.RemoteViews.OnClickHandler", lPParam.classLoader), new XC_MethodHook() {
                    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        View v = (View) param.args[0];
                        final ViewGroup root;
                        if (v instanceof ViewGroup && (root = ((ViewGroup) v)).getChildCount() == 4
                                && root.getLayoutParams().height == root.getChildAt(0).getLayoutParams().height
                                && root.getChildAt(0) instanceof ImageView
                                && root.getChildAt(1) instanceof LinearLayout
                                && root.getChildAt(2) instanceof LinearLayout
                                && root.getChildAt(3) instanceof ImageView) {
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
                            // Text container
                            queueLayout.setOrientation(LinearLayout.VERTICAL);
                            queueLayout.setBackgroundColor(Color.WHITE);
                            RelativeLayout.LayoutParams linearLayoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                            queueLayout.setLayoutParams(linearLayoutParams);
                            queueLayout.setClickable(true);
                            queueLayout.setVisibility(View.GONE);
                            // Music items
                            for (int i = 0; i < 8; i++) {
                                // Title containerb
                                LinearLayout titleLayout = new LinearLayout(root.getContext());
                                titleLayout.setId(TITLE_LAYOUT_BASE_ID + i);
                                titleLayout.setOrientation(LinearLayout.HORIZONTAL);
                                ShapeDrawable mask = new ShapeDrawable(new RectShape());
                                mask.getPaint().setColor(Color.WHITE);
                                //noinspection deprecation
                                titleLayout.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.parseColor("#1f000000")), null, mask));
                                LinearLayout.LayoutParams textContainerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (density * 32));
                                titleLayout.setLayoutParams(textContainerParams);
                                titleLayout.setClickable(true);
                                titleLayout.setOnClickListener(closeAndMaybeSwitch);
                                // Album art
                                ImageView albumArt = new ImageView(root.getContext());
                                albumArt.setId(IMAGE_BASE_ID + i);
                                albumArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
                                LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams((int) (density * 32), (int) (density * 32));
                                albumArt.setLayoutParams(imageParams);
                                // Title text
                                TextView titleText = new TextView(root.getContext());
                                titleText.setId(TEXT_BASE_ID + i);
                                titleText.setTextColor(Color.BLACK);
                                titleText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                                titleText.setSingleLine();
                                titleText.setMaxLines(1);
                                titleText.setEllipsize(TextUtils.TruncateAt.END);
                                titleText.setGravity(Gravity.CENTER_VERTICAL);
                                titleText.setPadding((int) (density * 12), 0, (int) (density * 16), 0);
                                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (density * 32));
                                titleText.setLayoutParams(textParams);
                                // Click view
                                View clickView = new View(root.getContext());
                                clickView.setId(CLICK_BASE_ID + i);
                                clickView.setVisibility(View.GONE);
                                clickView.setClickable(true);
                                // Add views
                                titleLayout.addView(albumArt);
                                titleLayout.addView(titleText);
                                titleLayout.addView(clickView);
                                queueLayout.addView(titleLayout);
                            }
                            root.addView(queueButton, root.getChildCount() - 1);
                            root.addView(queueLayout, root.getChildCount() - 2);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            log(t);
        }
    }

    private static SpannableString getBoldString(String toBold) {
        SpannableString sp = new SpannableString(toBold);
        sp.setSpan(new StyleSpan(Typeface.BOLD), 0, sp.length(), 0);
        return sp;
    }
}