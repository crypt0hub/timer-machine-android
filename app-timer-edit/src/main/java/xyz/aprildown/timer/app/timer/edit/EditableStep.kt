package xyz.aprildown.timer.app.timer.edit

import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.annotation.VisibleForTesting
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import xyz.aprildown.timer.app.base.data.PreferenceData.getTypeColor
import xyz.aprildown.timer.app.base.utils.setTime
import xyz.aprildown.timer.component.key.RoundTextView
import xyz.aprildown.timer.component.key.behaviour.EditableBehaviourLayout
import xyz.aprildown.timer.domain.entities.BehaviourEntity
import xyz.aprildown.timer.domain.entities.StepType
import xyz.aprildown.tools.helper.gone
import xyz.aprildown.tools.helper.show
import xyz.aprildown.tools.helper.showActionAndMultiLine
import xyz.aprildown.tools.helper.toColorStateList

@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
class EditableStep(
    var label: String,
    var length: Long,
    var behaviour: List<BehaviourEntity>,
    val stepType: StepType = StepType.NORMAL,
    private val handler: Handler,
    var isInAGroup: Boolean = false
) : AbstractItem<EditableStep.ViewHolder>() {

    sealed class Event {
        object Length : Event()
        object Behaviour : Event()
        object InOutGroup : Event()
    }

    /**
     * Field setter are handled here whenever possible.
     * The position(bindingAdapterPosition) parameter is the global position
     */
    interface Handler {
        fun onStepNameChange(position: Int, newName: String)
        fun onLengthClick(view: View, position: Int)
        fun onAddBtnClick(view: View, position: Int)

        fun showBehaviourSettingsView(
            view: View,
            layout: EditableBehaviourLayout,
            current: BehaviourEntity,
            position: Int
        )

        fun onBehaviourAddedOrRemoved(position: Int, newBehaviours: List<BehaviourEntity>)
    }

    override val layoutRes: Int = R.layout.item_edit_step
    override val type: Int = R.id.type_step_step
    override fun getViewHolder(v: View): ViewHolder = ViewHolder(v, handler)

    class ViewHolder(
        view: View,
        private val handler: Handler
    ) : FastAdapter.ViewHolder<EditableStep>(view) {

        private val context = view.context
        private val startStepGroupIndicator: View =
            view.findViewById(R.id.viewStepGroupIndicatorStart)
        private val endStepGroupIndicator: View = view.findViewById(R.id.viewStepGroupIndicatorEnd)
        private val stepColor: ImageView = view.findViewById(R.id.colorStep)
        private val stepName: EditText = view.findViewById(R.id.editStepName)
        private val length: RoundTextView = view.findViewById(R.id.textStepLength)
        private val behaviour: EditableBehaviourLayout = view.findViewById(R.id.layoutBehaviour)
        private val addBtn: ImageButton = view.findViewById(R.id.btnStepAdd)

        private var stepNameTextChangeListener: TextWatcher? = null

        init {
            stepName.showActionAndMultiLine(EditorInfo.IME_ACTION_DONE)

            length.setOnClickListener {
                handler.onLengthClick(it, bindingAdapterPosition)
            }

            behaviour.setListener(object : EditableBehaviourLayout.Listener {
                override fun showBehaviourSettingsView(
                    view: View,
                    layout: EditableBehaviourLayout,
                    current: BehaviourEntity
                ) {
                    handler.showBehaviourSettingsView(view, layout, current, bindingAdapterPosition)
                }
            })
        }

        override fun bindView(item: EditableStep, payloads: List<Any>) {
            if (payloads.isEmpty()) {
                fullBind(item)
            } else {
                payloads.forEach {
                    when (it) {
                        is Event.Behaviour -> {
                            behaviour.setBehaviours(item.behaviour)
                        }
                        is Event.Length -> {
                            length.setTime(item.length)
                        }
                        is Event.InOutGroup -> {
                            val inAGroup = item.isInAGroup
                            startStepGroupIndicator.isVisible = inAGroup
                            endStepGroupIndicator.isVisible = inAGroup
                        }
                    }
                }
            }
        }

        private fun fullBind(item: EditableStep) {
            val color = item.stepType.getTypeColor(context)

            val inAGroup = item.isInAGroup
            startStepGroupIndicator.isVisible = inAGroup
            endStepGroupIndicator.isVisible = inAGroup

            ImageViewCompat.setImageTintList(stepColor, color.toColorStateList())

            stepName.run {
                stepNameTextChangeListener = object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int
                    ) = Unit

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int
                    ) = Unit

                    override fun afterTextChanged(s: Editable?) {
                        val newName = s.toString()
                        item.label = newName
                        this@ViewHolder.handler.onStepNameChange(bindingAdapterPosition, newName)
                    }
                }
                addTextChangedListener(stepNameTextChangeListener)
                setText(item.label)
            }

            length.setBgColor(color)
            length.setTime(item.length)

            if (item.stepType == StepType.END) {
                addBtn.gone()
            } else {
                addBtn.show()
                addBtn.imageTintList = ColorStateList.valueOf(color)
                addBtn.setOnClickListener {
                    handler.onAddBtnClick(it, bindingAdapterPosition)
                }
            }

            behaviour.setEnabledColor(color)
            behaviour.setBehaviours(item.behaviour)
            behaviour.setBehaviourAddedOrRemovedCallback {
                val newBehaviours = behaviour.getBehaviours()
                item.behaviour = newBehaviours
                handler.onBehaviourAddedOrRemoved(bindingAdapterPosition, newBehaviours)
            }
        }

        override fun unbindView(item: EditableStep) {
            stepName.removeTextChangedListener(stepNameTextChangeListener)
            stepNameTextChangeListener = null
        }
    }
}
