package de.Maxr1998.xposed.gpm.hooks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.MediaRouteButton;
import android.content.Intent;
import android.content.res.TypedArray;
import android.content.res.XModuleResources;
import android.content.res.XResources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ScaleDrawable;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.ColorInt;
import android.support.annotation.IdRes;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getIntField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;
import static de.robv.android.xposed.XposedHelpers.setBooleanField;

public class NowPlaying {

    private static final XModuleResources modRes = createInstance(MODULE_PATH, null);

    // Classes
    private static final String NOW_PLAYING_FRAGMENT = GPM + ".ui.NowPlayingFragment";
    private static final String PLAYBACK_CONTROLS = GPM + /*".ui.nowplaying2" + */ ".PlaybackControls";
    private static final String EXPANDING_SCROLL_VIEW = GPM + ".widgets.ExpandingScrollView";
    private static final String EXPANDING_STATE = EXPANDING_SCROLL_VIEW + ".ExpandingState";

    @IdRes
    private static final int EQ_BUTTON_ID = 0xE01;
    @ColorInt
    private static int lastColor = Color.parseColor("#9E9E9E");

    private static boolean isNewDesignEnabled() {
        return PREFS.getBoolean(Common.NP_NEW_DESIGN, false);
    }

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Enable new playback/nowplaying screen | Since there are no new features, stay on the old on for now
            /*findAndHookMethod(GPM + "Feature", lPParam.classLoader, "isPlayback2Enabled", Context.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return true;
                }
            });*/

