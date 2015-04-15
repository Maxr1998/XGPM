package de.Maxr1998.xposed.gpm.hooks;

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
import android.support.v7.graphics.Palette;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;

import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LayoutInflated;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class NowPlaying implements IXposedHookInitPackageResources, IXposedHookLoadPackage, Palette.PaletteAsyncListener {

    private XC_LayoutInflated.LayoutInflatedParam exLIPar;
    private int lastColor = 0;
    private Object nowPlayingScreenFragment;

    @Override
    public void handleInitPackageResources(final XC_InitPackageResources.InitPackageResourcesParam resParam) throws Throwable {
        if (!resParam.packageName.equals(GPM))
            return;

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
                if (PREFS.getBoolean(Common.NP_RESIZE_COVERS, false)) {
                    View pager = lIParam.view.findViewById(lIParam.res.getIdentifier("art_pager", "id", GPM));
                    RelativeLayout.LayoutParams pagerLayoutParams = (RelativeLayout.LayoutParams) pager.getLayoutParams();
                    pagerLayoutParams.addRule(RelativeLayout.BELOW, lIParam.res.getIdentifier("header_pager", "id", GPM));
                    pagerLayoutParams.addRule(RelativeLayout.ABOVE, lIParam.res.getIdentifier("play_controls", "id", GPM));
                    pager.setLayoutParams(pagerLayoutParams);
                }
                if (PREFS.getBoolean(Common.NP_TINT_ICONS, false)) {
                    tintGraphics();
                }
            }
        });
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
        findAndHookMethod(GPM + ".ui.NowPlayingScreenFragment", lPParam.classLoader, "onClick", View.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                PREFS.reload();
                if (PREFS.getBoolean(Common.NP_TINT_ICONS, false)) {
                    nowPlayingScreenFragment = param.thisObject;
                    tintGraphics();
                }
            }
        });
    }

    @Override
    public void onGenerated(Palette palette) {
        lastColor = palette.getVibrantColor(Color.YELLOW);
        tintGraphics();
    }

    private void tintGraphics() {
        if (lastColor == 0) {
            return;
        }
        ImageButton thumbsUp = (ImageButton) exLIPar.view.findViewById(exLIPar.res.getIdentifier("thumbsup", "id", GPM)),
                thumbsDown = (ImageButton) exLIPar.view.findViewById(exLIPar.res.getIdentifier("thumbsdown", "id", GPM)),
                playPause = (ImageButton) exLIPar.view.findViewById(exLIPar.res.getIdentifier("pause", "id", GPM));
        SeekBar seekBar = (SeekBar) exLIPar.view.findViewById(exLIPar.res.getIdentifier("progress", "id", "android"));

        thumbsUp.setColorFilter(lastColor);
        thumbsDown.setColorFilter(lastColor);
        LayerDrawable progress = (LayerDrawable) seekBar.getProgressDrawable().getCurrent();
        ClipDrawable clipProgress = (ClipDrawable) progress.findDrawableByLayerId(exLIPar.res.getIdentifier("progress", "id", "android"));
        clipProgress.setColorFilter(lastColor, PorterDuff.Mode.SRC_IN);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            ScaleDrawable thumb = (ScaleDrawable) seekBar.getThumb();
            thumb.setColorFilter(lastColor, PorterDuff.Mode.SRC_IN);
        }
        playPause.getBackground().setColorFilter(lastColor, PorterDuff.Mode.SRC_ATOP);

        if (nowPlayingScreenFragment == null) {
            return;
        }
        System.out.println("Queue tint");
        ImageButton queue = (ImageButton) exLIPar.view.findViewById(exLIPar.res.getIdentifier("queue_switcher", "id", GPM));
        if (XposedHelpers.getBooleanField(nowPlayingScreenFragment, "mQueueShown")) {
            queue.setColorFilter(lastColor);
            System.out.println("Queue visible!");
        } else queue.clearColorFilter();
    }
}
