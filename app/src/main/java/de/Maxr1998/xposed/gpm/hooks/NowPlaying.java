package de.Maxr1998.xposed.gpm.hooks;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.AnimatedStateListDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.ColorInt;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.graphics.Palette;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import java.util.ArrayList;

import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.Common.XGPM;
import static de.Maxr1998.xposed.gpm.UtilsKt.removeFromParent;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.Maxr1998.xposed.gpm.hooks.Main.modRes;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodBestMatch;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setBooleanField;

@SuppressWarnings("RedundantThrows")
class NowPlaying {
    static final int EQ_BUTTON_ID = 0xE01;
    static final int MEDIA_ROUTE_PICKER_WRAPPER_ID = 0x2ED;
    static final int QUEUE_TAG_KEY = 0xffffffff;
    // Classes
    private static final String NOW_PLAYING_FRAGMENT = GPM + ".ui.nowplaying2.NowPlayingControllerFragment";
    private static final String PLAYBACK_CONTROLS = GPM + ".ui.nowplaying2.PlaybackControls";
    private static final String EXPANDING_SCROLL_VIEW = GPM + ".ui.common.ExpandingScrollView";
    private static final String EXPANDING_STATE = EXPANDING_SCROLL_VIEW + ".ExpandingState";
    @ColorInt
    private static int lastColor = Color.parseColor("#9E9E9E");

    private static boolean isNewDesignEnabled() {
        return PREFS.getBoolean(Common.NP_NEW_DESIGN, false);
    }

