package xyz.aprildown.timer.app.timer.one

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewStubProxy
import com.github.zawadz88.materialpopupmenu.popupMenu
import dagger.hilt.android.AndroidEntryPoint
import xyz.aprildown.timer.app.base.data.PreferenceData
import xyz.aprildown.timer.app.base.data.PreferenceData.oneOneFourActions
import xyz.aprildown.timer.app.base.data.PreferenceData.oneOneTimeSize
import xyz.aprildown.timer.app.base.data.PreferenceData.oneOneUsingTimingBar
import xyz.aprildown.timer.app.base.data.PreferenceData.timePanels
import xyz.aprildown.timer.app.timer.one.databinding.FragmentOneBinding
import xyz.aprildown.timer.app.timer.one.layout.TweakTimeLayout
import xyz.aprildown.timer.app.timer.one.step.StepListView
import xyz.aprildown.timer.component.key.TimePanelLayout
import xyz.aprildown.timer.component.key.switchItem
import xyz.aprildown.timer.domain.entities.StepEntity
import xyz.aprildown.timer.presentation.stream.TimerIndex
import xyz.aprildown.tools.anko.dp
import xyz.aprildown.tools.anko.snackbar
import xyz.aprildown.tools.arch.observeEvent
import xyz.aprildown.tools.arch.observeNonNull

@AndroidEntryPoint
class OneFragment : BaseOneFragment<FragmentOneBinding>(), FiveActionsView.Listener {

    override fun createBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentOneBinding =
        DataBindingUtil.inflate(inflater, R.layout.fragment_one, container, false)

    override fun setUpViews(binding: FragmentOneBinding) {
        val context = binding.root.context
        binding.fiveActionsOne.run {
            setActionClickListener(this@OneFragment)
            setMainFabClickListener {
                actionStartPause()
            }
        }

        binding.layoutOneTweakTime.setCallback(
            requireActivity(),
            object : TweakTimeLayout.Callback {
                override fun onTweakTime(amount: Long) {
                    actionTweakTime(amount)
                }
            }
        )

        viewModel.messageEvent.observeEvent(viewLifecycleOwner) {
            when (it) {
                R.string.one_ui_locked -> {
                    binding.layoutOneRoot.snackbar(it, R.string.one_ui_unlock) {
                        actionLockUi(false)
                    }
                }
                else -> {
                    binding.layoutOneRoot.snackbar(it)
                }
            }
        }
        binding.textOneLoop.setOnClickListener {
            showPickLoopDialog(viewModel.timer.value?.loop ?: 1) {
                val currentIndex = viewModel.timerCurrentIndex.value ?: return@showPickLoopDialog
                actionJump(
                    when (currentIndex) {
                        is TimerIndex.Start, is TimerIndex.End -> {
                            val firstStep = viewModel.timer.value?.steps?.get(0)
                                ?: return@showPickLoopDialog
                            if (firstStep is StepEntity.Step) {
                                TimerIndex.Step(loopIndex = it, stepIndex = 0)
                            } else {
                                TimerIndex.Group(
                                    loopIndex = it, stepIndex = 0,
                                    groupStepIndex = TimerIndex.Step(0, 0)
                                )
                            }
                        }
                        is TimerIndex.Step -> currentIndex.copy(loopIndex = it)
                        is TimerIndex.Group -> currentIndex.copy(loopIndex = it)
                    }
                )
            }
        }
        binding.listOneSteps.run {
            setHasFixedSize(true)
            listener = object : StepListView.StepLongClickListener {
                override fun onJumpToStep(index: TimerIndex) {
                    actionJump(index)
                }

                override fun onGroupTitleClicked(defaultIndex: TimerIndex.Group) {
                    showPickLoopDialog(
                        (viewModel.timer.value?.steps?.get(defaultIndex.stepIndex)
                            as? StepEntity.Group)?.loop ?: 1
                    ) {
                        actionJump(
                            defaultIndex.copy(
                                groupStepIndex = defaultIndex.groupStepIndex.copy(loopIndex = it)
                            )
                        )
                    }
                }

                override fun onEditStep(index: TimerIndex) {
                    actionUpdateStep(index)
                }

                override fun onEditStepTime(index: TimerIndex) {
                    actionUpdateStepTime(index)
                }
            }
        }

        viewModel.timer.observe(viewLifecycleOwner) {
            setToolbarTitle(it.name)
        }
        viewModel.editTimerEvent.observeEvent(viewLifecycleOwner) {
            startActivity(appNavigator.getEditIntent(timerId = it))
        }
        viewModel.intentEvent.observeEvent(viewLifecycleOwner) {
            context.startService(it)
        }
    }

    override fun onActionClick(index: Int, view: View) {
        when (view.tag) {
            PreferenceData.ONE_LAYOUT_ONE_ACTION_STOP -> actionStop()
            PreferenceData.ONE_LAYOUT_ONE_ACTION_PREV -> actionPrevStep()
            PreferenceData.ONE_LAYOUT_ONE_ACTION_NEXT -> actionNextStep()
            PreferenceData.ONE_LAYOUT_ONE_ACTION_MORE -> showActionsMenu(view.context, view)
            PreferenceData.ONE_LAYOUT_ONE_ACTION_LOCK -> actionLockUi(
                !(viewModel.uiLocked.value ?: false)
            )
            PreferenceData.ONE_LAYOUT_ONE_ACTION_EDIT -> actionEditTimer()
        }
    }

