package com.dronelink.core.ui.util

import com.dronelink.core.Dronelink
import com.dronelink.core.Kernel

inline fun <reified V: Enum<V>> V.enumToString(): String {
    val gson = Kernel.createGson()
    return Dronelink.getInstance().formatEnum(
        this.javaClass.simpleName,
        gson.toJson(this).replace("\"", ""),
        ""
    )
}