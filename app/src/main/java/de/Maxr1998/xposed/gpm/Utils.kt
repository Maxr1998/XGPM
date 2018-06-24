package de.Maxr1998.xposed.gpm

import android.content.res.Resources
import android.support.annotation.Px
import android.view.View
import android.view.ViewGroup

fun View.removeFromParent(invalidate: Boolean = true): View {
    if (parent != null && parent is ViewGroup) {
        val parentView = parent as ViewGroup
        if (invalidate)
            parentView.removeView(this)
        else
            parentView.removeViewInLayout(this)
    }
    return this
}

@Px
fun Resources.dpToPx(v: Int) = (v * displayMetrics.density).toInt()

fun Resources.isPortrait(): Boolean = displayMetrics.heightPixels > displayMetrics.widthPixels