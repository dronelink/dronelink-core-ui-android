package com.dronelink.core.ui.util

import android.content.Context

fun Context.dpToPx(dp: Int): Int {
    return dp * resources.displayMetrics.density.toInt();
}