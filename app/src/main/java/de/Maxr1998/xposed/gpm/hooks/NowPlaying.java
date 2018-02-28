package de.Maxr1998.xposed.gpm.hooks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.content.res.XmlResourceParser;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.AnimatedStateListDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.ColorInt;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import java.util.ArrayList;

import de.Maxr1998.xposed.gpm.Common;
import de.Maxr1998.xposed.gpm.R;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LayoutInflated;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static android.content.res.XModuleResources.createInstance;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.Common.XGPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.MODULE_PATH;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.findMethodBestMatch;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;
import static de.robv.android.xposed.XposedHelpers.setBooleanField;

@SuppressWarnings("RedundantThrows")
class NowPlaying {

    private static final XModuleResources modRes = createInstance(MODULE_PATH, null);

    // Classes
    private static final String NOW_PLAYING_FRAGMENT = GPM + ".ui.NowPlayingFragment";
    private static final String PLAYBACK_CONTROLS = GPM + ".ui.playback.PlaybackControls";
    private static final String EXPANDING_SCROLL_VIEW = GPM + ".ui.common.ExpandingScrollView";
    private static final String EXPANDING_STATE = EXPANDING_SCROLL_VIEW + ".ExpandingState";

    @IdRes
    private static final int EQ_BUTTON_ID = 0xE01;
    @IdRes
    private static final int MEDIA_ROUTE_PICKER_WRAPPER_ID = 0x2ED;
    @IdRes
    private static final int QUEUE_TAG_KEY = 0xffffffff;
    @ColorInt
    private static int lastColor = Color.parseColor("#9E9E9E");

    private static int assetCookieLayout;

    private static boolean isNewDesignEnabled() {
        return PREFS.getBoolean(Common.NP_NEW_DESIGN, false);
    }

