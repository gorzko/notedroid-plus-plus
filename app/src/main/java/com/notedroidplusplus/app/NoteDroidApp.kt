package com.notedroidplusplus.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Applies the persisted app-language preference on every process start via
 * per-app locales (backward-compatible through AppCompat on API < 33).
 */
class NoteDroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val tag = getSharedPreferences("notedroid_settings", MODE_PRIVATE)
            .getString("app_language", null)
        applyLanguage(tag)
    }

    companion object {
        const val KEY_APP_LANGUAGE = "app_language"

        fun applyLanguage(tag: String?) {
            val locales = if (tag.isNullOrEmpty()) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
