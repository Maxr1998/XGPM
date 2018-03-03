package de.Maxr1998.xposed.gpm.hooks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.content.res.XResources;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import de.Maxr1998.xposed.gpm.Common;
import de.Maxr1998.xposed.gpm.R;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LayoutInflated;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.Common.XGPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.modRes;
import static de.Maxr1998.xposed.gpm.hooks.NowPlaying.EQ_BUTTON_ID;
import static de.Maxr1998.xposed.gpm.hooks.NowPlaying.MEDIA_ROUTE_PICKER_WRAPPER_ID;
import static de.Maxr1998.xposed.gpm.hooks.NowPlaying.QUEUE_TAG_KEY;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;

@SuppressWarnings("RedundantThrows")
class NowPlayingResources {

    private static boolean isNewDesignEnabled(SharedPreferences prefs) {
        return prefs.getBoolean(Common.NP_NEW_DESIGN, false);
    }

    static void initResources(XC_InitPackageResources.InitPackageResourcesParam resParam, final SharedPreferences prefs) {
        try {
            // Replace overflow button
            resParam.res.setReplacement(GPM, "drawable", "ic_menu_moreoverflow_large", modRes.fwd(R.drawable.ic_more_vert_black_24dp));

            // Remove drop shadow from album art
            if (prefs.getBoolean(Common.NP_REMOVE_DROP_SHADOW, false)) {
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
                    log("Handling inflation of NowPlaying layout");
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
                    if (prefs.getBoolean(Common.NP_ADD_EQ_SHORTCUT, false)) {
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
                    if (isNewDesignEnabled(prefs)) {
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
                        if (prefs.getBoolean(Common.NP_RESIZE_COVERS, false)) {
                            artPagerLayoutParams.addRule(RelativeLayout.BELOW, headerPagerId);
                            artPagerLayoutParams.addRule(RelativeLayout.ABOVE, playControlsId);
                        }

                        // Improve playback controls visibility
                        if (prefs.getBoolean(Common.NP_REMOVE_DROP_SHADOW, false)) {
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
                        topWrapperRight.getChildAt(i).setBackground(a.getDrawable(0));
                    }
                    a.recycle();
                }
            });

            resParam.res.hookLayout(GPM, "layout", "nowplaying_header_page", new XC_LayoutInflated() {
                @Override
                public void handleLayoutInflated(LayoutInflatedParam lIParam) throws Throwable {
                    if (isNewDesignEnabled(prefs)) {
                        ((View) lIParam.view.findViewById(lIParam.res.getIdentifier("album_small", "id", GPM)).getParent()).setVisibility(View.GONE);
                        ((LinearLayout.LayoutParams) lIParam.view.findViewById(lIParam.res.getIdentifier("header_text", "id", GPM))
                                .getLayoutParams()).leftMargin = (int) (16 * lIParam.res.getDisplayMetrics().density);
                    } else if (prefs.getBoolean(Common.NP_HIDE_YT_ICONS, false)) {
                        View youtubeTinyIcon = lIParam.view.findViewById(lIParam.res.getIdentifier("youtube_tiny_icon", "id", GPM));
                        ((ViewGroup) youtubeTinyIcon.getParent()).removeView(youtubeTinyIcon);
                    }
                }
            });

            resParam.res.hookLayout(GPM, "layout", "nowplaying_art_page", new XC_LayoutInflated() {
                @Override
                public void handleLayoutInflated(LayoutInflatedParam lIParam) throws Throwable {
                    if (prefs.getBoolean(Common.NP_HIDE_YT_ICONS, false)) {
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
}