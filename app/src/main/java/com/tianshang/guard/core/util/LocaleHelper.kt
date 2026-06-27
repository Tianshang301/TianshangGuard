package com.tianshang.guard.core.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    fun wrapContext(context: Context, language: String): Context {
        val locale = when (language) {
            "zh" -> Locale.CHINESE
            "en" -> Locale.ENGLISH
            else -> return context // "system" - use system locale
        }

        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration)
    }

    fun getCurrentLanguage(context: Context): String {
        val locale = context.resources.configuration.locales[0]
        return if (locale.language == "zh") "zh" else "en"
    }
}
