package yangfentuozi.batteryrecorder.ui.model

import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.appString
import yangfentuozi.batteryrecorder.shared.util.LoggerX

val LoggerX.LogLevel.displayName: String
    get() = when (this) {
        LoggerX.LogLevel.Verbose -> appString(R.string.log_level_verbose)
        LoggerX.LogLevel.Debug -> appString(R.string.log_level_debug)
        LoggerX.LogLevel.Info -> appString(R.string.log_level_info)
        LoggerX.LogLevel.Warning -> appString(R.string.log_level_warning)
        LoggerX.LogLevel.Error -> appString(R.string.log_level_error)
        LoggerX.LogLevel.Assert -> appString(R.string.log_level_assert)
        LoggerX.LogLevel.Disabled -> appString(R.string.log_level_disabled)
    }
