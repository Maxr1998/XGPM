package de.Maxr1998.xposed.gpm.hooks;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
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
                            final Object headerView = getObjectField(param.thisObject, "mMaterialHeader");
                            final View.OnClickListener openArtistPage = (View.OnClickListener) getObjectField(headerView, "mAvatarViewOnClickListener");
                            final View artistImage = (View) getObjectField(headerView, "mAvatar");
                            final View extraInfo = (View) getObjectField(headerView, "mSubtitle");
                            artistImage.setOnClickListener(openArtistPage);
                            extraInfo.setOnClickListener(openArtistPage);
                        }
                    });
        } catch (Throwable t) {
            log(t);
        }
    }
}
