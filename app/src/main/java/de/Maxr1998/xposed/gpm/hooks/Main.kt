package de.Maxr1998.xposed.gpm.hooks

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.XModuleResources
import android.os.Build
import com.crossbowffs.remotepreferences.RemotePreferences
import de.Maxr1998.xposed.gpm.BuildConfig
import de.Maxr1998.xposed.gpm.Common
import de.Maxr1998.xposed.gpm.Common.GPM
import de.robv.android.xposed.*
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.*
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage


class Main : IXposedHookZygoteInit, IXposedHookLoadPackage, IXposedHookInitPackageResources {

    @Throws(Throwable::class)
    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        MODULE_PATH = startupParam.modulePath
        modRes = XModuleResources.createInstance(MODULE_PATH, null)
    }

    @Throws(Throwable::class)
    override fun handleLoadPackage(lPParam: XC_LoadPackage.LoadPackageParam) {
        if (lPParam.packageName == GPM) {
            findAndHookMethod(Application::class.java, "attach", Context::class.java, object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    PREFS = (param.args[0] as Context).getRemotePreferences()
                    // UI
                    Features.init(lPParam)
                    MainStage.init(lPParam)
                    MyLibrary.init(lPParam)
                    NavigationDrawer.init(lPParam)
                    NowPlaying.init(lPParam)
                    TrackList.init(lPParam)
                    Misc.init(lPParam)

                    // External
                    //ArtReplacer.init(lPParam);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        //NotificationMod.init(lPParam);
                    }

                    // Debug
                    if (BuildConfig.DEBUG) {
                        try {
                            findAndHookMethod("$GPM.utils.DebugUtils", lPParam.classLoader, "isLoggable", findClass("$GPM.utils.DebugUtils.MusicTag", lPParam.classLoader), object : XC_MethodReplacement() {
                                @Throws(Throwable::class)
                                override fun replaceHookedMethod(methodHookParam: MethodHookParam): Any {
                                    return true
                                }
                            })
                        } catch (t: Throwable) {
                            log(t)
                        }
                    }
                }
            })
        }/* else if (lPParam.packageName == "android" && Build.DEVICE == "mako") {
            hookAllMethods(Process::class.java, "start", object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val flags = param.args[5] as Int
                    param.args[5] = flags or 0x1
                }
            })
            findAndHookMethod("com.android.server.wm.WindowManagerService", lPParam.classLoader, "isSystemSecure",
                    XC_MethodReplacement.returnConstant(true))
        }*/
    }

    @Throws(Throwable::class)
    override fun handleInitPackageResources(resParam: XC_InitPackageResources.InitPackageResourcesParam) {
        if (resParam.packageName == GPM) {
            systemContext?.let {
                initNowPlaying(resParam, it.getRemotePreferences())
            }
        }
    }

    companion object {
        @JvmField
        var MODULE_PATH: String? = null
        @JvmField
        var PREFS: SharedPreferences? = null
        @JvmField
        var modRes: XModuleResources? = null


        // #############################################################################
        // Thanks to XposedGELSettings for the following snippet (https://git.io/vP2Gw):
        private val systemContext: Context?
            get() = callStaticMethod(findClass("android.app.ActivityThread", null), "currentActivityThread")?.let {
                callMethod(it, "getSystemContext") as Context
            }
        // #############################################################################

        private fun Context.getRemotePreferences(): SharedPreferences =
                RemotePreferences(this, Common.PREFERENCE_PROVIDER_AUTHORITY, Common.XGPM + "_preferences")
    }
}