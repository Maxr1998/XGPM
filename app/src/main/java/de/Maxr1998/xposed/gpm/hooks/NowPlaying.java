package de.Maxr1998.xposed.gpm.hooks;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
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
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import static de.Maxr1998.xposed.gpm.Common.GPM;
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

public class NowPlaying {

    private static final int EQ_BUTTON_ID = 0xE01;

    private static int lastColor = 0;

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Icon tinting from cover Palette
            findAndHookMethod(GPM + ".ui.NowPlayingFragment", lPParam.classLoader, "setCurrentPage", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    tintUI(param.thisObject);
                }
            });

            // Tint queue button
            findAndHookMethod(GPM + ".ui.NowPlayingFragment", lPParam.classLoader, "updateQueueSwitcherState", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    PREFS.reload();
                    if (PREFS.getBoolean(Common.NP_TINT_ICONS, false)) {
                        tintQueueButton(param.thisObject);
                    }
                }
            });

            // Handle visibility of equalizer button, tint after opening
            final String exScrollView = GPM + ".widgets.ExpandingScrollView";
            final String exState = exScrollView + ".ExpandingState";
            findAndHookMethod(GPM + ".ui.NowPlayingFragment", lPParam.classLoader, "onExpandingStateChanged", exScrollView, exState, exState, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    try {
                        if (param.args[2] == Enum.valueOf((Class) findClass(exState, lPParam.classLoader), "FULLY_EXPANDED")) {
                            tintUI(param.thisObject);
                        }
                    } finally {
                        View queueSwitcher = (View) getObjectField(param.thisObject, "mQueueSwitcher");
                        ((ViewGroup) queueSwitcher.getParent()).getChildAt(0).setVisibility(queueSwitcher.getVisibility());
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }

    public static void initResources(XC_InitPackageResources.InitPackageResourcesParam resParam) {
        try {
            final XModuleResources modRes = createInstance(MODULE_PATH, resParam.res);

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

            resParam.res.hookLayout(GPM, "layout", "nowplaying_screen", new XC_LayoutInflated() {
                @Override
                public void handleLayoutInflated(LayoutInflatedParam lIParam) throws Throwable {
                    PREFS.reload();
                    // Global vars
                    RelativeLayout header = (RelativeLayout) lIParam.view.findViewById(lIParam.res.getIdentifier("top_wrapper_right", "id", GPM));
                    View queueSwitcher = header.findViewById(lIParam.res.getIdentifier("queue_switcher", "id", GPM));

                    // Add EQ button
                    if (PREFS.getBoolean(Common.NP_ADD_EQ_SHORTCUT, false)) {
                        ImageButton eqButton = getEQButton(header.getContext(), modRes);
                        header.addView(eqButton, 0);
                        RelativeLayout.LayoutParams queueParams = (RelativeLayout.LayoutParams) queueSwitcher.getLayoutParams();
                        queueParams.addRule(RelativeLayout.RIGHT_OF, 0);
                        queueParams.addRule(RelativeLayout.RIGHT_OF, EQ_BUTTON_ID);
                        eqButton.setVisibility(queueSwitcher.getVisibility());
                    }

                    // Touch feedback
                    @SuppressLint("InlinedApi") TypedArray a = header.getContext().obtainStyledAttributes(new int[]{Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            ? android.R.attr.selectableItemBackgroundBorderless : android.R.attr.selectableItemBackground});
                    for (int i = 0; i < header.getChildCount(); i++) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                            header.getChildAt(i).setBackground(a.getDrawable(0));
                        } else {
                            //noinspection deprecation
                            header.getChildAt(i).setBackgroundDrawable(a.getDrawable(0));
                        }
                    }
                    a.recycle();

                    // Resize covers
                    if (PREFS.getBoolean(Common.NP_RESIZE_COVERS, false)) {
                        View pager = lIParam.view.findViewById(lIParam.res.getIdentifier("art_pager", "id", GPM));
                        RelativeLayout.LayoutParams pagerLayoutParams = (RelativeLayout.LayoutParams) pager.getLayoutParams();
                        pagerLayoutParams.addRule(RelativeLayout.BELOW, lIParam.res.getIdentifier("header_pager", "id", GPM));
                        pagerLayoutParams.addRule(RelativeLayout.ABOVE, lIParam.res.getIdentifier("play_controls", "id", GPM));
                        pager.setLayoutParams(pagerLayoutParams);
                    }

                    // Improve playback controls visibility
                    if (PREFS.getBoolean(Common.NP_REMOVE_DROP_SHADOW, false)) {
                        ImageView repeat = (ImageView) lIParam.view.findViewById(lIParam.res.getIdentifier("repeat", "id", GPM)),
                                shuffle = (ImageView) lIParam.view.findViewById(lIParam.res.getIdentifier("shuffle", "id", GPM));
                        if (((View) repeat.getParent()).getId() != lIParam.res.getIdentifier("play_controls", "id", GPM)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                repeat.setBackground(modRes.getDrawable(R.drawable.ripple_circle, null));
                                shuffle.setBackground(modRes.getDrawable(R.drawable.ripple_circle, null));
                            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                                //noinspection deprecation
                                repeat.setBackground(modRes.getDrawable(R.drawable.circle));
                                //noinspection deprecation
                                shuffle.setBackground(modRes.getDrawable(R.drawable.circle));
                            } else {
                                //noinspection deprecation
                                repeat.setBackgroundDrawable(modRes.getDrawable(R.drawable.circle));
                                //noinspection deprecation
                                shuffle.setBackgroundDrawable(modRes.getDrawable(R.drawable.circle));
                            }
                        }
                    }
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }

    private static void tintUI(final Object nowPlayingFragment) {
        PREFS.reload();
        if (PREFS.getBoolean(Common.NP_TINT_ICONS, false)) {
            Object artPager = getObjectField(nowPlayingFragment, "mArtPager");
            ArrayList<?> mItems = (ArrayList<?>) getObjectField(artPager, "mItems");
            for (int i = 0; i < mItems.size(); i++) {
                if (getIntField(mItems.get(i), "position") == (int) callMethod(artPager, "getCurrentItem")) {
                    Object artPageFragment = getObjectField(mItems.get(i), "object");
                    if (artPageFragment != null) {
                        ViewGroup root = (ViewGroup) getObjectField(nowPlayingFragment, "mRootView");
                        ImageView mAlbum = (ImageView) getObjectField(artPageFragment, "mAlbum");
                        if (mAlbum.getDrawable() != null) {
                            Palette coverPalette = Palette.from(((BitmapDrawable) mAlbum.getDrawable()).getBitmap()).maximumColorCount(16).generate();
                            lastColor = coverPalette.getVibrantColor(Color.parseColor("#9E9E9E"));
                            tintQueueButton(nowPlayingFragment);
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
                        } else {
                            ((Handler) getObjectField(nowPlayingFragment, "mHandler")).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    tintUI(nowPlayingFragment);
                                }
                            }, 200);
                        }
                    }
                    break;
                }
            }
        }
    }

    private static void tintQueueButton(Object nowPlayingFragment) {
        ImageButton queueSwitcher = (ImageButton) getObjectField(nowPlayingFragment, "mQueueSwitcher");
        if (getBooleanField(nowPlayingFragment, "mQueueShown")) {
            queueSwitcher.setColorFilter(lastColor);
        } else queueSwitcher.clearColorFilter();
    }

    private static ImageButton getEQButton(Context c, XModuleResources xModRes) throws Throwable {
        Resources res = c.getResources();
        ImageButton eqButton = new ImageButton(c);
        //noinspection ResourceType
        eqButton.setId(EQ_BUTTON_ID);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                res.getDimensionPixelSize(res.getIdentifier("nowplaying_screen_info_block_width", "dimen", GPM)),
                res.getDimensionPixelSize(res.getIdentifier("nowplaying_screen_info_block_height", "dimen", GPM)));
        params.addRule(RelativeLayout.RIGHT_OF, res.getIdentifier("voice_control", "id", GPM));
        eqButton.setLayoutParams(params);
        //noinspection deprecation
        eqButton.setImageDrawable(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? xModRes.getDrawable(R.drawable.ic_equalizer_black_24dp, null) : xModRes.getDrawable(R.drawable.ic_equalizer_black_24dp));
        eqButton.setScaleType(ImageView.ScaleType.CENTER);
        eqButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent eqIntent = new Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL");
                int i = (int) callStaticMethod(findClass(GPM + ".utils.MusicUtils", view.getContext().getClassLoader()), "getAudioSessionId");
                if (i != -1) {
                    eqIntent.putExtra("android.media.extra.AUDIO_SESSION", i);
                } else {
                    Log.w("MusicSettings", "Failed to get valid audio session id");
                }
                try {
                    ((Activity) view.getContext()).startActivityForResult(eqIntent, 26);
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(view.getContext(), "Couldn't find an Equalizer app. Try to install DSP Manager or similar", Toast.LENGTH_SHORT).show();
                    view.setVisibility(View.GONE);
                }
            }
        });
        return eqButton;
    }
}
