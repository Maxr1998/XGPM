package de.Maxr1998.xposed.gpm.hooks;

import android.app.Activity;
import android.content.Intent;
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
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import de.Maxr1998.xposed.gpm.Common;
import de.Maxr1998.xposed.gpm.R;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LayoutInflated;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.Common.MODULE_PATH;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class NowPlaying implements IXposedHookInitPackageResources, IXposedHookLoadPackage, Palette.PaletteAsyncListener {

    private XC_LayoutInflated.LayoutInflatedParam exLIPar;
    private int lastColor = 0;
    private Object nowPlayingScreenFragment;
    private ImageButton eQButton;

    @Override
    public void handleInitPackageResources(final XC_InitPackageResources.InitPackageResourcesParam resParam) throws Throwable {
        if (!resParam.packageName.equals(GPM))
            return;

        // Replace overflow button
        resParam.res.setReplacement(GPM, "drawable", "ic_menu_moreoverflow_large", XModuleResources.createInstance(MODULE_PATH, resParam.res).fwd(R.drawable.ic_more_vert_black_24dp));

        // Remove drop shadow from album art
        if (PREFS.getBoolean(Common.NP_REMOVE_DROP_SHADOW, false)) {
            resParam.res.setReplacement(GPM, "drawable", "now_playing_art_scrim", new XResources.DrawableLoader() {
                @Override
                public Drawable newDrawable(XResources xResources, int i) throws Throwable {
                    return new ColorDrawable(0);
                }
            });
        }

        resParam.res.hookLayout(GPM, "layout", "nowplaying_screen", new XC_LayoutInflated() {
            @Override
            public void handleLayoutInflated(LayoutInflatedParam lIParam) throws Throwable {
                PREFS.reload();
                exLIPar = lIParam;
                // Add EQ button
                if (PREFS.getBoolean(Common.NP_ADD_EQ_SHORTCUT, false)) {
                    RelativeLayout header = (RelativeLayout) lIParam.view.findViewById(lIParam.res.getIdentifier("top_wrapper_right", "id", GPM));
                    header.addView(getEQButton(header, resParam.res), header.getChildCount() - 1);
                    RelativeLayout.LayoutParams overflow = new RelativeLayout.LayoutParams(
                            exLIPar.res.getDimensionPixelSize(lIParam.res.getIdentifier("nowplaying_screen_info_block_width", "dimen", GPM)),
                            exLIPar.res.getDimensionPixelSize(lIParam.res.getIdentifier("nowplaying_screen_info_block_height", "dimen", GPM)));
                    overflow.addRule(RelativeLayout.RIGHT_OF, lIParam.res.getIdentifier("plain", "id", GPM));
                    header.findViewById(lIParam.res.getIdentifier("overflow", "id", GPM)).setLayoutParams(overflow);
                }
                // Resize covers
                if (PREFS.getBoolean(Common.NP_RESIZE_COVERS, false)) {
                    View pager = lIParam.view.findViewById(lIParam.res.getIdentifier("art_pager", "id", GPM));
                    RelativeLayout.LayoutParams pagerLayoutParams = (RelativeLayout.LayoutParams) pager.getLayoutParams();
                    pagerLayoutParams.addRule(RelativeLayout.BELOW, lIParam.res.getIdentifier("header_pager", "id", GPM));
                    pagerLayoutParams.addRule(RelativeLayout.ABOVE, lIParam.res.getIdentifier("play_controls", "id", GPM));
                    pager.setLayoutParams(pagerLayoutParams);
                }
                // Improve visibility
                if (PREFS.getBoolean(Common.NP_REMOVE_DROP_SHADOW, false)) {
                    ShapeDrawable shape = new ShapeDrawable(new OvalShape());
                    shape.getPaint().setColor(Color.parseColor("#AA000000"));
                    ImageView repeat = (ImageView) lIParam.view.findViewById(lIParam.res.getIdentifier("repeat", "id", GPM)),
                            shuffle = (ImageView) lIParam.view.findViewById(lIParam.res.getIdentifier("shuffle", "id", GPM));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        repeat.setBackground(shape);
                        shuffle.setBackground(shape);
                    } else {
                        //noinspection deprecation
                        repeat.setBackgroundDrawable(shape);
                        //noinspection deprecation
                        shuffle.setBackgroundDrawable(shape);
                    }
                }
                // Tint graphics
                if (PREFS.getBoolean(Common.NP_TINT_ICONS, false)) {
                    tintGraphics();
                }
            }
        });
    }

    private ImageButton getEQButton(RelativeLayout header, XResources res) {
        eQButton = new ImageButton(header.getContext());
        eQButton.setId(exLIPar.res.getIdentifier("plain", "id", GPM));
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                exLIPar.res.getDimensionPixelSize(exLIPar.res.getIdentifier("nowplaying_screen_info_block_width", "dimen", GPM)),
                exLIPar.res.getDimensionPixelSize(exLIPar.res.getIdentifier("nowplaying_screen_info_block_height", "dimen", GPM)));
        params.addRule(RelativeLayout.RIGHT_OF, exLIPar.res.getIdentifier("play_pause_header", "id", GPM));
        eQButton.setLayoutParams(params);
        XModuleResources modRes = XModuleResources.createInstance(MODULE_PATH, res);
        //noinspection deprecation
        eQButton.setImageDrawable(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? modRes.getDrawable(R.drawable.ic_equalizer_black_24dp, null) : modRes.getDrawable(R.drawable.ic_equalizer_black_24dp));
        eQButton.setScaleType(ImageView.ScaleType.CENTER);
        eQButton.setBackgroundResource(0);
        eQButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent eqIntent = new Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL");
                int i = (int) XposedHelpers.callStaticMethod(XposedHelpers.findClass(GPM + ".utils.MusicUtils", exLIPar.view.getContext().getClassLoader()), "getAudioSessionId");
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
                }
            }
        });
        eQButton.setVisibility(View.GONE);
        return eQButton;
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lPParam) throws Throwable {
        if (!lPParam.packageName.equals(GPM))
            return;
        // Icon tinting from cover Palette
        findAndHookMethod(GPM + ".ui.NowPlayingArtPageFragment", lPParam.classLoader, "updateArtVisibility", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                PREFS.reload();
                if (PREFS.getBoolean(Common.NP_TINT_ICONS, false)) {
                    ImageView mAlbum = (ImageView) XposedHelpers.getObjectField(param.thisObject, "mAlbum");
                    BitmapDrawable cover = (BitmapDrawable) mAlbum.getDrawable();
                    if (cover != null) {
                        Palette.generateAsync(cover.getBitmap(), 16, NowPlaying.this);
                    }
                }
            }
        });
        findAndHookMethod(GPM + ".ui.NowPlayingScreenFragment", lPParam.classLoader, "updateQueueSwitcherState", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                PREFS.reload();
                if (PREFS.getBoolean(Common.NP_TINT_ICONS, false)) {
                    nowPlayingScreenFragment = param.thisObject;
                    tintGraphics();
                }
            }
        });

        // Handle visibility of EQ Button
        String exScrollView = GPM + ".widgets.ExpandingScrollView";
        String exState = exScrollView + ".ExpandingState";
        findAndHookMethod(GPM + ".ui.NowPlayingScreenFragment", lPParam.classLoader, "onExpandingStateChanged", exScrollView, exState, exState, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (eQButton != null) {
                    eQButton.setVisibility(((View) XposedHelpers.getObjectField(param.thisObject, "mQueueSwitcher")).getVisibility());
                }
            }
        });
    }

    @Override
    public void onGenerated(Palette palette) {
        lastColor = palette.getVibrantColor(Color.parseColor("#9E9E9E"));
        tintGraphics();
    }

    private void tintGraphics() {
        if (lastColor == 0) {
            return;
        }
        ImageButton playPause = (ImageButton) exLIPar.view.findViewById(exLIPar.res.getIdentifier("pause", "id", GPM));
        SeekBar seekBar = (SeekBar) exLIPar.view.findViewById(exLIPar.res.getIdentifier("progress", "id", "android"));

        /*thumbsUp.setColorFilter(lastColor);
        thumbsDown.setColorFilter(lastColor);*/
        LayerDrawable progress = (LayerDrawable) seekBar.getProgressDrawable().getCurrent();
        ClipDrawable clipProgress = (ClipDrawable) progress.findDrawableByLayerId(exLIPar.res.getIdentifier("progress", "id", "android"));
        clipProgress.setColorFilter(lastColor, PorterDuff.Mode.SRC_IN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ScaleDrawable thumb = (ScaleDrawable) seekBar.getThumb();
            thumb.setColorFilter(lastColor, PorterDuff.Mode.SRC_IN);
        }
        ((TextView) exLIPar.view.findViewById(exLIPar.res.getIdentifier("currenttime", "id", GPM))).setTextColor(lastColor);
        playPause.getBackground().setColorFilter(lastColor, PorterDuff.Mode.SRC_ATOP);

        if (nowPlayingScreenFragment == null) {
            return;
        }
        ImageButton queue = (ImageButton) exLIPar.view.findViewById(exLIPar.res.getIdentifier("queue_switcher", "id", GPM));
        if (XposedHelpers.getBooleanField(nowPlayingScreenFragment, "mQueueShown")) {
            queue.setColorFilter(lastColor);
        } else queue.clearColorFilter();
    }
}
