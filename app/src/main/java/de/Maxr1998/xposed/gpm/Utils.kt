package de.Maxr1998.xposed.gpm

import android.view.View
import android.view.ViewGroup

fun View.removeFromParent(invalidate: Boolean = true): Boolean {
    if (parent is ViewGroup) {
        val parentView = parent as ViewGroup
        if (invalidate)
            parentView.removeView(this)
        else
            parentView.removeViewInLayout(this)
        return true
    }
    return false
}