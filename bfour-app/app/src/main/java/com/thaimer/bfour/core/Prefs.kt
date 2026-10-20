package com.thaimer.bfour.core

import android.content.Context
import androidx.core.content.edit

/** Everything that survives an app restart. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("bfour", Context.MODE_PRIVATE)

    var deviceAddress: String?
        get() = sp.getString(KEY_ADDRESS, null)
        set(value) = sp.edit { putString(KEY_ADDRESS, value) }

    var deviceType: Int?
        get() = sp.getInt(KEY_TYPE, -1).takeIf { it >= 0 }
        set(value) = sp.edit { putInt(KEY_TYPE, value ?: -1) }

    var unit: TempUnit
        get() = if (sp.getString(KEY_UNIT, "C") == "F") TempUnit.F else TempUnit.C
        set(value) = sp.edit { putString(KEY_UNIT, value.name) }

    fun target(probe: Int): Target? {
        val c = sp.getFloat("$KEY_TARGET$probe", 0f)
        if (c <= 0f) return null
        return Target(c.toDouble(), sp.getString("$KEY_LABEL$probe", null))
    }

    fun setTarget(probe: Int, target: Target?) {
        sp.edit {
            putFloat("$KEY_TARGET$probe", target?.celsius?.toFloat() ?: 0f)
            putString("$KEY_LABEL$probe", target?.label)
        }
    }

    private companion object {
        const val KEY_ADDRESS = "address"
        const val KEY_TYPE = "type"
        const val KEY_UNIT = "unit"
        const val KEY_TARGET = "target_"
        const val KEY_LABEL = "label_"
    }
}

data class Target(val celsius: Double, val label: String?)