    static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            /**
             * The worst hack I've ever used, but it works..
             */
            final String resourceClass = Resources.class.getName() + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? "Impl" : "");
            findAndHookMethod(findClass(resourceClass, lPParam.classLoader), "loadXmlResourceParser",
                    String.class, int.class, int.class, String.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args[3].equals("layout") && isNewDesignEnabled()) {
                                String name = (String) param.args[0];
                                if (name.contains("/nowplaying_")) {
                                    name = name.replace("-w570dp", "").replace("-v13", "");
                                } else if (name.endsWith("/play_controls.xml")) {
                                    name = name.replace("-w570dp", "").replace("-v17", "");
                                }
                                param.args[0] = name;
                            }
                        }
                    });

            findAndHookMethod(NOW_PLAYING_FRAGMENT, lPParam.classLoader, "refreshPlaybackControls", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isNewDesignEnabled()) {
                        setBooleanField(param.thisObject, "mIsTablet", false);
                    }
                }
            });

            findAndHookMethod(NOW_PLAYING_FRAGMENT, lPParam.classLoader, "onCreateView", LayoutInflater.class, ViewGroup.class, Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    final View mQueueWrapper = ((View) getObjectField(param.thisObject, "mQueueWrapper"));
                    if (mQueueWrapper.getTag(QUEUE_TAG_KEY) != null) {
                        setBooleanField(param.thisObject, "mQueueShown", false);

                        final View mQueuePlayingFromHeaderView = (View) getObjectField(param.thisObject, "mQueuePlayingFromHeaderView");
                        mQueuePlayingFromHeaderView.setVisibility(View.GONE);

                        final int customProgressBarId = modRes.getIdentifier("progress_bar", "id", XGPM);
                        final int customPlayControlsBarId = modRes.getIdentifier("play_controls_bar", "id", XGPM);
                        final RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) mQueueWrapper.getLayoutParams();
                        ((View) getObjectField(param.thisObject, "mQueueSwitcher")).setOnClickListener(view -> {
                            boolean wasQueueShown = getBooleanField(param.thisObject, "mQueueShown");
                            setBooleanField(param.thisObject, "mQueueShown", !wasQueueShown);
                            boolean isQueueShown = !wasQueueShown;
                            mQueuePlayingFromHeaderView.setVisibility(isQueueShown ? View.VISIBLE : View.GONE);
                            mQueueWrapper.getRootView().findViewById(customProgressBarId).setVisibility(isQueueShown ? View.GONE : View.VISIBLE);
                            layoutParams.addRule(RelativeLayout.ABOVE, isQueueShown ? customPlayControlsBarId : customProgressBarId);
                            mQueueWrapper.requestLayout();
                            callMethod(getObjectField(param.thisObject, "mQueue"), "scrollToNowPlaying");
                        });
                    }
                }
            });

            findAndHookMethod(NOW_PLAYING_FRAGMENT, lPParam.classLoader, "setupPlayQueue", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object queueAdapter = getObjectField(param.thisObject, "mQueueAdapter");
                    if (queueAdapter != null)
                        callMethod(queueAdapter, "showAlbumArt", true);
                }
            });

            findAndHookMethod(NOW_PLAYING_FRAGMENT, lPParam.classLoader, "showQueue", boolean.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    View mQueueWrapper = ((View) getObjectField(param.thisObject, "mQueueWrapper"));
                    if (mQueueWrapper.getTag(QUEUE_TAG_KEY) != null)
                        param.setResult(null);
                }

                // Tint queue button
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (PREFS.getBoolean(Common.NP_TINT_ICONS, false) && !isNewDesignEnabled()) {
                        tintQueueButton(param.thisObject);
                    }
                }
            });

            // Icon tinting from cover Palette
            findAndHookMethod(NOW_PLAYING_FRAGMENT, lPParam.classLoader, "setCurrentPage", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    updateTint(param.thisObject);
                }
            });

            // Handle visibility of equalizer button, tint after opening
            findAndHookMethod(NOW_PLAYING_FRAGMENT, lPParam.classLoader, "onExpandingStateChanged", EXPANDING_SCROLL_VIEW, EXPANDING_STATE, EXPANDING_STATE, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    View queueSwitcher = (View) getObjectField(param.thisObject, "mQueueSwitcher");
                    View EQ_BUTTON_ID_TMP = ((View) queueSwitcher.getParent()).findViewById(EQ_BUTTON_ID);
                    if (EQ_BUTTON_ID_TMP != null)
                        EQ_BUTTON_ID_TMP.setVisibility(queueSwitcher.getVisibility());
                    if (isNewDesignEnabled()) {
                        ((View) getObjectField(param.thisObject, "mRootView")).findViewById(MEDIA_ROUTE_PICKER_WRAPPER_ID).setVisibility(queueSwitcher.getVisibility());
                    }
                    updateTint(param.thisObject);
                }
            });

            findAndHookMethod(NOW_PLAYING_FRAGMENT, lPParam.classLoader, "onMoving", EXPANDING_SCROLL_VIEW, EXPANDING_STATE, float.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (isNewDesignEnabled() && !param.args[1].toString().equals("HIDDEN")) {
                        System.out.println("Moving " + param.args[1].toString());
                        ViewGroup root = (ViewGroup) getObjectField(param.thisObject, "mRootView");
                        Resources res = root.getResources();
                        RelativeLayout customHeaderBar = root.findViewById(modRes.getIdentifier("header_bar", "id", XGPM));
                        if (customHeaderBar != null) {
                            float ratio = (float) param.args[2];
                            customHeaderBar.setBackgroundColor(ColorUtils.blendARGB(Color.WHITE, lastColor, ratio));

                            RelativeLayout customTitleBar = root.findViewById(modRes.getIdentifier("title_bar", "id", XGPM));
                            View headerPager = root.findViewById(res.getIdentifier("header_pager", "id", GPM));
                            if (headerPager == null)
                                headerPager = root.findViewById(res.getIdentifier("tablet_header", "id", GPM));
                            Class[] addViewInnerParams = new Class[]{View.class, int.class, ViewGroup.LayoutParams.class, boolean.class};
                            if (ratio > 0.6f) {
                                headerPager.setAlpha(1f);
                                if (headerPager.getParent() != customTitleBar) {
                                    customTitleBar.invalidate();
                                    removeFromParent(headerPager, false);
                                    findMethodBestMatch(ViewGroup.class, "addViewInner", addViewInnerParams)
                                            .invoke(customTitleBar, headerPager, -1, headerPager.getLayoutParams(), false);
                                }
                            } else {
                                if (headerPager.getParent() != customHeaderBar) {
                                    customHeaderBar.invalidate();
                                    removeFromParent(headerPager, false);
                                    findMethodBestMatch(ViewGroup.class, "addViewInner", addViewInnerParams)
                                            .invoke(customHeaderBar, headerPager, -1, headerPager.getLayoutParams(), false);
                                }
                                headerPager.setAlpha((float) Math.pow(1f - ratio, 6));
                            }
                        }
                    }
                }
            });

            findAndHookMethod(NOW_PLAYING_FRAGMENT + "$NowPlayingHeaderPageAdapter", lPParam.classLoader, "onPageScrollStateChanged", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isNewDesignEnabled()) {
                        param.setResult(null);
                    }
                }
            });

            findAndHookMethod(NOW_PLAYING_FRAGMENT + "$NowPlayingHeaderPageAdapter", lPParam.classLoader, "onPageScrolled", int.class, float.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isNewDesignEnabled()) {
                        View customPlaybackOptionsBar = ((View) getObjectField(getObjectField(param.thisObject, "this$0"), "mRootView"))
                                .findViewById(modRes.getIdentifier("playback_options_bar", "id", XGPM));
                        float offset = (float) param.args[1];
                        boolean hide = offset > 0.01f && offset < 0.99f;
                        if ((customPlaybackOptionsBar.getVisibility() == View.VISIBLE && hide) || (customPlaybackOptionsBar.getVisibility() == View.INVISIBLE && !hide)) {
                            customPlaybackOptionsBar.clearAnimation();
                            customPlaybackOptionsBar.setVisibility(hide ? View.INVISIBLE : View.VISIBLE);
                            Animation animation = new AlphaAnimation(hide ? 1f : 0f, hide ? 0f : 1f);
                            animation.setDuration(100);
                            customPlaybackOptionsBar.startAnimation(animation);
                        }
                        param.setResult(null);
                    }
                }
            });

            // Buttons
            findAndHookMethod(PLAYBACK_CONTROLS, lPParam.classLoader, "refreshButtonsState", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (isNewDesignEnabled()) {
                        setBooleanField(param.thisObject, "mIsTabletExperience", false);
                    }
                }
            });

            findAndHookMethod(PLAYBACK_CONTROLS, lPParam.classLoader, "setRepeatButtonImage", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    ImageView repeatButton = (ImageView) getObjectField(param.thisObject, "mRepeatButton");
                    if ((int) param.args[0] > 0) {
                        repeatButton.setColorFilter(lastColor);
                    } else {
                        repeatButton.clearColorFilter();
                    }
                }
            });

            findAndHookMethod(PLAYBACK_CONTROLS, lPParam.classLoader, "setShuffleButtonImage", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    ImageView shuffleButton = (ImageView) getObjectField(param.thisObject, "mShuffleButton");
                    if ((int) param.args[0] > 0) {
                        shuffleButton.setColorFilter(lastColor);
                    } else {
                        shuffleButton.clearColorFilter();
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }

    private static void updateTint(final Object nowPlayingFragment) throws Throwable {
        if (PREFS.getBoolean(Common.NP_TINT_ICONS, false)) {
            ViewGroup root = (ViewGroup) getObjectField(nowPlayingFragment, "mRootView");
            Object currentState = getObjectField(nowPlayingFragment, "mCurrentState");
            Class exStateClass = findClass(EXPANDING_STATE, nowPlayingFragment.getClass().getClassLoader());
            //noinspection unchecked
            if (currentState == Enum.valueOf(exStateClass, "FULLY_EXPANDED")) {
                Object artPager = getObjectField(nowPlayingFragment, "mArtPager");
                ArrayList<?> mItems = (ArrayList<?>) getObjectField(artPager, "mItems");
                Object artPageFragment = null;
                for (int i = 0; i < mItems.size(); i++) {
                    if (getIntField(mItems.get(i), "position") == (int) callMethod(artPager, "getCurrentItem")) {
                        artPageFragment = getObjectField(mItems.get(i), "object");
                        break;
                    }
                }
                // Update color
                if (artPageFragment != null) {
                    ImageView mAlbum = (ImageView) getObjectField(artPageFragment, "mAlbum");
                    if (mAlbum.getDrawable() != null) {
                        Palette coverPalette = Palette.from(((BitmapDrawable) mAlbum.getDrawable()).getBitmap()).maximumColorCount(16).generate();
                        lastColor = coverPalette.getVibrantColor(Color.parseColor("#9E9E9E"));
                    } else {
                        ((Handler) getObjectField(nowPlayingFragment, "mHandler")).postDelayed(() -> {
                            try {
                                updateTint(nowPlayingFragment);
                            } catch (Throwable t) {
                                log(t);
                            }
                        }, 200);
                        return;
                    }
                }
                if (isNewDesignEnabled()) {
                    // Tint header bar & its items
                    RelativeLayout customHeaderBar = root.findViewById(modRes.getIdentifier("header_bar", "id", XGPM));
                    if (customHeaderBar != null) {
                        customHeaderBar.setBackgroundColor(lastColor);
                        RelativeLayout wrapper = (RelativeLayout) customHeaderBar.getChildAt(0);
                        double contrastBlack = ColorUtils.calculateContrast(Color.BLACK, lastColor);
                        double contrastWhite = ColorUtils.calculateContrast(Color.WHITE, lastColor);
                        int imageColor = contrastBlack > contrastWhite ? Color.BLACK : Color.WHITE;
                        for (int j = 0; j < wrapper.getChildCount(); j++) {
                            View current = wrapper.getChildAt(j);
                            if (current instanceof ImageView && current.getId() != root.getResources().getIdentifier("play_pause_header", "id", GPM)) {
                                ((ImageView) current).setColorFilter(imageColor);
                            } else if (current instanceof FrameLayout && ((FrameLayout) current).getChildCount() > 0 &&
                                    ((FrameLayout) current).getChildAt(0).getClass().getSimpleName().equals("MediaRouteButton")) {
                                ((Drawable) getObjectField(((FrameLayout) current).getChildAt(0), "mRemoteIndicator")).setColorFilter(imageColor, PorterDuff.Mode.SRC_ATOP);
                            }
                        }
                    }
                } else {
                    tintQueueButton(nowPlayingFragment);
                }
                // Tint all the rest
                SeekBar seekBar = (SeekBar) getObjectField(nowPlayingFragment, "mProgress");
                LayerDrawable progress = (LayerDrawable) seekBar.getProgressDrawable().getCurrent();
                ClipDrawable clipProgress = (ClipDrawable) progress.findDrawableByLayerId(root.getResources().getIdentifier("progress", "id", "android"));
                clipProgress.setColorFilter(lastColor, PorterDuff.Mode.SRC_IN);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AnimatedStateListDrawable thumb = (AnimatedStateListDrawable) seekBar.getThumb();
                    thumb.setColorFilter(lastColor, PorterDuff.Mode.SRC_IN);
                }
                ImageButton playPause = root.findViewById(root.getResources().getIdentifier("pause", "id", GPM));
                playPause.getBackground().setColorFilter(lastColor, PorterDuff.Mode.SRC_ATOP);
            }
        }
    }

    private static void tintQueueButton(Object nowPlayingFragment) throws Throwable {
        ImageButton queueSwitcher = (ImageButton) getObjectField(nowPlayingFragment, "mQueueSwitcher");
        if (getBooleanField(nowPlayingFragment, "mQueueShown")) {
            queueSwitcher.setColorFilter(lastColor);
        } else queueSwitcher.clearColorFilter();
    }
}