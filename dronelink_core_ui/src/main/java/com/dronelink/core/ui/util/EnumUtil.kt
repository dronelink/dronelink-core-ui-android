package com.dronelink.core.ui.util

import com.dronelink.core.Dronelink
import com.google.gson.Gson

inline fun <reified V : Enum<V>> V.enumToString(gson: Gson): String {
    return Dronelink.getInstance().formatEnum(
            this.javaClass.simpleName,
            gson.toJson(this).replace("\"", ""),
            ""
    )
}