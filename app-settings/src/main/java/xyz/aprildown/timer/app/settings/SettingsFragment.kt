package xyz.aprildown.timer.app.settings

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.net.toUri
import androidx.navigation.fragment.NavHostFragment
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import xyz.aprildown.timer.app.base.data.DarkTheme
import xyz.aprildown.timer.app.base.data.FlavorData
import xyz.aprildown.timer.app.base.data.PreferenceData
import xyz.aprildown.timer.app.base.ui.FlavorUiInjector
import xyz.aprildown.timer.app.base.ui.FlavorUiInjectorQualifier
import xyz.aprildown.timer.app.base.ui.MainCallback
import xyz.aprildown.timer.app.base.utils.NavigationUtils.subLevelNavigate
import xyz.aprildown.timer.component.settings.DarkThemeDialog
import xyz.aprildown.timer.component.settings.TweakTimeDialog
import xyz.aprildown.timer.domain.TimeUtils
import xyz.aprildown.tools.helper.IntentHelper
import xyz.aprildown.tools.helper.createChooserIntentIfDead
import xyz.aprildown.tools.helper.startActivitySafely
import java.util.Optional
import javax.inject.Inject

@AndroidEntryPoint
class SettingsFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener,
    Preference.OnPreferenceClickListener {

    @Inject
    lateinit var flavorData: FlavorData

    @Inject
    @FlavorUiInjectorQualifier
    lateinit var flavorUiInjector: Optional<FlavorUiInjector>

    private var sharedPreferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.pref_settings, rootKey)

        arguments?.run {
            getString(EXTRA_TO_PREF)
                ?.takeIf { it.isNotEmpty() }
                ?.let { scrollToPreference(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
        when (preference?.key) {
            KEY_APP_LANGUAGE -> {
                (requireActivity() as? MainCallback.ActivityCallback)
                    ?.restartWithDestination(R.id.dest_settings)
            }
            KEY_SCREEN -> refreshBrightnessTime(newValue?.toString())
        }
        // Set result so parent knows to refresh itself
        activity?.setResult(RESULT_OK)
        return true
    }

    override fun onPreferenceClick(preference: Preference?): Boolean {
        val context = requireActivity()
        when (preference?.key) {
            KEY_DARK_THEME -> {
                DarkThemeDialog(context).showSettingsDialog {
                    DarkTheme(context).applyAppCompatDelegate()
                    // If then dark theme isn't changed, we have to update UI.
                    (context as MainCallback.ActivityCallback).recreateThemeItem()
                    preference.summary = getDarkThemeSummary()
                }
            }
            KEY_EDIT_LAYOUT -> {
                NavHostFragment.findNavController(this)
                    .subLevelNavigate(R.id.dest_one_layout)
            }
            KEY_THEME -> {
                NavHostFragment.findNavController(this)
                    .subLevelNavigate(R.id.dest_theme)
            }
            KEY_TWEAK_TIME -> {
                TweakTimeDialog().show(context) {
                    findPreference<Preference>(KEY_TWEAK_TIME)?.updateTweakTimeSummary()
                }
            }
            KEY_FLOATING_WINDOW_PIP -> {
                NavHostFragment.findNavController(this)
                    .subLevelNavigate(R.id.dest_settings_floating_window_pip)
            }
            KEY_BAKED_COUNT -> {
                flavorUiInjector.orElse(null)?.toBakedCountDialog(this)
            }
            KEY_NOTIF_SETTING -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    startActivitySafely(
                        settingsIntent.createChooserIntentIfDead(context),
                        wrongMessageRes = R.string.no_action_found
                    )
                } else {
                    val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .setData("package:${context.packageName}".toUri())
                    startActivitySafely(
                        settingsIntent.createChooserIntentIfDead(context),
                        wrongMessageRes = R.string.no_action_found
                    )
                }
            }
            KEY_AUDIO_VOLUME -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startActivitySafely(Intent(Settings.Panel.ACTION_VOLUME))
                }
            }
            KEY_RATE -> {
                startActivitySafely(
                    IntentHelper.appStorePage(context),
                    wrongMessageRes = R.string.no_action_found
                )
            }
            KEY_RECOMMEND -> {
                startActivity(
                    IntentHelper.share(
                        context = context,
                        message = "${getString(R.string.share_content)}\n${flavorData.appDownloadLink}"
                    )
                )
            }
            KEY_ABOUT -> {
                NavHostFragment.findNavController(this)
                    .subLevelNavigate(R.id.dest_about)
            }
            else -> return false
        }
        return true
    }

    private fun refresh() {
        val sharedPreferences = preferenceManager.sharedPreferences

        findPreference<Preference>(KEY_DARK_THEME)?.run {
            onPreferenceClickListener = this@SettingsFragment
            summary = getDarkThemeSummary()
        }
        findPreference<Preference>(KEY_EDIT_LAYOUT)?.onPreferenceClickListener = this
        findPreference<Preference>(KEY_THEME)?.onPreferenceClickListener = this

        findPreference<ListPreference>(KEY_SCREEN)?.let {
            it.onPreferenceChangeListener = this
            refreshBrightnessTime(it.value)
        }

        findPreference<Preference>(KEY_TWEAK_TIME)?.run {
            onPreferenceClickListener = this@SettingsFragment
            updateTweakTimeSummary()
        }
        findPreference<Preference>(KEY_FLOATING_WINDOW_PIP)?.onPreferenceClickListener = this

        findPreference<Preference>(KEY_GROUP_REMINDER)?.isVisible =
            flavorData.supportAdvancedFeatures
        val prefBakedCount = findPreference<Preference>(KEY_BAKED_COUNT)?.apply {
            isVisible = flavorData.supportAdvancedFeatures
            onPreferenceClickListener = this@SettingsFragment
        }

        findPreference<Preference>(KEY_NOTIF_SETTING)?.onPreferenceClickListener = this

        findPreference<Preference>(KEY_AUDIO_VOLUME)?.run {
            onPreferenceClickListener = this@SettingsFragment
            isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        }

        findPreference<Preference>(KEY_RATE)?.onPreferenceClickListener = this
        findPreference<Preference>(KEY_RECOMMEND)?.onPreferenceClickListener = this
        findPreference<Preference>(KEY_ABOUT)?.onPreferenceClickListener = this

        sharedPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                KEY_BAKED_COUNT -> {
                    prefBakedCount?.setSummary(
                        if (sharedPreferences.getBoolean(key, false)) {
                            R.string.pref_reminder_baked_count_on
                        } else {
                            R.string.pref_reminder_baked_count_off
                        }
                    )
                }
            }
        }
        sharedPreferenceListener?.onSharedPreferenceChanged(sharedPreferences, KEY_BAKED_COUNT)
        sharedPreferences.registerOnSharedPreferenceChangeListener(sharedPreferenceListener)
    }

    private fun Preference.updateTweakTimeSummary() {
        setSummary(
            if (PreferenceData.TweakTimeSettings(requireContext()).hasSlot) R.string.pref_tweak_time_on
            else R.string.pref_tweak_time_off
        )
    }

    private fun getDarkThemeSummary(): CharSequence {
        val context = requireContext()
        val darkTheme = DarkTheme(context)
        return when (darkTheme.darkThemeValue) {
            DarkTheme.DARK_THEME_MANUAL -> {
                var result = context.getText(R.string.dark_theme_manual)
                if (darkTheme.scheduleEnabled) {
                    val range = darkTheme.scheduleRange
                    result = "$result ${
                        "%s %s - %s".format(
                            context.getText(R.string.dark_theme_scheduled),
                            TimeUtils.formattedTodayTime(
                                context = context,
                                hour = range.fromHour,
                                minute = range.fromMinute
                            ),
                            TimeUtils.formattedTodayTime(
                                context = context,
                                hour = range.toHour,
                                minute = range.toMinute
                            )
                        )
                    }"
                }
                result
            }
            DarkTheme.DARK_THEME_SYSTEM_DEFAULT ->
                context.getText(R.string.dark_theme_system_default)
            DarkTheme.DARK_THEME_BATTERY_SAVER ->
                context.getText(R.string.dark_theme_battery_saver)
            else -> context.getString(R.string.unknown)
        }
    }

    private fun refreshBrightnessTime(screenValue: String?) {
        findPreference<Preference>(KEY_SCREEN_TIMING)?.isVisible =
            screenValue != null && screenValue != getString(R.string.pref_screen_value_default)
    }

    override fun onPause() {
        super.onPause()
        if (sharedPreferenceListener != null) {
            preferenceManager.sharedPreferences
                .unregisterOnSharedPreferenceChangeListener(sharedPreferenceListener)
            sharedPreferenceListener = null
        }
    }
}

