package de.Maxr1998.xposed.gpm.hooks

import android.content.Context
import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import de.Maxr1998.xposed.gpm.Common
import de.Maxr1998.xposed.gpm.hooks.Main.Companion.modRes
import de.robv.android.xposed.XposedBridge.log
import de.robv.android.xposed.XposedHelpers.*
import java.lang.reflect.Method


object NowPlayingHelper {
    val headerBarId: Int by lazy {
        modRes?.getIdentifier("custom_header_bar", "id", Common.XGPM) ?: 0
    }
    val titleBarId: Int by lazy {
        modRes?.getIdentifier("custom_title_bar", "id", Common.XGPM) ?: 0
    }
    val mediaRouteWrapperId: Int by lazy {
        modRes?.getIdentifier("media_route_wrapper", "id", Common.XGPM) ?: 0
    }

    val playQueueWrapperWrapperId: Int by lazy {
        modRes?.getIdentifier("play_queue_wrapper", "id", Common.XGPM) ?: 0
    }

    val addViewInner: Method = findMethodBestMatch(ViewGroup::class.java, "addViewInner", View::class.java, Int::class.java, ViewGroup.LayoutParams::class.java, Boolean::class.java)
    val removeViewInternal: Method = findMethodBestMatch(ViewGroup::class.java, "removeViewInternal", Int::class.java, View::class.java)

    fun itemWidth(res: Resources) =
            res.getDimensionPixelSize(res.getIdentifier("nowplaying_screen_info_block_width", "dimen", Common.GPM))
}

object ConstraintLayout {
    fun create(context: Context): ViewGroup =
            getClass(context.classLoader).getConstructor(Context::class.java).newInstance(context) as ViewGroup

    fun getClass(classLoader: ClassLoader): Class<*> =
            findClass("android.support.constraint.ConstraintLayout", classLoader)
}

class ConstraintSet(val classLoader: ClassLoader) {
    private val constraintSetClass: Class<*> = findClass("android.support.constraint.ConstraintSet", classLoader)
    private val constraintClass: Class<*> = findClass("android.support.constraint.ConstraintSet.Constraint", classLoader)
    private val applyToInternalMethod = findMethodBestMatch(constraintSetClass, "applyToInternal", ConstraintLayout.getClass(classLoader))
    private val instance: Any = constraintSetClass.newInstance()

    fun applyTo(layout: ViewGroup) {
        applyToInternalMethod.invoke(instance, layout)
        callMethod(layout, "setConstraintSet", instance)
    }

    private fun getAll() = getObjectField(instance, "mConstraints") as HashMap<Int, Any>

    fun get(view: View): Constraint {
        val constraints = getAll()
        val viewID = view.id
        return if (!constraints.containsKey(viewID)) {
            Constraint().apply {
                setValue("mWidth", view.layoutParams.width)
                setValue("mHeight", view.layoutParams.height)
                setValue("visibility", view.visibility)
                constraints[viewID] = instance
            }
        } else Constraint(constraints[viewID]!!)
    }

