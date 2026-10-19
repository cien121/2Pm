package com.chan.weave.data

import androidx.room.TypeConverter
import com.chan.weave.core.CoreType

/** Room converters for [CoreType]. */
class CoreTypeConverters {
    @TypeConverter
    fun fromCoreType(value: CoreType): String = value.name

    @TypeConverter
    fun toCoreType(value: String): CoreType = CoreType.fromName(value)
}
