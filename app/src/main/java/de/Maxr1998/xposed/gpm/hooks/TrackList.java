package de.Maxr1998.xposed.gpm.hooks;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.callStaticMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getLongField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

@SuppressWarnings("RedundantThrows")
public class TrackList {

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Artist shortcut
            findAndHookMethod(GPM + ".ui.mylibrary.TrackContainerFragment", lPParam.classLoader, "onCreateView",
                    LayoutInflater.class, ViewGroup.class, Bundle.class, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                            final Context context = (Context) callMethod(param.thisObject, "getActivity");
                            final Object headerView = getObjectField(param.thisObject, "mMaterialHeader");
                            View.OnClickListener openArtistPage = view -> {
                                final Object meta = getObjectField(param.thisObject, "mDetailsMetadata");
                                final Object songList = getObjectField(headerView, "mSongList");
                                final boolean isNautilus = findClass(GPM + ".medialist.ExternalSongList", lPParam.classLoader).isInstance(songList);
                                final Class artistPageActivity = findClass(GPM + ".ui.mylibrary.ArtistPageActivity", lPParam.classLoader);
                                final long artistId = getLongField(meta, "artistId");
                                final String secondaryTitle = (String) getObjectField(meta, "secondaryTitle");
                                if (isNautilus) {
                                    callStaticMethod(artistPageActivity, "showNautilusArtist", new Class[]{Context.class, String.class, String.class},
                                            context, getObjectField(meta, "metajamArtistId"), secondaryTitle);
                                } else if (artistId != -1) {
                                    callStaticMethod(artistPageActivity, "showArtist", new Class[]{Context.class, Long.class, String.class, Boolean.class},
                                            context, artistId, secondaryTitle, true);
                                }
                            };
                            View artistImage = (View) getObjectField(headerView, "mAvatar");
                            View extraInfo = (View) getObjectField(headerView, "mSubtitle");
                            artistImage.setOnClickListener(openArtistPage);
                            extraInfo.setOnClickListener(openArtistPage);
                        }
                    });
        } catch (Throwable t) {
            log(t);
        }
    }
}
