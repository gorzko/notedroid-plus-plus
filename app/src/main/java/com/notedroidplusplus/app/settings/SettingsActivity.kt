package com.notedroidplusplus.app.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceFragmentCompat
import com.notedroidplusplus.app.R

class SettingsActivity : AppCompatActivity() {

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.sharedPreferencesName = "notedroid_settings"
            setPreferencesFromResource(R.xml.preferences, rootKey)
        }
    }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "app_language") {
            val tag = getSharedPreferences("notedroid_settings", MODE_PRIVATE).getString(key, null)
            com.notedroidplusplus.app.NoteDroidApp.applyLanguage(tag)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment())
            .commit()
        getSharedPreferences("notedroid_settings", MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onDestroy() {
        getSharedPreferences("notedroid_settings", MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onDestroy()
    }
}
