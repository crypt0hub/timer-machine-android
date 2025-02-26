package xyz.aprildown.timer.app.timer.one.step

import android.graphics.Color
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.fastadapter.items.AbstractItem
import xyz.aprildown.timer.app.base.data.PreferenceData.getTypeColor
import xyz.aprildown.timer.app.base.utils.produceTime
import xyz.aprildown.timer.app.timer.one.R
import xyz.aprildown.timer.app.timer.one.databinding.ItemStepStepBinding
import xyz.aprildown.timer.domain.entities.StepEntity
import xyz.aprildown.tools.helper.attachToView
import xyz.aprildown.tools.helper.gone
import xyz.aprildown.tools.helper.onLongClick
import xyz.aprildown.tools.helper.toColorStateList

internal class VisibleStep(
    val step: StepEntity.Step,
    private val number: Int,
    id: Long,
    private val currentPositionCallback: CurrentPositionCallback,
    private val stepLongClickListener: OnStepLongClickListener
) : AbstractItem<VisibleStep.ViewHolder>() {

    override val layoutRes: Int = R.layout.item_step_step
    override val type: Int = R.id.type_step_step
    override fun getViewHolder(v: View): ViewHolder = ViewHolder(v)
    override var identifier: Long = id

    override fun bindView(holder: ViewHolder, payloads: List<Any>) {
        super.bindView(holder, payloads)

        holder.run {
            val context = binding.root.context
            GestureDetectorCompat(
                binding.root.context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onLongPress(e: MotionEvent?) {
                        stepLongClickListener.onStepLongClick(this@VisibleStep)
                    }

                    override fun onDoubleTap(e: MotionEvent?): Boolean {
                        stepLongClickListener.onStepDoubleTap(this@VisibleStep)
                        return true
                    }
                }
            ).attachToView(binding.viewClickArea)

            binding.viewTimeClickArea.onLongClick {
                stepLongClickListener.onStepTimeLongClick(this@VisibleStep)
            }

            val isSelected = currentPositionCallback.currentPosition == bindingAdapterPosition

            if (isSelected) {
                binding.root.setBackgroundResource(R.color.visible_step_background_color_selected)
                TextViewCompat.setTextAppearance(
                    binding.textTitle,
                    R.style.TextAppearance_Stepper_Selected
                )
                TextViewCompat.setTextAppearance(
                    binding.textTime,
                    R.style.TextAppearance_Stepper_Selected
                )
                binding.layoutBehaviour.isVisible = step.behaviour.isNotEmpty()
            } else {
                binding.root.setBackgroundColor(Color.TRANSPARENT)
                TextViewCompat.setTextAppearance(
                    binding.textTitle,
                    R.style.TextAppearance_Stepper_NonSelected
                )
                TextViewCompat.setTextAppearance(
                    binding.textTime,
                    R.style.TextAppearance_Stepper_NonSelected
                )
                binding.layoutBehaviour.gone()
            }

            val typeColor = step.type.getTypeColor(context)
            ImageViewCompat.setImageTintList(
                binding.imageIndicatorBg,
                typeColor.toColorStateList()
            )
            binding.textNumber.text = number.toString()
            binding.textTitle.text = step.label
            binding.textTime.text = step.length.produceTime()
            binding.layoutBehaviour.run {
                setBehaviours(step.behaviour)
                setEnabledColor(typeColor)
            }
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val binding = ItemStepStepBinding.bind(view)
    }
}
