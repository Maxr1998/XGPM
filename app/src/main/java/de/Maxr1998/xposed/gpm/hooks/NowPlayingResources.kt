package de.Maxr1998.xposed.gpm.hooks

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.XResources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.core.view.plusAssign
import androidx.core.view.updateLayoutParams
import de.Maxr1998.xposed.gpm.*
import de.Maxr1998.xposed.gpm.Common.GPM
import de.Maxr1998.xposed.gpm.hooks.Main.Companion.modRes
import de.Maxr1998.xposed.gpm.hooks.NowPlaying.EQ_BUTTON_ID
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.*
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LayoutInflated

fun initNowPlaying(resParam: XC_InitPackageResources.InitPackageResourcesParam, prefs: SharedPreferences) {
    fun isNewDesignEnabled(): Boolean {
        return prefs.getBoolean(Common.NP_NEW_DESIGN, false)
    }

    try {
        if (BuildConfig.DEBUG)
            assert(modRes != null)
        // Replace overflow button
        resParam.res.setReplacement(GPM, "drawable", "ic_menu_moreoverflow_large", modRes?.fwd(R.drawable.ic_more_vert_black_24dp))

        // Remove drop shadow from album art
        if (prefs.getBoolean(Common.NP_REMOVE_DROP_SHADOW, false)) {
            resParam.res.setReplacement(GPM, "drawable", "now_playing_art_scrim", object : XResources.DrawableLoader() {
                @Throws(Throwable::class)
                override fun newDrawable(xResources: XResources, i: Int): Drawable {
                    return ColorDrawable(0)
                }
            })
            resParam.res.setReplacement(GPM, "dimen", "rating_controls_now_playing_page", modRes?.fwd(R.dimen.controls_height))
        }

        resParam.res.hookLayout(GPM, "layout", "nowplaying_screen", object : XC_LayoutInflated() {
            @Throws(Throwable::class)
            override fun handleLayoutInflated(lIParam: XC_LayoutInflated.LayoutInflatedParam) {
                log("Handling inflation of NowPlaying layout")
                val res = lIParam.res

                // Views
                val nowPlayingLayout: ViewGroup = lIParam.view as ViewGroup
                val context: Context = nowPlayingLayout.context
                val headerPagerId = res.getIdentifier("header_pager", "id", GPM)
                val tabletHeaderId = res.getIdentifier("tablet_header", "id", GPM)
                val topWrapperId = res.getIdentifier("top_wrapper_right", "id", GPM)
                val queueSwitcherId = res.getIdentifier("queue_switcher", "id", GPM)
                val artPagerId = res.getIdentifier("art_pager", "id", GPM)
                val playQueueWrapperId = res.getIdentifier("play_queue_wrapper", "id", GPM)
                val repeatId = res.getIdentifier("repeat", "id", GPM)
                val shuffleId = res.getIdentifier("shuffle", "id", GPM)
                val mediaRoutePickerId = res.getIdentifier("media_route_picker", "id", GPM)
                val currentTimeId = res.getIdentifier("currenttime", "id", GPM)
                val totalTimeId = res.getIdentifier("totaltime", "id", GPM)
                val playControlsId = if (context.resources.isPortrait()) res.getIdentifier("play_controls", "id", GPM)
                else res.getIdentifier("tablet_collapsed_play_controls", "id", GPM)

                val headerPager: View = nowPlayingLayout.findViewById(headerPagerId)
                        ?: nowPlayingLayout.findViewById(tabletHeaderId)
                val topWrapperRight: RelativeLayout = nowPlayingLayout.findViewById(topWrapperId)
                val queueSwitcher: View = topWrapperRight.findViewById(queueSwitcherId)
                val artPager: View = nowPlayingLayout.findViewById(artPagerId)
                val playQueueWrapper = nowPlayingLayout.findViewById<LinearLayout>(playQueueWrapperId)
                val repeat = nowPlayingLayout.findViewById<ImageView>(repeatId)
                val shuffle = nowPlayingLayout.findViewById<ImageView>(shuffleId)
                val progress = nowPlayingLayout.findViewById<View>(android.R.id.progress)
                val mediaRoutePicker = nowPlayingLayout.findViewById<View>(mediaRoutePickerId)
                val currentTime = nowPlayingLayout.findViewById<View>(currentTimeId)
                val totalTime = nowPlayingLayout.findViewById<View>(totalTimeId)
                val playControls = nowPlayingLayout.findViewById<ViewGroup>(playControlsId)

                // Add EQ button
                if (prefs.getBoolean(Common.NP_ADD_EQ_SHORTCUT, false)) {
                    @Suppress("DEPRECATION")
                    val eqButton = ImageButton(nowPlayingLayout.context).apply {
                        id = EQ_BUTTON_ID
                        scaleType = ImageView.ScaleType.CENTER
                        setImageDrawable(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) modRes?.getDrawable(R.drawable.ic_equalizer_black_24dp, null) else
                            modRes?.getDrawable(R.drawable.ic_equalizer_black_24dp))
                        setOnClickListener { view ->
                            val eqIntent = Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL")
                            val i = callStaticMethod(findClass("$GPM.utils.MusicUtils", view.context.classLoader), "getAudioSessionId", view.context) as Int
                            if (i != -1) {
                                eqIntent.putExtra("android.media.extra.AUDIO_SESSION", i)
                            } else {
                                Log.w("MusicSettings", "Failed to get valid audio session id")
                            }
                            try {
                                (view.context as Activity).startActivityForResult(eqIntent, 26)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                Toast.makeText(view.context, "Couldn't find an Equalizer app. Try to install Viper4Android, DSP Manager or similar", Toast.LENGTH_SHORT).show()
                                view.visibility = View.GONE
                            }
                        }
                        visibility = queueSwitcher.visibility
                    }

                    topWrapperRight.addView(eqButton, 1, RelativeLayout.LayoutParams(NowPlayingHelper.itemWidth(res), MATCH_PARENT))
                    eqButton.updateLayoutParams<RelativeLayout.LayoutParams> {
                        addRule(RelativeLayout.RIGHT_OF, NowPlayingHelper.mediaRouteWrapperId)
                    }
                    queueSwitcher.updateLayoutParams<RelativeLayout.LayoutParams> {
                        addRule(RelativeLayout.RIGHT_OF, EQ_BUTTON_ID)
                    }
                }

                // New design
                if (isNewDesignEnabled()) {
                    val customLayout = ConstraintLayout.create(context)
                    val customHeaderBar = RelativeLayout(context).apply {
                        id = NowPlayingHelper.headerBarId
                        setOnClickListener {
                            callMethod(getStaticObjectField(findClass("android.support.v4.content.LocalBroadcastManager", context.classLoader), "mInstance"),
                                    "sendBroadcast", Intent("com.google.android.music.nowplaying.HEADER_CLICKED"))
                        }
                    }
                    val customTitleBar = FrameLayout(context).apply { id = NowPlayingHelper.titleBarId }
                    val mediaRoutePickerWrapper = FrameLayout(nowPlayingLayout.context).apply { id = NowPlayingHelper.mediaRouteWrapperId }
                    val playQueueWrapperWrapper = FrameLayout(nowPlayingLayout.context).apply { id = NowPlayingHelper.playQueueWrapperWrapperId }

                    customLayout.addView(customHeaderBar, MATCH_PARENT, context.resources.dpToPx(64))
                    customHeaderBar += topWrapperRight.removeFromParent()
                    topWrapperRight.addView(mediaRoutePickerWrapper, 0, RelativeLayout.LayoutParams(NowPlayingHelper.itemWidth(res), MATCH_PARENT))
                    mediaRoutePicker?.apply {
                        mediaRoutePickerWrapper += mediaRoutePicker.removeFromParent()
                        minimumWidth = 0
                        minimumHeight = 0
                        updateLayoutParams<FrameLayout.LayoutParams> {
                            topMargin = 0
                            bottomMargin = 0
                            gravity = Gravity.CENTER
                        }
                    }
                    val portrait = context.resources.isPortrait()
                    customLayout.addView(artPager.removeFromParent(), if (portrait)
                        MATCH_PARENT else ConstraintSet.MATCH_CONSTRAINT, ConstraintSet.MATCH_CONSTRAINT)
                    customLayout += progress.removeFromParent()
                    progress.setPadding(0, progress.paddingTop, 0, progress.paddingBottom)
                    customLayout += currentTime.removeFromParent()
                    customLayout += totalTime.removeFromParent()
                    customLayout.addView(customTitleBar, ConstraintSet.MATCH_CONSTRAINT, context.resources.dpToPx(64))
                    if (portrait) {
                        customTitleBar += headerPager.removeFromParent()
                    } else customHeaderBar += headerPager.removeFromParent()
                    headerPager.layoutParams.height = customHeaderBar.layoutParams.height // = 64dp
                    headerPager.setBackgroundColor(Color.TRANSPARENT)
                    customLayout += playControls.removeFromParent()
                    playControls.setBackgroundColor(Color.TRANSPARENT)
                    customLayout.addView(playQueueWrapperWrapper, MATCH_PARENT, ConstraintSet.MATCH_CONSTRAINT)
                    playQueueWrapperWrapper.addView(playQueueWrapper.removeFromParent(), MATCH_PARENT, MATCH_PARENT)
                    playQueueWrapper.getChildAt(0).updateLayoutParams<ViewGroup.MarginLayoutParams> {
                        topMargin = 0
                    }

                    // Constrain the layout
                    val constraints = ConstraintSet(context.classLoader)
                    if (portrait) constraints.apply {
                        connect(customHeaderBar, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                        spanWidth(customHeaderBar)
                        connect(artPager, ConstraintSet.TOP, NowPlayingHelper.headerBarId, ConstraintSet.BOTTOM)
                        spanWidth(artPager)
                        get(artPager).setValue("dimensionRatio", "1:1")
                        connect(progress, ConstraintSet.TOP, artPagerId, ConstraintSet.BOTTOM)
                        connect(progress, ConstraintSet.BOTTOM, artPagerId, ConstraintSet.BOTTOM)
                        spanWidth(progress)
                        get(progress).apply {
                            setValue("startMargin", context.resources.dpToPx(-3))
                            setValue("endMargin", context.resources.dpToPx(-3))
                        }
                        connect(currentTime, ConstraintSet.TOP, android.R.id.progress, ConstraintSet.BOTTOM)
                        connect(currentTime, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                        get(currentTime).apply {
                            setValue("topMargin", context.resources.dpToPx(-3))
                        }
                        connect(totalTime, ConstraintSet.TOP, android.R.id.progress, ConstraintSet.BOTTOM)
                        connect(totalTime, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                        get(totalTime).apply {
                            setValue("topMargin", context.resources.dpToPx(-3))
                        }
                        connect(customTitleBar, ConstraintSet.TOP, currentTimeId, ConstraintSet.BOTTOM)
                        spanWidth(customTitleBar)
                        connect(playControls, ConstraintSet.TOP, NowPlayingHelper.titleBarId, ConstraintSet.BOTTOM)
                        spanWidth(playControls)
                        connect(playControls, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                        connect(playQueueWrapperWrapper, ConstraintSet.TOP, NowPlayingHelper.headerBarId, ConstraintSet.BOTTOM)
                        spanWidth(playQueueWrapperWrapper)
                        connect(playQueueWrapperWrapper, ConstraintSet.BOTTOM, NowPlayingHelper.titleBarId, ConstraintSet.BOTTOM)
                    } else constraints.apply {
                        fun spanRightOfArt(view: View) {
                            connect(view, ConstraintSet.START, artPagerId, ConstraintSet.END)
                            connect(view, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                        }
                        connect(customHeaderBar, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
                        spanWidth(customHeaderBar)
                        connect(artPager, ConstraintSet.TOP, NowPlayingHelper.headerBarId, ConstraintSet.BOTTOM)
                        connect(artPager, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                        connect(artPager, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                        get(artPager).setValue("dimensionRatio", "1:1")
                        progress.updateLayoutParams<ViewGroup.LayoutParams> { width = ConstraintSet.MATCH_CONSTRAINT }
                        connect(progress, ConstraintSet.TOP, NowPlayingHelper.headerBarId, ConstraintSet.BOTTOM)
                        spanRightOfArt(progress)
                        connect(currentTime, ConstraintSet.TOP, android.R.id.progress, ConstraintSet.BOTTOM)
                        connect(currentTime, ConstraintSet.START, artPagerId, ConstraintSet.END)
                        get(currentTime).apply {
                            setValue("topMargin", context.resources.dpToPx(-3))
                        }
                        connect(totalTime, ConstraintSet.TOP, android.R.id.progress, ConstraintSet.BOTTOM)
                        connect(totalTime, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
                        get(totalTime).apply {
                            setValue("topMargin", context.resources.dpToPx(-3))
                        }
                        connect(customTitleBar, ConstraintSet.TOP, currentTimeId, ConstraintSet.BOTTOM)
                        spanRightOfArt(customTitleBar)
                        playControls.updateLayoutParams<ViewGroup.LayoutParams> { width = ConstraintSet.MATCH_CONSTRAINT }
                        connect(playControls, ConstraintSet.TOP, NowPlayingHelper.titleBarId, ConstraintSet.BOTTOM)
                        spanRightOfArt(playControls)
                        connect(playControls, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
                        connect(playQueueWrapperWrapper, ConstraintSet.TOP, NowPlayingHelper.headerBarId, ConstraintSet.BOTTOM)
                        spanRightOfArt(playQueueWrapperWrapper)
                        connect(playQueueWrapperWrapper, ConstraintSet.BOTTOM, NowPlayingHelper.titleBarId, ConstraintSet.BOTTOM)
                    }
                    constraints.applyTo(customLayout)

                    // Remove the old views and save them into an invisible subgroup
                    val backup: ViewGroup = FrameLayout(context)
                    while (nowPlayingLayout.childCount > 0) {
                        nowPlayingLayout.getChildAt(0)?.let {
                            NowPlayingHelper.removeViewInternal.invoke(nowPlayingLayout, 0, it)
                            NowPlayingHelper.addViewInner.invoke(backup, it, -1, FrameLayout.LayoutParams(1, 1), true)
                        }
                    }
                    backup.requestLayout()
                    backup.visibility = View.GONE
                    customLayout += backup
                    nowPlayingLayout.addView(customLayout, MATCH_PARENT, WRAP_CONTENT)
                } else {
                    // Resize covers
                    if (prefs.getBoolean(Common.NP_RESIZE_COVERS, false)) {
                        artPager.updateLayoutParams<RelativeLayout.LayoutParams> {
                            addRule(RelativeLayout.BELOW, headerPagerId)
                            addRule(RelativeLayout.ABOVE, playControlsId)
                        }
                    }

                    // Improve playback controls visibility
                    if (prefs.getBoolean(Common.NP_REMOVE_DROP_SHADOW, false) &&
                            (repeat.parent as View).id != playControlsId &&
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        repeat.background = modRes?.getDrawable(R.drawable.ripple_circle, null)
                        shuffle.background = modRes?.getDrawable(R.drawable.ripple_circle, null)
                    }
                }

                // Touch feedback
                @SuppressLint("InlinedApi") val a = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless))
                for (i in 0 until topWrapperRight.childCount) {
                    topWrapperRight.getChildAt(i).background = a.getDrawable(0)
                }
                a.recycle()
            }
        })

        resParam.res.hookLayout(GPM, "layout", "nowplaying_header_page", object : XC_LayoutInflated() {
            @Throws(Throwable::class)
            override fun handleLayoutInflated(lIParam: XC_LayoutInflated.LayoutInflatedParam) {
                if (isNewDesignEnabled()) {
                    val albumArtWrapper = lIParam.view.findViewById<View>(lIParam.res.getIdentifier("album_small", "id", GPM)).parent as View
                    val text = lIParam.view.findViewById<View>(lIParam.res.getIdentifier("header_text", "id", GPM))
                    var lastShowAlbumArt = false
                    lIParam.view.viewTreeObserver.addOnGlobalLayoutListener {
                        val showAlbumArt = (lIParam.view.parent?.parent as View?)?.id != NowPlayingHelper.titleBarId
                        if (showAlbumArt == lastShowAlbumArt)
                            return@addOnGlobalLayoutListener
                        albumArtWrapper.visibility = if (showAlbumArt) View.VISIBLE else View.GONE
                        text.updateLayoutParams<LinearLayout.LayoutParams> {
                            leftMargin = lIParam.res.dpToPx(if (!showAlbumArt) 16 else 0)
                        }
                        lastShowAlbumArt = showAlbumArt
                    }
                }
                if (prefs.getBoolean(Common.NP_HIDE_YT_ICONS, false)) {
                    lIParam.view.findViewById<View>(lIParam.res.getIdentifier("youtube_tiny_icon", "id", GPM)).removeFromParent()
                }
            }
        })

        resParam.res.hookLayout(GPM, "layout", "nowplaying_art_page", object : XC_LayoutInflated() {
            @Throws(Throwable::class)
            override fun handleLayoutInflated(lIParam: XC_LayoutInflated.LayoutInflatedParam) {
                if (prefs.getBoolean(Common.NP_HIDE_YT_ICONS, false)) {
                    val root = lIParam.view as RelativeLayout
                    val backup = FrameLayout(root.context)
                    backup += root.findViewById<View>(root.resources.getIdentifier("youtube_overlay", "id", GPM)).removeFromParent()
                    backup += root.findViewById<View>(root.resources.getIdentifier("youtube_play_red", "id", GPM)).removeFromParent()
                    backup += root.findViewById<View>(root.resources.getIdentifier("youtube_widget_text", "id", GPM)).removeFromParent()
                    backup += root.findViewById<View>(root.resources.getIdentifier("youtube_widget_white", "id", GPM)).removeFromParent()
                    backup.visibility = View.GONE
                    root += backup
                }
            }
        })
    } catch (t: Throwable) {
        log(t)
    }
}