    private fun showActionsMenu(context: Context, view: View) {
        popupMenu {
            section {
                switchItem {
                    label = context.getString(R.string.one_action_lock_ui)
                    onBind = {
                        it.isChecked = viewModel.uiLocked.value ?: false
                    }
                    onCheckedChange = { _, isChecked ->
                        actionLockUi(isChecked)
                    }
                }
                item {
                    label = context.getString(R.string.one_action_show_float)
                    callback = { actionFloating() }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
                ) {
                    item {
                        label = context.getString(R.string.one_action_pip)
                        icon = R.drawable.settings_pip
                        callback = { actionEnterPipMode() }
                    }
                }
            }
            section {
                item {
                    label = context.getString(R.string.one_action_edit_layout)
                    icon = R.drawable.settings_customize
                    callback = { actionToOneLayoutSetting() }
                }
                item {
                    label = context.getString(R.string.one_action_add_shortcut)
                    icon = R.drawable.ic_watch
                    callback = { actionCreateShortcut() }
                }
                item {
                    label = context.getString(R.string.one_action_edit_timer)
                    icon = R.drawable.ic_edit
                    callback = { actionEditTimer() }
                }
            }
        }.show(context, view)
    }

    override fun applySettings(binding: FragmentOneBinding) {
        val context = binding.root.context
        if (context.oneOneUsingTimingBar) {
            val mpb = binding.stubTimingBar.getSafeView<ProgressBar>()
            viewModel.timerCurrentTime.observe(viewLifecycleOwner) {
                val stepTime = viewModel.timerStepTime
                if (stepTime > 0) {
                    mpb.progress = (it.toDouble() / stepTime.toDouble() * 100).toInt()
                }
            }
        }

        binding.textOneTime.textSize = dp(context.oneOneTimeSize)

        context.timePanels.let {
            if (it.isNotEmpty()) {
                val timePanelLayout = binding.stubTimePanel.getSafeView<TimePanelLayout>()
                timePanelLayout.setPanels(it)
                setUpTimePanels(timePanelLayout, it)
            }
        }

        val actions = context.oneOneFourActions
        binding.fiveActionsOne.withActions(getFourActionsFromKeys(actions))
        if (actions.contains(PreferenceData.ONE_LAYOUT_ONE_ACTION_LOCK)) {
            viewModel.uiLocked.observe(viewLifecycleOwner) {
                if (it == true) {
                    binding.fiveActionsOne.changeAction(
                        PreferenceData.ONE_LAYOUT_ONE_ACTION_LOCK,
                        R.string.one_action_unlock_ui,
                        R.drawable.ic_locked
                    )
                } else {
                    binding.fiveActionsOne.changeAction(
                        PreferenceData.ONE_LAYOUT_ONE_ACTION_LOCK,
                        R.string.one_action_lock_ui,
                        R.drawable.ic_unlocked
                    )
                }
            }
        }
    }

    private fun setUpTimePanels(
        timePanelLayout: TimePanelLayout,
        timePanels: List<PreferenceData.TimePanel>
    ) {
        viewModel.timerElapsedTime.observeNonNull(viewLifecycleOwner) { elapsedTime: Long ->
            val timerTotalTime = viewModel.timerTotalTime.toDouble()
            timePanels.forEach { timePanel ->
                timePanelLayout.updateText(
                    timePanel, when (timePanel) {
                        PreferenceData.TimePanel.ELAPSED_TIME -> elapsedTime.toDouble()
                        PreferenceData.TimePanel.ELAPSED_PERCENT ->
                            elapsedTime / timerTotalTime * 100
                        PreferenceData.TimePanel.REMAINING_TIME ->
                            timerTotalTime - elapsedTime
                        PreferenceData.TimePanel.REMAINING_PERCENT ->
                            (timerTotalTime - elapsedTime) / timerTotalTime * 100
                        PreferenceData.TimePanel.STEP_END_TIME ->
                            System.currentTimeMillis() +
                                (viewModel.timerCurrentTime.value ?: 0L).toDouble()
                        PreferenceData.TimePanel.TIMER_END_TIME ->
                            System.currentTimeMillis() + timerTotalTime - elapsedTime
                    }
                )
            }
        }
    }

    companion object {
        fun getFourActionsFromKeys(keys: List<String>): List<FiveActionsView.Action> =
            keys.map {
                when (it) {
                    PreferenceData.ONE_LAYOUT_ONE_ACTION_STOP -> FiveActionsView.Action(
                        it, R.string.one_action_stop, R.drawable.ic_stop
                    )
                    PreferenceData.ONE_LAYOUT_ONE_ACTION_PREV -> FiveActionsView.Action(
                        it, R.string.one_action_prev, R.drawable.ic_arrow_up
                    )
                    PreferenceData.ONE_LAYOUT_ONE_ACTION_NEXT -> FiveActionsView.Action(
                        it, R.string.one_action_next, R.drawable.ic_arrow_down
                    )
                    PreferenceData.ONE_LAYOUT_ONE_ACTION_MORE -> FiveActionsView.Action(
                        it, R.string.one_action_more, R.drawable.ic_overflow
                    )
                    PreferenceData.ONE_LAYOUT_ONE_ACTION_LOCK -> FiveActionsView.Action(
                        it, R.string.one_action_lock_ui, R.drawable.ic_unlocked
                    )
                    PreferenceData.ONE_LAYOUT_ONE_ACTION_EDIT -> FiveActionsView.Action(
                        it, R.string.one_action_edit_timer, R.drawable.ic_edit
                    )
                    else -> throw IllegalStateException("Unknown action $it")
                }
            }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T : View> ViewStubProxy.getSafeView(): T =
    (if (isInflated) root else viewStub!!.inflate()) as T