    fun connect(startView: View, startSide: Int, endID: Int, endSide: Int) {
        val constraint = get(startView)
        when (startSide) {
            LEFT -> when (endSide) {
                LEFT -> {
                    constraint.setValue("leftToLeft", endID)
                    constraint.setValue("leftToRight", UNSET)
                }
                RIGHT -> {
                    constraint.setValue("leftToRight", endID)
                    constraint.setValue("leftToLeft", UNSET)
                }
                else -> throw IllegalArgumentException("left to " + sideToString(endSide) + " undefined")
            }
            RIGHT -> when (endSide) {
                LEFT -> {
                    constraint.setValue("rightToLeft", endID)
                    constraint.setValue("rightToRight", UNSET)
                }
                RIGHT -> {
                    constraint.setValue("rightToRight", endID)
                    constraint.setValue("rightToLeft", UNSET)
                }
                else -> throw IllegalArgumentException("right to " + sideToString(endSide) + " undefined")
            }
            TOP -> when (endSide) {
                TOP -> {
                    constraint.setValue("topToTop", endID)
                    constraint.setValue("topToBottom", UNSET)
                    constraint.setValue("baselineToBaseline", UNSET)
                }
                BOTTOM -> {
                    constraint.setValue("topToBottom", endID)
                    constraint.setValue("topToTop", UNSET)
                    constraint.setValue("baselineToBaseline", UNSET)
                }
                else -> throw IllegalArgumentException("right to " + sideToString(endSide) + " undefined")
            }
            BOTTOM -> when (endSide) {
                BOTTOM -> {
                    constraint.setValue("bottomToBottom", endID)
                    constraint.setValue("bottomToTop", UNSET)
                    constraint.setValue("baselineToBaseline", UNSET)
                }
                TOP -> {
                    constraint.setValue("bottomToTop", endID)
                    constraint.setValue("bottomToBottom", UNSET)
                    constraint.setValue("baselineToBaseline", UNSET)
                }
                else -> throw IllegalArgumentException("right to " + sideToString(endSide) + " undefined")
            }
            BASELINE -> if (endSide == BASELINE) {
                constraint.setValue("baselineToBaseline", endID)
                constraint.setValue("bottomToBottom", UNSET)
                constraint.setValue("bottomToTop", UNSET)
                constraint.setValue("topToTop", UNSET)
                constraint.setValue("topToBottom", UNSET)
            } else {
                throw IllegalArgumentException("right to " + sideToString(endSide) + " undefined")
            }
            START -> when (endSide) {
                START -> {
                    constraint.setValue("startToStart", endID)
                    constraint.setValue("startToEnd", UNSET)
                }
                END -> {
                    constraint.setValue("startToEnd", endID)
                    constraint.setValue("startToStart", UNSET)
                }
                else -> throw IllegalArgumentException("right to " + sideToString(endSide) + " undefined")
            }
            END -> when (endSide) {
                END -> {
                    constraint.setValue("endToEnd", endID)
                    constraint.setValue("endToStart", UNSET)
                }
                START -> {
                    constraint.setValue("endToStart", endID)
                    constraint.setValue("endToEnd", UNSET)
                }
                else -> throw IllegalArgumentException("right to " + sideToString(endSide) + " undefined")
            }
            else -> throw IllegalArgumentException(sideToString(startSide) + " to " + sideToString(endSide) + " unknown")
        }
    }

    fun spanWidth(view: View) {
        connect(view, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        connect(view, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
    }

    fun print() {
        getAll().forEach { k, v ->
            log("$k: ${Constraint(v).print()}")
        }
    }

    inner class Constraint(val instance: Any = constraintClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()) {
        fun setValue(key: String, value: Int) {
            setIntField(instance, key, value)
        }

        fun setValue(key: String, value: String) {
            setObjectField(instance, key, value)
        }

        fun print(): String {
            val output = StringBuilder()
            output.appendln("Constraint {")
            constraintClass.fields.forEach {
                output.appendln("    ${it.name}: ${it.get(instance)}")
            }
            output.appendln("}")
            return output.toString()
        }
    }

    companion object {
        const val MATCH_CONSTRAINT = 0
        const val PARENT_ID = 0
        const val UNSET = -1
        const val HORIZONTAL = 0
        const val VERTICAL = 1
        const val LEFT = 1
        const val RIGHT = 2
        const val TOP = 3
        const val BOTTOM = 4
        const val BASELINE = 5
        const val START = 6
        const val END = 7


        private fun sideToString(side: Int): String {
            return when (side) {
                LEFT -> "left"
                RIGHT -> "right"
                TOP -> "top"
                BOTTOM -> "bottom"
                BASELINE -> "baseline"
                START -> "start"
                END -> "end"
                else -> "undefined"
            }
        }
    }
}