private const val KEY_DARK_THEME = DarkTheme.PREF_DARK_THEME
private const val KEY_EDIT_LAYOUT = "key_edit_layout"
private const val KEY_THEME = "key_theme"
private const val KEY_APP_LANGUAGE = PreferenceData.KEY_APP_LANGUAGE

private const val KEY_SCREEN = PreferenceData.KEY_SCREEN
private const val KEY_SCREEN_TIMING = PreferenceData.KEY_SCREEN_TIMING

// private const val KEY_NOTIFIER_PLUS = PreferenceData.KEY_NOTIFIER_PLUS
private const val KEY_TWEAK_TIME = "key_tweak_time"
private const val KEY_FLOATING_WINDOW_PIP = "key_floating_window_pip"

private const val KEY_GROUP_REMINDER = "pref_group_reminder"

private const val KEY_BAKED_COUNT = PreferenceData.PREF_BAKED_COUNT

// private const val KEY_PHONE_CALL = PreferenceData.KEY_PHONE_CALL

// private const val KEY_WEEK_START = PreferenceData.KEY_WEEK_START

private const val KEY_NOTIF_SETTING = "key_notif_setting"

private const val KEY_AUDIO_VOLUME = "key_audio_volume"

private const val KEY_RATE = "key_rate"
private const val KEY_RECOMMEND = "key_recommend"
private const val KEY_ABOUT = "key_about"

private const val EXTRA_TO_PREF = "extra_to_pref"
