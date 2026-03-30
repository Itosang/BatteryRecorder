package yangfentuozi.batteryrecorder.shared.config

import android.content.SharedPreferences

data class LongConfigItem(
    val key: String,
    val def: Long,
    val min: Long,
    val max: Long
) {
    fun coerce(value: Long): Long {
        return value.coerceIn(min, max)
    }

    fun readFromSP(sharedPreferences: SharedPreferences): Long {
        return coerce(sharedPreferences.getLong(key, def))
    }

    fun writeToSP(editor: SharedPreferences.Editor, value: Long) {
        editor.putLong(key, coerce(value))
    }
}

data class IntConfigItem(
    val key: String,
    val def: Int,
    val min: Int,
    val max: Int
) {
    fun coerce(value: Int): Int {
        return value.coerceIn(min, max)
    }

    fun readFromSP(sharedPreferences: SharedPreferences): Int {
        return coerce(sharedPreferences.getInt(key, def))
    }

    fun writeToSP(editor: SharedPreferences.Editor, value: Int) {
        editor.putInt(key, coerce(value))
    }
}

data class BooleanConfigItem(
    val key: String,
    val def: Boolean
) {
    fun readFromSP(sharedPreferences: SharedPreferences): Boolean {
        return sharedPreferences.getBoolean(key, def)
    }

    fun writeToSP(editor: SharedPreferences.Editor, value: Boolean) {
        editor.putBoolean(key, value)
    }
}

data class StringSetConfigItem(
    val key: String,
    val def: Set<String> = emptySet()
) {
    fun readFromSP(sharedPreferences: SharedPreferences): Set<String> {
        return sharedPreferences.getStringSet(key, def)?.toSet() ?: def
    }

    fun writeToSP(editor: SharedPreferences.Editor, value: Set<String>) {
        editor.putStringSet(key, value)
    }
}

data class EnumConfigItem<T>(
    val key: String,
    val def: T,
    val converter: EnumConfigConverter<T>
) 
        where T : Enum<T> {

    fun readFromSP(sharedPreferences: SharedPreferences): T {
        return converter.fromValue(sharedPreferences.getInt(key, converter.toValue(def))) ?: def
    }

    fun writeToSP(editor: SharedPreferences.Editor, value: T) {
        editor.putInt(key, converter.toValue(value))
    }
}

interface EnumConfigConverter<T> where T : Enum<T> {
    fun fromValue(value: Int): T?
    fun toValue(value: T): Int
}
