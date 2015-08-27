package de.Maxr1998.xposed.gpm.hooks;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

public class TrackList {

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Artist shortcut
            findAndHookMethod(GPM + ".ui.mylibrary.MaterialTrackContainerFragment", lPParam.classLoader, "onCreateView",
                    LayoutInflater.class, ViewGroup.class, Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                            View.OnClickListener openArtistPage = new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    Object meta = getObjectField(param.thisObject, "mDetailsMetadata");
                                    Class[] args = {Context.class, Long.class, String.class, Boolean.class};
                                    XposedHelpers.callStaticMethod(findClass(GPM + ".ui.mylibrary.ArtistPageActivity", lPParam.classLoader),
                                            "showArtist", args, XposedHelpers.callMethod(param.thisObject, "getActivity"),
                                            getObjectField(meta, "artistId"), getObjectField(meta, "secondaryTitle"), true);
                                }
                            };
                            Object header = getObjectField(param.thisObject, "mMaterialHeader");
                            Object songList = getObjectField(header, "mSongList");

                            if (findClass(GPM + ".medialist.PlaylistSongList", lPParam.classLoader).isInstance(songList) || findClass(GPM + ".medialist.SharedWithMeSongList", lPParam.classLoader).isInstance(songList)) {
                                return;
                            }
                            View artistImage = (View) getObjectField(header, "mAvatar");
                            View extraInfo = (View) getObjectField(header, "mSubtitle");
                            artistImage.setOnClickListener(openArtistPage);
                            extraInfo.setOnClickListener(openArtistPage);
                        }
                    });
        } catch (Throwable t) {
            log(t);
        }
    }
}
