package de.Maxr1998.xposed.gpm.hooks;

import android.media.AudioManager;

import de.Maxr1998.xposed.gpm.Common;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.Maxr1998.xposed.gpm.Common.GPM;
import static de.Maxr1998.xposed.gpm.hooks.Main.PREFS;
import static de.robv.android.xposed.XposedBridge.log;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getStaticObjectField;

class Misc {

    public static void init(final XC_LoadPackage.LoadPackageParam lPParam) {
        try {
            // Prevent request audio focus
            findAndHookMethod(AudioManager.class, "requestAudioFocus", AudioManager.OnAudioFocusChangeListener.class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    PREFS.reload();
                    if (PREFS.getBoolean(Common.DISABLE_AUDIO_FOCUS, false))
                        param.setResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
                }
            });

            // Prevent I'm feeling lucky radio when launching GPM from Voice
            findAndHookMethod(GPM + ".search.VoiceActionHelper", lPParam.classLoader, "playIFL", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    Object musicService = getStaticObjectField(findClass(GPM + ".utils.MusicUtils", lPParam.classLoader), "sService");

                    if (musicService != null) {
                        callMethod(musicService, "play");
                    }
                    return null;
                }
            });
        } catch (Throwable t) {
            log(t);
        }
    }
}