package io.github.deweyreed.timer.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.annotation.IdRes
import androidx.core.content.edit
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.contains
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mikepenz.materialdrawer.holder.ImageHolder
import com.mikepenz.materialdrawer.interfaces.OnCheckedChangeListener
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem
import com.mikepenz.materialdrawer.model.SwitchDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.iconRes
import com.mikepenz.materialdrawer.model.interfaces.nameRes
import com.mikepenz.materialdrawer.model.interfaces.selectedColorInt
import com.mikepenz.materialdrawer.util.addStickyFooterItem
import com.mikepenz.materialdrawer.util.removeAllStickyFooterItems
import com.mikepenz.materialdrawer.util.updateStickyFooterItem
import com.mikepenz.materialdrawer.widget.AccountHeaderView
import com.mikepenz.materialdrawer.widget.MaterialDrawerSliderView
import dagger.hilt.android.AndroidEntryPoint
import io.github.deweyreed.timer.R
import io.github.deweyreed.timer.databinding.ActivityMainBinding
import xyz.aprildown.timer.app.base.data.DarkTheme
import xyz.aprildown.timer.app.base.data.PreferenceData
import xyz.aprildown.timer.app.base.data.PreferenceData.storedAudioTypeValue
import xyz.aprildown.timer.app.base.ui.AppNavigator
import xyz.aprildown.timer.app.base.ui.BaseActivity
import xyz.aprildown.timer.app.base.ui.FlavorUiInjector
import xyz.aprildown.timer.app.base.ui.FlavorUiInjectorQualifier
import xyz.aprildown.timer.app.base.ui.MainCallback
import xyz.aprildown.timer.app.base.ui.newDynamicTheme
import xyz.aprildown.timer.app.base.utils.AppThemeUtils
import xyz.aprildown.timer.app.base.utils.NavigationUtils.createMainFragmentNavOptions
import xyz.aprildown.timer.app.base.utils.NavigationUtils.getCurrentFragment
import xyz.aprildown.timer.app.timer.one.OneActivity
import xyz.aprildown.timer.component.settings.DarkThemeDialog
import xyz.aprildown.timer.domain.usecases.home.TipManager
import xyz.aprildown.timer.domain.utils.AppConfig
import xyz.aprildown.timer.domain.utils.Constants
import xyz.aprildown.timer.workshop.ChangeLogDialog
import xyz.aprildown.timer.workshop.RateDialog
import xyz.aprildown.timer.workshop.reminder.AppReminder
import xyz.aprildown.tools.anko.longSnackbar
import xyz.aprildown.tools.anko.toast
import xyz.aprildown.tools.helper.IntentHelper
import xyz.aprildown.tools.helper.float
import xyz.aprildown.tools.helper.isDarkTheme
import xyz.aprildown.tools.helper.restartWithFading
import xyz.aprildown.tools.helper.startActivitySafely
import xyz.aprildown.tools.utils.ThemeColorUtils
import java.time.Instant
import java.util.Optional
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : BaseActivity(),
    MainCallback.ActivityCallback,
    NavController.OnDestinationChangedListener {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var sharedPreferences: SharedPreferences

    @Inject
    lateinit var appNavigator: AppNavigator

    @Inject
    @FlavorUiInjectorQualifier
    lateinit var flavorUiInjector: Optional<FlavorUiInjector>

    private val navController: NavController by lazy {
        (supportFragmentManager.findFragmentById(R.id.fragmentContainer) as NavHostFragment)
            .navController
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        // The value may be changed when our app is in the background.
        AppThemeUtils.configThemeForDark(this, isDark = resources.isDarkTheme)

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.mainRoot.toolbar)

        setUpViews()
        setUpDrawer()
        setUpNavigation()

        setUpReminders()
        setUpDebug()

        setUpAutoDark()
    }

    override fun recreate() {
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            restartWithFading(
                intent(
                    this@MainActivity,
                    openDrawer = binding.drawer.isOpen,
                    showAutoDarkMsg = intent?.getBooleanExtra(EXTRA_SHOW_AUTO_DARK_MSG, false)
                        ?: false,
                    destinationId = navController.currentDestination?.id ?: 0
                )
            )
        } else {
            adjustDrawerForAutoDark()
            super.recreate()
        }
    }

    override fun onBackPressed() {
        when {
            binding.drawer.isOpen -> binding.drawer.close()
            else -> super.onBackPressed()
        }
    }

    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        val currentDestId = destination.id

        var requireFab = false

        fun refreshMainUi(itemIdentifier: Long) {
            supportActionBar?.title = destination.label
            binding.slider.setSelectionFix(identifier = itemIdentifier)
            if (requireFab && actionFab.isOrWillBeHidden) {
                actionFab.show()
            } else if (!requireFab && actionFab.isOrWillBeShown) {
                actionFab.hide()
            }
        }

        binding.drawer.setDrawerLockMode(
            if (currentDestId in setOf(
                    R.id.dest_timer,
                    R.id.dest_scheduler,
                    R.id.dest_backup_restore,
                    R.id.dest_settings,
                    R.id.dest_help,
                )
            ) {
                DrawerLayout.LOCK_MODE_UNLOCKED
            } else {
                DrawerLayout.LOCK_MODE_LOCKED_CLOSED
            }
        )

        if (destination.parent?.id == R.id.dest_cloud_backup) {
            refreshMainUi(DRAWER_ID_BACKUP_RESTORE)
            return
        }

        when (currentDestId) {
            R.id.dest_timer -> {
                requireFab = true
                refreshMainUi(DRAWER_ID_TIMER)
            }
            R.id.dest_record -> refreshMainUi(DRAWER_ID_TIMER)

            R.id.dest_scheduler -> {
                requireFab = true
                refreshMainUi(DRAWER_ID_SCHEDULER)
            }
            R.id.dest_edit_scheduler -> refreshMainUi(DRAWER_ID_SCHEDULER)

            R.id.dest_backup_restore,
            R.id.dest_export,
            R.id.dest_import -> refreshMainUi(DRAWER_ID_BACKUP_RESTORE)

            R.id.dest_help,
            R.id.dest_whitelist -> refreshMainUi(DRAWER_ID_HELP)

            R.id.dest_settings,
            R.id.dest_settings_floating_window_pip,
            R.id.dest_theme,
            R.id.dest_one_layout,
            R.id.dest_about -> refreshMainUi(DRAWER_ID_SETTINGS)
        }
    }

    override val actionFab: FloatingActionButton get() = binding.mainRoot.fab
    override val snackbarView: View get() = binding.mainRoot.root

    override fun enterTimerScreen(itemView: View, id: Int) {
        startActivity(OneActivity.intent(this, id))
    }

    override fun enterEditScreen(timerId: Int, folderId: Long) {
        startActivity(appNavigator.getEditIntent(timerId = timerId, folderId = folderId))
    }

    override fun restartWithDestination(destinationId: Int) {
        restartWithFading(intent(this, destinationId = destinationId))
    }

    override fun recreateThemeItem() {
        binding.slider.updateStickyFooterItem(createThemeDrawerItem())
    }

    private fun setUpViews() {
        binding.mainRoot.fab.setOnClickListener {
            val fragment = supportFragmentManager.getCurrentFragment(R.id.fragmentContainer)
            if (fragment is MainCallback.FragmentCallback) {
                fragment.onFabClick(it)
            }
        }
        volumeControlStream = storedAudioTypeValue
        // Theme will do this for us.
        // drawerLayout.setStatusBarBackgroundColor(withDynamicTheme { colorStatusBar })
        binding.mainRoot.toolbar.setNavigationOnClickListener { binding.drawer.open() }
    }

    private fun navigateToMainDestination(@IdRes destinationId: Int, bundle: Bundle? = null) {
        navController.navigate(
            destinationId, bundle, createMainFragmentNavOptions(destinationId)
        )
    }

    // region Drawer

    private fun setUpDrawer() {
        val colorPrimary = newDynamicTheme.colorPrimary
        val selectedItemBackgroundColor = ThemeColorUtils.adjustAlpha(
            colorPrimary,
            float(R.dimen.material_drawer_selected_background_alpha)
        )
        val drawerItemTint = DrawerItemTint(this)
        val textColorTint = drawerItemTint.createTextColorTint()
        val iconTint = drawerItemTint.createIconTint()

        fun PrimaryDrawerItem.withCommonSettings() = apply {
            textColor = textColorTint
            isIconTinted = true
            iconColor = iconTint
            selectedColorInt = selectedItemBackgroundColor
        }

        AccountHeaderView(this).run {
            attachToSliderView(binding.slider)
            headerBackground = ImageHolder(R.drawable.ic_launcher_background)
            selectionListEnabledForSingleProfile = false
            addProfiles(ProfileSettingDrawerItem())
        }

        binding.slider.itemAdapter.add(
            listOf(
                PrimaryDrawerItem().apply {
                    identifier = DRAWER_ID_TIMER
                    nameRes = R.string.main_action_timers
                    iconRes = R.drawable.ic_timer
                    withCommonSettings()
                },
                PrimaryDrawerItem().apply {
                    identifier = DRAWER_ID_SCHEDULER
                    nameRes = R.string.main_action_schedulers
                    iconRes = R.drawable.ic_scheduler
                    withCommonSettings()
                },
                PrimaryDrawerItem().apply {
                    identifier = DRAWER_ID_BACKUP_RESTORE
                    nameRes = R.string.main_action_backup
                    iconRes = R.drawable.ic_backup
                    withCommonSettings()
                },
                DrawerDividerItem(),
                PrimaryDrawerItem().apply {
                    identifier = DRAWER_ID_SETTINGS
                    nameRes = R.string.main_action_settings
                    iconRes = R.drawable.ic_settings
                    withCommonSettings()
                },
                PrimaryDrawerItem().apply {
                    identifier = DRAWER_ID_HELP
                    nameRes = R.string.main_action_help
                    iconRes = R.drawable.settings_help
                    withCommonSettings()
                }
            )
        )
        flavorUiInjector.orElse(null)?.let { flavorUiInjector ->
            binding.slider.itemAdapter.add(
                listOf(
                    DrawerDividerItem(),
                    PrimaryDrawerItem().apply {
                        identifier = DRAWER_ID_IN_APP_PURCHASES
                        nameRes = R.string.billing_iap
                        iconRes = R.drawable.settings_premium
                        withCommonSettings()
                        isSelectable = false
                        onDrawerItemClickListener = { _, _, _ ->
                            flavorUiInjector.showInAppPurchases(this@MainActivity)
                            true
                        }
                    }
                )
            )
        }

        binding.slider.addStickyFooterItem(createThemeDrawerItem())

        binding.slider.closeOnClick = false
        binding.slider.headerDivider = false
        binding.slider.tintStatusBar = true

        binding.slider.onDrawerItemClickListener = { _, item, _ ->
            val shouldClose = when (item.identifier) {
                DRAWER_ID_TIMER -> {
                    navigateToMainDestination(R.id.dest_timer)
                    true
                }
                DRAWER_ID_SCHEDULER -> {
                    navigateToMainDestination(R.id.dest_scheduler)
                    true
                }
                DRAWER_ID_BACKUP_RESTORE -> {
                    navigateToMainDestination(R.id.dest_backup_restore)
                    true
                }
                DRAWER_ID_SETTINGS -> {
                    navigateToMainDestination(R.id.dest_settings)
                    true
                }
                DRAWER_ID_HELP -> {
                    navigateToMainDestination(R.id.dest_help)
                    true
                }
                DRAWER_ID_THEME -> false
                else -> true
            }
            if (shouldClose && binding.drawer.isOpen) {
                binding.drawer.close()
            }
            true
        }

        if (intent?.getBooleanExtra(EXTRA_OPEN_DRAWER, false) == true) {
            intent.removeExtra(EXTRA_OPEN_DRAWER)
            binding.drawer.open()
        }

        binding.mainRoot.toolbar.setupWithNavController(
            navController,
            AppBarConfiguration.Builder(R.id.dest_timer)
                .setOpenableLayout(binding.drawer)
                .build()
        )
    }

    private fun createThemeDrawerItem(): IDrawerItem<*> {
        val drawerItemTint = DrawerItemTint(this)
        val textColorTint = drawerItemTint.createTextColorTint()
        val iconTint = drawerItemTint.createIconTint()
        return if (DarkTheme(this).darkThemeValue == DarkTheme.DARK_THEME_MANUAL) {
            SwitchDrawerItem().apply {
                identifier = DRAWER_ID_THEME
                iconRes = R.drawable.settings_day_night
                isIconTinted = true
                iconColor = iconTint
                nameRes = R.string.main_action_dark
                textColor = textColorTint
                isSelectable = false
                isChecked = resources.isDarkTheme
                onCheckedChangeListener = object : OnCheckedChangeListener {
                    override fun onCheckedChanged(
                        drawerItem: IDrawerItem<*>,
                        buttonView: CompoundButton,
                        isChecked: Boolean
                    ) {
                        updateManualDark(isChecked)
                    }
                }
            }
        } else {
            PrimaryDrawerItem().apply {
                identifier = DRAWER_ID_THEME
                iconRes = R.drawable.settings_day_night
                isIconTinted = true
                iconColor = iconTint
                nameRes = R.string.main_action_dark
                textColor = textColorTint
                isSelectable = false
                onDrawerItemClickListener = { _, _, _ ->
                    DarkThemeDialog(this@MainActivity).showSettingsDialog {
                        DarkTheme(this@MainActivity).applyAppCompatDelegate()
                        recreateThemeItem()
                    }
                    true
                }
            }
        }
    }

    // endregion Drawer

    private fun setUpNavigation() {
        flavorUiInjector.orElse(null)?.let { flavorUiInjector ->
            navController.graph.addAll(
                navController.navInflater.inflate(flavorUiInjector.cloudBackupNavGraphId)
            )
        }

        navController.addOnDestinationChangedListener(this)

        val destinationId = intent?.getIntExtra(EXTRA_DESTINATION_ID, 0)
        if (destinationId != null &&
            destinationId != 0 &&
            destinationId != navController.currentDestination?.id
        ) {
            intent?.removeExtra(EXTRA_DESTINATION_ID)
            if (navController.graph.contains(destinationId)) {
                navController.navigate(
                    destinationId,
                    null,
                    NavOptions.Builder()
                        .setPopEnterAnim(R.anim.close_enter)
                        .setPopExitAnim(R.anim.close_exit)
                        .build()
                )
            }
        }
    }

    private fun setUpAutoDark() {

        val prefs = getPreferences(MODE_PRIVATE)
        val keyLastStartTime = "last_start_time"
        val now = Instant.now().toEpochMilli()
        val lastStartTime = prefs.getLong(keyLastStartTime, now)
        prefs.edit { putLong(keyLastStartTime, now) }
        val darkTheme = DarkTheme(this)
        if (darkTheme.darkThemeValue == DarkTheme.DARK_THEME_MANUAL && darkTheme.scheduleEnabled) {

            val currentIsDark = resources.isDarkTheme

            if (darkTheme.calculateAutoDarkChange(
                    currentIsDark = currentIsDark,
                    nowMilli = now,
                    lastLaunchMilli = lastStartTime
                )
            ) {
                intent?.putExtra(EXTRA_SHOW_AUTO_DARK_MSG, true)
                lifecycleScope.launchWhenStarted {
                    updateManualDark(isDark = !currentIsDark)
                }
            } else {
                if (intent?.getBooleanExtra(EXTRA_SHOW_AUTO_DARK_MSG, false) == true) {
                    intent.removeExtra(EXTRA_SHOW_AUTO_DARK_MSG)
                    if (currentIsDark) {
                        snackbarView.longSnackbar(
                            R.string.dark_theme_scheduled_dark_now,
                            R.string.undo
                        ) {
                            updateManualDark(isDark = false)
                        }
                    } else {
                        snackbarView.longSnackbar(
                            R.string.dark_theme_scheduled_light_now,
                            R.string.undo
                        ) {
                            updateManualDark(isDark = true)
                        }
                    }
                }
            }
        }
    }

    private fun setUpReminders() {
        if (!AppConfig.showFirstTimeInfo) return

        // @return if the event is consumed
        fun showIntroOrUpdateMessages(): Boolean {
            val newVersion = "6.0.0"
            if (!TipManager.hasTutorialViewed(sharedPreferences)) {
                sharedPreferences.edit()
                    .putBoolean(newVersion, false)
                    .apply()
                return true
            } else {
                if (sharedPreferences.getBoolean(newVersion, true)) {
                    sharedPreferences.edit().putBoolean(newVersion, false)
                        .remove("5.8.0").remove("5.7.0")
                        .remove("5.6.0").remove("5.5.0").remove("5.4.1")
                        .remove("5.4.0").remove("5.3.0").remove("5.2.0")
                        .remove("5.1.0").remove("5.0.0").remove("4.7.0")
                        .remove("4.6.0").remove("4.5.1").remove("4.5.0")
                        .remove("4.4.0").remove("4.3.0").remove("4.2.0")
                        .remove("4.1.0").remove("4.0.0").remove("3.8.0")
                        .remove(PreferenceData.KEY_LABS_HALF_COUNT) // Last used in 3.8.0
                        .remove(PreferenceData.KEY_LABS_NOTIFICATION) // Last used in 3.8.0
                        .remove(PreferenceData.KEY_LABS_TIME_PANEL) // Last used in 3.8.0
                        .remove("theme_changed") // Last used in 3.8.0
                        .remove("pref_voice_dialog_mode") // Last used in 5.1.0
                        .apply()
                    ChangeLogDialog(this).show()
                    return true
                }
            }
            return false
        }

        fun showRateDialog(): Boolean {
            val rate = AppReminder(this, Constants.REMINDER_RATE)
            if (rate.getAgreeToShow()) {
                rate.setInstallDays(7)
                    .setLaunchTimes(7)
                    .setRemindInterval(7)
                    .monitor()
                    .ifConditionsAreMetThen {
                        showRateDialog(this, rate)
                        return true
                    }
            }
            return false
        }

        if (showIntroOrUpdateMessages()) return
        if (showRateDialog()) return
    }

    // region Other private helper methods

    private fun updateManualDark(isDark: Boolean) {
        adjustDrawerForAutoDark()
        val darkTheme = DarkTheme(this)
        darkTheme.manualOn = isDark
        darkTheme.applyAppCompatDelegate()
    }

    /**
     * During auto dark, if the system doesn't call our [recreate]'s restartWithFading,
     * The dark theme drawer item switch will go through [onSaveInstanceState] and recreate the
     * activity after auto dark, causing our auto dark fails.
     * I remove the switch to fix to problem although UI is ugly.
     */
    private fun adjustDrawerForAutoDark() {
        // After P or later, recreate is always called, but before, it may or may not be called.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            binding.slider.removeAllStickyFooterItems()
        }
    }

    // endregion Other private helper methods

    companion object {
        fun intent(
            context: Context,
            openDrawer: Boolean = false,
            showAutoDarkMsg: Boolean = false,
            @IdRes destinationId: Int = 0
        ): Intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_DRAWER, openDrawer)
            .putExtra(EXTRA_SHOW_AUTO_DARK_MSG, showAutoDarkMsg)
            .putExtra(EXTRA_DESTINATION_ID, destinationId)
    }
}

