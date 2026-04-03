package yangfentuozi.batteryrecorder.ui.model

import yangfentuozi.batteryrecorder.R
import yangfentuozi.batteryrecorder.appString
import yangfentuozi.batteryrecorder.shared.config.dataclass.UpdateChannel

val UpdateChannel.displayName: String
    get() = when (this) {
        UpdateChannel.Stable -> appString(R.string.update_channel_stable)
        UpdateChannel.Prerelease -> appString(R.string.update_channel_prerelease)
    }
