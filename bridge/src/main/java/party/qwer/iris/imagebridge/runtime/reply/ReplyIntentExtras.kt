package party.qwer.iris.imagebridge.runtime.reply

import android.content.Intent

@Suppress("DEPRECATION")
internal fun Intent.stringExtraOrNull(name: String): String? =
    when (val raw = extras?.get(name)) {
        is String -> raw.takeIf { it.isNotBlank() }
        is CharSequence -> raw.toString().takeIf { it.isNotBlank() }
        is Number -> raw.toString()
        else -> null
    }

@Suppress("DEPRECATION")
internal fun Intent.textExtraOrNull(name: String): String? =
    when (val raw = extras?.get(name)) {
        is CharSequence -> raw.toString()
        is Number -> raw.toString()
        else -> null
    }

@Suppress("DEPRECATION")
internal fun Intent.intExtraOrNull(name: String): Int? =
    when (val raw = extras?.get(name)) {
        is Int -> raw
        is Long -> raw.toInt()
        is Number -> raw.toInt()
        is String -> raw.toIntOrNull()
        else -> null
    }

@Suppress("DEPRECATION")
internal fun Intent.longExtraOrNull(name: String): Long? =
    when (val raw = extras?.get(name)) {
        is Long -> raw
        is Int -> raw.toLong()
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull()
        else -> null
    }

internal fun Intent.nestedIntentExtraOrNull(name: String): Intent? =
    runCatching {
        getParcelableExtra(name, Intent::class.java)
    }.getOrNull()