            // Icon tinting from cover Palette
            findAndHookMethod(NOW_PLAYING_FRAGMENT, lPParam.classLoader, "setCurrentPage", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    updateTint(param.thisObject);
                }
            });

            // Tint queue button
            findAndHookMethod(NOW_PLAYING_FRAGMENT, lPParam.classLoader, "updateQueueSwitcherState", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    PREFS.reload();
                    if (PREFS.getBoolean(Common.NP_TINT_ICONS, false) && !isNewDesignEnabled()) {
                        tintQueueButton(param.thisObject);
                    }
                }
            });

            // Handle visibility of equalizer button, tint after opening
            findAndHookMethod(NOW_PLAYING_FRAGMENT, lPParam.classLoader, "onExpandingStateChanged", EXPANDING_SCROLL_VIEW, EXPANDING_STATE, EXPANDING_STATE, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    View queueSwitcher = (View) getObjectField(param.thisObject, "mQueueSwitcher");
                    ((View) queueSwitcher.getParent()).findViewById(EQ_BUTTON_ID).setVisibility(queueSwitcher.getVisibility());
                    updateTint(param.thisObject);
                }
            });

            // Buttons
            findAndHookMethod(PLAYBACK_CONTROLS, lPParam.classLoader, "refreshButtonImages", new XC_MethodHook() {
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

    public static void initResources(XC_InitPackageResources.InitPackageResourcesParam resParam) {
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
                    PREFS.reload();
                    final XResources res = lIParam.res;

                    // Views
                    final RelativeLayout nowPlayingLayout = (RelativeLayout) lIParam.view;
                    int headerPagerId = res.getIdentifier("header_pager", "id", GPM);
                    int topWrapperId = res.getIdentifier("top_wrapper_right", "id", GPM);
                    int voiceControlId = res.getIdentifier("voice_control", "id", GPM);
                    int queueSwitcherId = res.getIdentifier("queue_switcher", "id", GPM);
                    int artPagerId = res.getIdentifier("art_pager", "id", GPM);
                    int playQueueWrapperId = res.getIdentifier("play_queue_wrapper", "id", GPM);
                    int repeatId = res.getIdentifier("repeat", "id", GPM);
                    int shuffleId = res.getIdentifier("shuffle", "id", GPM);
                    int mediaRoutePickerId = res.getIdentifier("media_route_picker", "id", GPM);
                    int playControlsId = res.getIdentifier("play_controls", "id", GPM);
                    View headerPager = nowPlayingLayout.findViewById(headerPagerId);
                    RelativeLayout topWrapperRight = (RelativeLayout) nowPlayingLayout.findViewById(topWrapperId);
                    View queueSwitcher = topWrapperRight.findViewById(queueSwitcherId);
                    View artPager = nowPlayingLayout.findViewById(artPagerId);
                    RelativeLayout.LayoutParams artPagerLayoutParams = (RelativeLayout.LayoutParams) artPager.getLayoutParams();
                    LinearLayout playQueueWrapper = (LinearLayout) nowPlayingLayout.findViewById(playQueueWrapperId);
                    ImageView repeat = (ImageView) nowPlayingLayout.findViewById(repeatId),
                            shuffle = (ImageView) nowPlayingLayout.findViewById(shuffleId);
                    View progress = nowPlayingLayout.findViewById(android.R.id.progress);
                    RelativeLayout playControls = (RelativeLayout) nowPlayingLayout.findViewById(playControlsId);

                    // Add EQ button
                    if (PREFS.getBoolean(Common.NP_ADD_EQ_SHORTCUT, false)) {
                        ImageButton eqButton = new ImageButton(nowPlayingLayout.getContext());
                        eqButton.setId(EQ_BUTTON_ID);
                        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                                res.getDimensionPixelSize(res.getIdentifier("nowplaying_screen_info_block_width", "dimen", GPM)),
                                res.getDimensionPixelSize(res.getIdentifier("nowplaying_screen_info_block_height", "dimen", GPM)));
                        params.addRule(RelativeLayout.RIGHT_OF, voiceControlId);
                        eqButton.setLayoutParams(params);
                        //noinspection deprecation
                        eqButton.setImageDrawable(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? modRes.getDrawable(R.drawable.ic_equalizer_black_24dp, null) : modRes.getDrawable(R.drawable.ic_equalizer_black_24dp));
                        eqButton.setScaleType(ImageView.ScaleType.CENTER);
                        eqButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
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
                            }
                        });
                        topWrapperRight.addView(eqButton, 1);
                        RelativeLayout.LayoutParams queueParams = (RelativeLayout.LayoutParams) queueSwitcher.getLayoutParams();
                        queueParams.addRule(RelativeLayout.RIGHT_OF, EQ_BUTTON_ID);
                        eqButton.setVisibility(queueSwitcher.getVisibility());
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
                        RelativeLayout customHeaderBar = (RelativeLayout) customLayout.findViewById(customHeaderBarId);
                        LinearLayout customMainContainer = (LinearLayout) customLayout.findViewById(customMainContainerId);
                        RelativeLayout customProgressBar = (RelativeLayout) customMainContainer.findViewById(customProgressBarId);
                        RelativeLayout customTitleBar = (RelativeLayout) customMainContainer.findViewById(customTitleBarId);
                        LinearLayout customPlaybackOptionsBar = (LinearLayout) customTitleBar.findViewById(customPlaybackOptionsBarId);
                        RelativeLayout customPlayControlsBar = (RelativeLayout) customLayout.findViewById(customPlayControlsBarId);

                        customHeaderBar.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                callMethod(getStaticObjectField(findClass("android.support.v4.content.LocalBroadcastManager", v.getContext().getApplicationContext().getClassLoader()), "mInstance"), "sendBroadcast", new Intent("com.google.android.music.nowplaying.HEADER_CLICKED"));
                            }
                        });
                        customHeaderBar.addView(disconnect(topWrapperRight));

                        View mediaRoutePicker = nowPlayingLayout.findViewById(mediaRoutePickerId);
                        topWrapperRight.addView(disconnect(mediaRoutePicker), 0);
                        View voiceControl = topWrapperRight.findViewById(voiceControlId);
                        ((RelativeLayout.LayoutParams) voiceControl.getLayoutParams()).addRule(RelativeLayout.RIGHT_OF, mediaRoutePickerId);

                        LinearLayout.LayoutParams customArtPagerLayoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, 0);
                        customMainContainer.addView(disconnect(artPager), 0, customArtPagerLayoutParams);
                        artPager.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                            @Override
                            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                v.getLayoutParams().height = v.getMeasuredWidth();
                            }
                        });

                        RelativeLayout.LayoutParams playQueueWrapperLayoutParams = (RelativeLayout.LayoutParams) playQueueWrapper.getLayoutParams();
                        playQueueWrapperLayoutParams.addRule(RelativeLayout.BELOW, customHeaderBarId);
                        playQueueWrapperLayoutParams.addRule(RelativeLayout.ABOVE, customPlayControlsBarId);
                        customLayout.addView(disconnect(playQueueWrapper));

                        ((LinearLayout.LayoutParams) playQueueWrapper.findViewById(res.getIdentifier("queue_header_view", "id", GPM)).getLayoutParams()).topMargin = 0;
                        playQueueWrapper.findViewById(res.getIdentifier("play_queue", "id", GPM)).setPadding(0, 0, 0, 0);

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
                        headerPager.setBackgroundColor(0);

                        customPlaybackOptionsBar.addView(disconnect(repeat), WRAP_CONTENT, MATCH_PARENT);
                        ((ViewGroup.MarginLayoutParams) repeat.getLayoutParams()).rightMargin = (int) (16 * res.getDisplayMetrics().density);
                        customPlaybackOptionsBar.addView(disconnect(shuffle), WRAP_CONTENT, MATCH_PARENT);

                        customPlayControlsBar.addView(disconnect(playControls), MATCH_PARENT, MATCH_PARENT);
                        playControls.setBackgroundColor(0);

                        FrameLayout backup = new FrameLayout(nowPlayingLayout.getContext());
                        backup.addView(disconnect(nowPlayingLayout.findViewById(res.getIdentifier("overlay_ads_view", "id", GPM))));
                        backup.addView(disconnect(nowPlayingLayout.findViewById(res.getIdentifier("companion_ads_background", "id", GPM))));
                        backup.addView(disconnect(nowPlayingLayout.findViewById(res.getIdentifier("companion_ads_view", "id", GPM))));

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
                }
            });

            resParam.res.hookLayout(GPM, "layout", "nowplaying_header_page", new XC_LayoutInflated() {
                @Override
                public void handleLayoutInflated(LayoutInflatedParam lIParam) throws Throwable {
                    PREFS.reload();
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
                    PREFS.reload();
                    log("Hooking artpage");
                    if (PREFS.getBoolean(Common.NP_HIDE_YT_ICONS, false)) {
                        RelativeLayout root = (RelativeLayout) lIParam.view;
                        log(root.toString());
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
        ViewGroup parent = (ViewGroup) v.getParent();
        if (parent != null) {
            parent.removeView(v);
        }
        return v;
    }

    private static void updateTint(final Object nowPlayingFragment) {
        PREFS.reload();
        if (PREFS.getBoolean(Common.NP_TINT_ICONS, false)) {
            ViewGroup root = (ViewGroup) getObjectField(nowPlayingFragment, "mRootView");
            Object currentState = getObjectField(nowPlayingFragment, "mCurrentState");
            Class exStateClass = findClass(EXPANDING_STATE, nowPlayingFragment.getClass().getClassLoader());
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
                        ((Handler) getObjectField(nowPlayingFragment, "mHandler")).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                updateTint(nowPlayingFragment);
                            }
                        }, 200);
                        return;
                    }
                }
                if (isNewDesignEnabled()) {
                    // Tint header bar & its items
                    RelativeLayout customHeaderBar = (RelativeLayout) root.findViewById(modRes.getIdentifier("header_bar", "id", XGPM));
                    customHeaderBar.setBackgroundColor(lastColor);
                    RelativeLayout wrapper = (RelativeLayout) customHeaderBar.getChildAt(0);
                    double contrastBlack = ColorUtils.calculateContrast(Color.BLACK, lastColor);
                    double contrastWhite = ColorUtils.calculateContrast(Color.WHITE, lastColor);
                    int imageColor = contrastBlack > contrastWhite ? Color.BLACK : Color.WHITE;
                    for (int j = 0; j < wrapper.getChildCount(); j++) {
                        View current = wrapper.getChildAt(j);
                        if (current instanceof ImageView && current.getId() != root.getResources().getIdentifier("play_pause_header", "id", GPM)) {
                            ((ImageView) current).setColorFilter(imageColor);
                        } else if (current instanceof MediaRouteButton) {
                            ((Drawable) getObjectField(current, "mRemoteIndicator")).setColorFilter(imageColor, PorterDuff.Mode.SRC_ATOP);
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    ScaleDrawable thumb = (ScaleDrawable) seekBar.getThumb();
                    thumb.setColorFilter(lastColor, PorterDuff.Mode.SRC_IN);
                }
                ImageButton playPause = (ImageButton) root.findViewById(root.getResources().getIdentifier("pause", "id", GPM));
                playPause.getBackground().setColorFilter(lastColor, PorterDuff.Mode.SRC_ATOP);
            } else if (currentState == Enum.valueOf(exStateClass, "COLLAPSED") && isNewDesignEnabled()) {
                root.findViewById(modRes.getIdentifier("header_bar", "id", XGPM)).setBackgroundColor(Color.WHITE);
            }
        }
    }

    private static void tintQueueButton(Object nowPlayingFragment) {
        ImageButton queueSwitcher = (ImageButton) getObjectField(nowPlayingFragment, "mQueueSwitcher");
        if (getBooleanField(nowPlayingFragment, "mQueueShown")) {
            queueSwitcher.setColorFilter(lastColor);
        } else queueSwitcher.clearColorFilter();
    }
}