private const val DRAWER_ID_TIMER = 10L
private const val DRAWER_ID_SCHEDULER = 20L
private const val DRAWER_ID_BACKUP_RESTORE = 25L
private const val DRAWER_ID_SETTINGS = 50L
private const val DRAWER_ID_HELP = 55L
private const val DRAWER_ID_IN_APP_PURCHASES = 60L
private const val DRAWER_ID_THEME = 90L

private const val EXTRA_OPEN_DRAWER = "open_drawer"
private const val EXTRA_SHOW_AUTO_DARK_MSG = "show_auto_dark_msg"
private const val EXTRA_DESTINATION_ID = "destination_id"

/**
 * Original: https://github.com/mikepenz/MaterialDrawer/issues/2574
 * MaterialDrawer https://github.com/mikepenz/MaterialDrawer/releases/tag/v8.1.0 fixes the problem,
 * but the selection is wrong sometimes because of [MaterialDrawerSliderView.setSelection]'s implementation.
 * Therefore, I'll keep the fix for now.
 */
private fun MaterialDrawerSliderView.setSelectionFix(identifier: Long) {
    val fastAdapter = adapter
    for (index in 0 until fastAdapter.itemCount) {
        val item = fastAdapter.getItem(index) ?: continue
        val isTarget = item.identifier == identifier
        if (item.isSelected != isTarget) {
            item.isSelected = isTarget
            fastAdapter.notifyItemChanged(index)
        }
    }
}

private fun showRateDialog(context: Context, rate: AppReminder) {
    RateDialog(
        onRate = {
            rate.ok()
            context.toast(R.string.thanks)
            context.startActivitySafely(
                IntentHelper.appStorePage(context),
                wrongMessageRes = R.string.no_action_found
            )
        },
        onLater = {
            rate.later()
        },
        onNever = {
            rate.no()
        }
    ).show(context)
}