    static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            findAndHookMethod(LayoutInflater.class, "inflate", int.class, ViewGroup.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!isNewDesignEnabled()) {
                        return;
                    }
                    LayoutInflater li = (LayoutInflater) param.thisObject;
                    Context c = (Context) getObjectField(param.thisObject, "mContext");
                    if ((int) param.args[0] == c.getResources().getIdentifier("nowplaying_screen", "layout", GPM)) {
                        log("Found layout");
                        final XmlResourceParser parser = stripLayout(c.getResources(), (int) param.args[0]);
                        //noinspection TryFinallyCanBeTryWithResources
                        try {
                            param.setResult(li.inflate(parser, (ViewGroup) param.args[1], (boolean) param.args[2]));
                        } finally {
                            parser.close();
                        }
                    }
                }
            });


            // Enable new playback/nowplaying screen | Since there are no new features, stay on the old on for now
            /*findAndHookMethod(GPM + "Feature", lPParam.classLoader, "isPlayback2Enabled", Context.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return true;
                }
            });*/

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

            findAndHookMethod(NOW_PLAYING_FRAGMENT, lPParam.classLoader, /*"setupPlayQueue"*/"access$2500", NOW_PLAYING_FRAGMENT, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    callMethod(getObjectField(param.args[0], "mQueueAdapter"), "showAlbumArt", true);
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
            findAndHookMethod(NOW_PLAYING_FRAGMENT, lPParam.classLoader, "setCurrentPage", new XC_MethodHook() {
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
                                    findMethodBestMatch(ViewGroup.class, "addViewInner", addViewInnerParams)
                                            .invoke(customTitleBar, disconnect(headerPager, false), -1, headerPager.getLayoutParams(), false);
                                }
                            } else {
                                if (headerPager.getParent() != customHeaderBar) {
                                    customHeaderBar.invalidate();
                                    findMethodBestMatch(ViewGroup.class, "addViewInner", addViewInnerParams)
                                            .invoke(customHeaderBar, disconnect(headerPager, false), -1, headerPager.getLayoutParams(), false);
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
                        setBooleanField(param.thisObject, "mIsTabletExperience", true);
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

    /**
     * The worst hack I've ever used, but it works :P
     */
    private static XmlResourceParser stripLayout(Resources res, @LayoutRes int id) {
        try {
            TypedValue value = new TypedValue();
            res.getValue(id, value, true);
            String result = value.string.toString();
            if (!result.contains("w570dp")) {
                assetCookieLayout = value.assetCookie;
                return res.getLayout(id);
            }
            result = result.replace("-w570dp", "").replace("-v13", "");
            return (XmlResourceParser) callMethod(res, "loadXmlResourceParser", result, id, assetCookieLayout != 0 ? assetCookieLayout : Integer.MIN_VALUE, "layout");
        } catch (Throwable t) {
            log(t);
            return res.getLayout(id);
        }
    }

    static void initResources(XC_InitPackageResources.InitPackageResourcesParam resParam) {
        try {
            // Replace overflow button
            resParam.res.setReplacement(GPM, "drawable", "ic_menu_moreoverflow_large", modRes.fwd(R.drawable.ic_more_vert_black_24dp));

            // Remove drop shadow from album art
            if (PREFS.getBoolean(Common.NP_REMOVE_DROP_SHADOW, false)) {
                resParam.res.setReplacement(GPM, "drawable", "now_playing_art_scrim", new XResources.DrawableLoader() {
                    @Override
                    public Drawable newDrawable(XResources xResources, int i) throws Throwable {
                        return new ColorDrawable(0);
                    }
                });
                resParam.res.setReplacement(GPM, "dimen", "rating_controls_now_playing_page", modRes.fwd(R.dimen.controls_height));
            }

            // Layout modifications
            resParam.res.hookLayout(GPM, "layout", "nowplaying_screen", new XC_LayoutInflated() {
                @Override
                public void handleLayoutInflated(final LayoutInflatedParam lIParam) throws Throwable {
                    final XResources res = lIParam.res;

                    // Views
                    final RelativeLayout nowPlayingLayout = (RelativeLayout) lIParam.view;
                    int headerPagerId = res.getIdentifier("header_pager", "id", GPM);
                    int tabletHeaderId = res.getIdentifier("tablet_header", "id", GPM);
                    int tabletPlayControlsId = res.getIdentifier("tablet_collapsed_play_controls", "id", GPM);
                    int topWrapperId = res.getIdentifier("top_wrapper_right", "id", GPM);
                    int queueSwitcherId = res.getIdentifier("queue_switcher", "id", GPM);
                    int artPagerId = res.getIdentifier("art_pager", "id", GPM);
                    int playQueueWrapperId = res.getIdentifier("play_queue_wrapper", "id", GPM);
                    int repeatId = res.getIdentifier("repeat", "id", GPM);
                    int shuffleId = res.getIdentifier("shuffle", "id", GPM);
                    int mediaRoutePickerId = res.getIdentifier("media_route_picker", "id", GPM);
                    int playControlsId = res.getIdentifier("play_controls", "id", GPM);
                    View headerPager = nowPlayingLayout.findViewById(headerPagerId);
                    if (headerPager == null)
                        headerPager = nowPlayingLayout.findViewById(tabletHeaderId);
                    View tabletPlaybackControlsWrapper = nowPlayingLayout.findViewById(tabletPlayControlsId);
                    RelativeLayout topWrapperRight = nowPlayingLayout.findViewById(topWrapperId);
                    View queueSwitcher = topWrapperRight.findViewById(queueSwitcherId);
                    View artPager = nowPlayingLayout.findViewById(artPagerId);
                    RelativeLayout.LayoutParams artPagerLayoutParams = (RelativeLayout.LayoutParams) artPager.getLayoutParams();
                    LinearLayout playQueueWrapper = nowPlayingLayout.findViewById(playQueueWrapperId);
                    ImageView repeat = nowPlayingLayout.findViewById(repeatId),
                            shuffle = nowPlayingLayout.findViewById(shuffleId);
                    View progress = nowPlayingLayout.findViewById(android.R.id.progress);
                    RelativeLayout playControls = nowPlayingLayout.findViewById(playControlsId);

                    // Add EQ button
                    if (PREFS.getBoolean(Common.NP_ADD_EQ_SHORTCUT, false)) {
                        ImageButton eqButton = new ImageButton(nowPlayingLayout.getContext());
                        eqButton.setId(EQ_BUTTON_ID);
                        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                                res.getDimensionPixelSize(res.getIdentifier("nowplaying_screen_info_block_width", "dimen", GPM)),
                                res.getDimensionPixelSize(res.getIdentifier("nowplaying_screen_info_block_height", "dimen", GPM)));
                        params.addRule(RelativeLayout.RIGHT_OF, /*voiceControlId*/MEDIA_ROUTE_PICKER_WRAPPER_ID);
                        eqButton.setLayoutParams(params);
                        //noinspection deprecation
                        eqButton.setImageDrawable(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? modRes.getDrawable(R.drawable.ic_equalizer_black_24dp, null) : modRes.getDrawable(R.drawable.ic_equalizer_black_24dp));
                        eqButton.setScaleType(ImageView.ScaleType.CENTER);
                        eqButton.setOnClickListener(view -> {
                            Intent eqIntent = new Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL");
                            int i = (int) callStaticMethod(findClass(GPM + ".utils.MusicUtils", view.getContext().getClassLoader()), "getAudioSessionId", view.getContext());
                            if (i != -1) {
                                eqIntent.putExtra("android.media.extra.AUDIO_SESSION", i);
                            } else {
                                Log.w("MusicSettings", "Failed to get valid audio session id");
                            }
                            try {
                                ((Activity) view.getContext()).startActivityForResult(eqIntent, 26);
                            } catch (Exception e) {
                                e.printStackTrace();
                                Toast.makeText(view.getContext(), "Couldn't find an Equalizer app. Try to install Viper4Android, DSP Manager or similar", Toast.LENGTH_SHORT).show();
                                view.setVisibility(View.GONE);
                            }
                        });
                        topWrapperRight.addView(eqButton, 1);
                        RelativeLayout.LayoutParams queueParams = (RelativeLayout.LayoutParams) queueSwitcher.getLayoutParams();
                        queueParams.addRule(RelativeLayout.RIGHT_OF, EQ_BUTTON_ID);
                        eqButton.setVisibility(queueSwitcher.getVisibility());
                    }

                    // New design
                    if (isNewDesignEnabled()) {
                        final RelativeLayout customLayout = (RelativeLayout) LayoutInflater.from(nowPlayingLayout.getContext())
                                .inflate(modRes.getLayout(modRes.getIdentifier("now_playing_layout", "layout", XGPM)), null);

                        // Views
                        int customHeaderBarId = modRes.getIdentifier("header_bar", "id", XGPM);
                        int customMainContainerId = modRes.getIdentifier("main_container", "id", XGPM);
                        int customProgressBarId = modRes.getIdentifier("progress_bar", "id", XGPM);
                        int customTitleBarId = modRes.getIdentifier("title_bar", "id", XGPM);
                        int customPlaybackOptionsBarId = modRes.getIdentifier("playback_options_bar", "id", XGPM);
                        int customPlayControlsBarId = modRes.getIdentifier("play_controls_bar", "id", XGPM);
                        RelativeLayout customHeaderBar = customLayout.findViewById(customHeaderBarId);
                        LinearLayout customMainContainer = customLayout.findViewById(customMainContainerId);
                        RelativeLayout customProgressBar = customLayout.findViewById(customProgressBarId);
                        RelativeLayout customTitleBar = customLayout.findViewById(customTitleBarId);
                        LinearLayout customPlaybackOptionsBar = customTitleBar.findViewById(customPlaybackOptionsBarId);
                        RelativeLayout customPlayControlsBar = customLayout.findViewById(customPlayControlsBarId);

                        customHeaderBar.setOnClickListener(v -> callMethod(getStaticObjectField(findClass("android.support.v4.content.LocalBroadcastManager", v.getContext().getApplicationContext().getClassLoader()), "mInstance"), "sendBroadcast", new Intent("com.google.android.music.nowplaying.HEADER_CLICKED")));
                        customHeaderBar.addView(disconnect(topWrapperRight));

                        if (tabletPlaybackControlsWrapper != null) {
                            customHeaderBar.addView(disconnect(tabletPlaybackControlsWrapper));
                        }

                        ViewGroup mediaRoutePickerWrapper = new FrameLayout(nowPlayingLayout.getContext());
                        mediaRoutePickerWrapper.setId(MEDIA_ROUTE_PICKER_WRAPPER_ID);
                        topWrapperRight.addView(mediaRoutePickerWrapper, 0, new RelativeLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
                        View mediaRoutePicker = nowPlayingLayout.findViewById(mediaRoutePickerId);
                        if (mediaRoutePicker != null) {
                            mediaRoutePickerWrapper.addView(disconnect(mediaRoutePicker));
                            mediaRoutePicker.setMinimumWidth(0);
                            mediaRoutePicker.setMinimumHeight(0);
                            FrameLayout.LayoutParams mediaRoutePickerParams = (FrameLayout.LayoutParams) mediaRoutePicker.getLayoutParams();
                            mediaRoutePickerParams.topMargin = 0;
                            mediaRoutePickerParams.bottomMargin = 0;
                            mediaRoutePickerParams.gravity = Gravity.CENTER;
                        }

                        final boolean portrait = customMainContainer.indexOfChild(customTitleBar) >= 0;

                        LinearLayout.LayoutParams customArtPagerLayoutParams = portrait ? new LinearLayout.LayoutParams(MATCH_PARENT, 0) : new LinearLayout.LayoutParams(0, MATCH_PARENT);
                        customMainContainer.addView(disconnect(artPager), 0, customArtPagerLayoutParams);
                        artPager.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                            if (portrait)
                                v.getLayoutParams().height = v.getMeasuredWidth();
                            else v.getLayoutParams().width = v.getMeasuredHeight();
                        });

                        RelativeLayout.LayoutParams playQueueWrapperLayoutParams = (RelativeLayout.LayoutParams) playQueueWrapper.getLayoutParams();
                        playQueueWrapperLayoutParams.addRule(RelativeLayout.BELOW, customHeaderBarId);
                        playQueueWrapperLayoutParams.addRule(RelativeLayout.ABOVE, portrait ? customPlayControlsBarId : customProgressBarId);
                        (portrait ? customLayout : (RelativeLayout) customLayout.getChildAt(2)).addView(disconnect(playQueueWrapper), portrait ? -1 : 2);

                        ((LinearLayout.LayoutParams) playQueueWrapper.findViewById(res.getIdentifier("queue_header_view", "id", GPM)).getLayoutParams()).topMargin = 0;
                        playQueueWrapper.findViewById(res.getIdentifier("play_queue", "id", GPM)).setPadding(0, 0, 0, 0);

                        if (!portrait)
                            playQueueWrapper.setTag(QUEUE_TAG_KEY, new Object());

                        RelativeLayout.LayoutParams progressLayoutParams = (RelativeLayout.LayoutParams) progress.getLayoutParams();
                        progressLayoutParams.addRule(RelativeLayout.ABOVE, 0);
                        progressLayoutParams.bottomMargin = (int) (-16 * res.getDisplayMetrics().density);
                        customProgressBar.addView(disconnect(progress));

                        View currentTime = disconnect(nowPlayingLayout.findViewById(res.getIdentifier("currenttime", "id", GPM)));
                        RelativeLayout.LayoutParams currentTimeLayoutParams = (RelativeLayout.LayoutParams) currentTime.getLayoutParams();
                        currentTimeLayoutParams.bottomMargin = 0;
                        customProgressBar.addView(currentTime);

                        View totalTime = disconnect(nowPlayingLayout.findViewById(res.getIdentifier("totaltime", "id", GPM)));
                        RelativeLayout.LayoutParams totalTimeLayoutParams = (RelativeLayout.LayoutParams) totalTime.getLayoutParams();
                        totalTimeLayoutParams.bottomMargin = 0;
                        customProgressBar.addView(totalTime);

                        customTitleBar.addView(disconnect(headerPager), 0);
                        headerPager.getLayoutParams().height = customHeaderBar.getLayoutParams().height; // = 64dp
                        headerPager.setBackgroundColor(Color.TRANSPARENT);

                        customPlaybackOptionsBar.addView(disconnect(repeat), WRAP_CONTENT, MATCH_PARENT);
                        ((ViewGroup.MarginLayoutParams) repeat.getLayoutParams()).rightMargin = (int) (16 * res.getDisplayMetrics().density);
                        customPlaybackOptionsBar.addView(disconnect(shuffle), WRAP_CONTENT, MATCH_PARENT);

                        customPlayControlsBar.addView(disconnect(playControls), MATCH_PARENT, MATCH_PARENT);
                        playControls.setBackgroundResource(0);
                        playControls.findViewById(res.getIdentifier("ratingThumbs", "id", GPM)).setBackgroundResource(0);

                        FrameLayout backup = new FrameLayout(nowPlayingLayout.getContext());
                        backup.addView(disconnect(nowPlayingLayout.findViewById(res.getIdentifier("overlay_ads_view", "id", GPM))));
                        backup.addView(disconnect(nowPlayingLayout.findViewById(res.getIdentifier("companion_ads_background", "id", GPM))));
                        backup.addView(disconnect(nowPlayingLayout.findViewById(res.getIdentifier("companion_ads_view", "id", GPM))));
                        View tabletHeaderAdsWrapper = nowPlayingLayout.findViewById(res.getIdentifier("tablet_collapsed_ad_view", "id", GPM));
                        if (tabletHeaderAdsWrapper != null)
                            backup.addView(disconnect(tabletHeaderAdsWrapper));
                        View tabletAdsHeader = nowPlayingLayout.findViewById(res.getIdentifier("tablet_ads_header", "id", GPM));
                        if (tabletAdsHeader != null)
                            backup.addView(disconnect(tabletAdsHeader));
                        backup.addView(disconnect(nowPlayingLayout.findViewById(res.getIdentifier("upsell_now_playing", "id", GPM))));

                        nowPlayingLayout.removeAllViews();
                        nowPlayingLayout.addView(backup, 0, 0);
                        nowPlayingLayout.addView(customLayout);
                    } else {
                        // Resize covers
                        if (PREFS.getBoolean(Common.NP_RESIZE_COVERS, false)) {
                            artPagerLayoutParams.addRule(RelativeLayout.BELOW, headerPagerId);
                            artPagerLayoutParams.addRule(RelativeLayout.ABOVE, playControlsId);
                        }

                        // Improve playback controls visibility
                        if (PREFS.getBoolean(Common.NP_REMOVE_DROP_SHADOW, false)) {
                            if (((View) repeat.getParent()).getId() != playControlsId) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                    repeat.setBackground(modRes.getDrawable(R.drawable.ripple_circle, null));
                                    shuffle.setBackground(modRes.getDrawable(R.drawable.ripple_circle, null));
                                }
                            }
                        }
                    }

                    // Touch feedback
                    @SuppressLint("InlinedApi") TypedArray a = nowPlayingLayout.getContext().obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackgroundBorderless});
                    for (int i = 0; i < topWrapperRight.getChildCount(); i++) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                            topWrapperRight.getChildAt(i).setBackground(a.getDrawable(0));
                        } else {
                            //noinspection deprecation
                            topWrapperRight.getChildAt(i).setBackgroundDrawable(a.getDrawable(0));
                        }
                    }
                    a.recycle();
                }
            });

            resParam.res.hookLayout(GPM, "layout", "nowplaying_header_page", new XC_LayoutInflated() {
                @Override
                public void handleLayoutInflated(LayoutInflatedParam lIParam) throws Throwable {
                    if (isNewDesignEnabled()) {
                        ((View) lIParam.view.findViewById(lIParam.res.getIdentifier("album_small", "id", GPM)).getParent()).setVisibility(View.GONE);
                        ((LinearLayout.LayoutParams) lIParam.view.findViewById(lIParam.res.getIdentifier("header_text", "id", GPM))
                                .getLayoutParams()).leftMargin = (int) (16 * lIParam.res.getDisplayMetrics().density);
                    } else if (PREFS.getBoolean(Common.NP_HIDE_YT_ICONS, false)) {
                        View youtubeTinyIcon = lIParam.view.findViewById(lIParam.res.getIdentifier("youtube_tiny_icon", "id", GPM));
                        ((ViewGroup) youtubeTinyIcon.getParent()).removeView(youtubeTinyIcon);
                    }
                }
            });

            resParam.res.hookLayout(GPM, "layout", "nowplaying_art_page", new XC_LayoutInflated() {
                @Override
                public void handleLayoutInflated(LayoutInflatedParam lIParam) throws Throwable {
                    if (PREFS.getBoolean(Common.NP_HIDE_YT_ICONS, false)) {
                        RelativeLayout root = (RelativeLayout) lIParam.view;
                        FrameLayout backup = new FrameLayout(root.getContext());
                        backup.addView(disconnect(root.findViewById(root.getResources().getIdentifier("youtube_overlay", "id", GPM))));
                        backup.addView(disconnect(root.findViewById(root.getResources().getIdentifier("youtube_play_red", "id", GPM))));
                        backup.addView(disconnect(root.findViewById(root.getResources().getIdentifier("youtube_widget_text", "id", GPM))));
                        backup.addView(disconnect(root.findViewById(root.getResources().getIdentifier("youtube_widget_white", "id", GPM))));
                        backup.setVisibility(View.GONE);
                        root.addView(backup, 0);
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }

    private static <V extends View> V disconnect(V v) {
        return disconnect(v, true);
    }

    private static <V extends View> V disconnect(V v, boolean relayout) {
        ViewGroup parent = (ViewGroup) v.getParent();
        if (parent != null) {
            if (relayout) parent.removeView(v);
            else parent.removeViewInLayout(v);
        }
        return v;
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
                for (Object pager : new Object[]{getObjectField(nowPlayingFragment, "mHeaderPager"), artPager}) {
                    if (pager == null)
                        continue;
                    for (Object edgeEffectCompat : new Object[]{getObjectField(pager, "mLeftEdge"), getObjectField(pager, "mRightEdge")}) {
                        ((Paint) getObjectField(getObjectField(edgeEffectCompat, "mEdgeEffect"), "mPaint")).setColor(lastColor);
                    }
                }
                SeekBar seekBar = (SeekBar) getObjectField(nowPlayingFragment, "mProgress");
                LayerDrawable progress = (LayerDrawable) seekBar.getProgressDrawable().getCurrent();
                ClipDrawable clipProgress = (ClipDrawable) progress.findDrawableByLayerId(root.getResources().getIdentifier("progress", "id", "android"));
                clipProgress.setColorFilter(lastColor, PorterDuff.Mode.SRC_IN);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
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