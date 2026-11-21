package com.mubarak.bydspeed.ui

import android.content.Context
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.google.android.material.R as M

/** Small helpers for building large, touch-friendly car UI in code. */
fun Context.dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

fun Context.sectionTitle(text: CharSequence) = TextView(this).apply {
    this.text = text
    TextViewCompat.setTextAppearance(this, M.style.TextAppearance_Material3_HeadlineSmall)
    setPadding(0, dp(24), 0, dp(8))
}

fun Context.bodyText(text: CharSequence = "", sizeSp: Float = 18f) = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
}

fun vertical(context: Context, pad: Int = 0) = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(pad, pad, pad, pad)
    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}

fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
    LinearLayout.LayoutParams(w, h, weight)
