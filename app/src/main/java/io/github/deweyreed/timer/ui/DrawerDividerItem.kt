package io.github.deweyreed.timer.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.mikepenz.materialdrawer.model.AbstractDrawerItem
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import io.github.deweyreed.timer.R

/**
 * [DividerDrawerItem]
 */
internal class DrawerDividerItem :
    AbstractDrawerItem<DrawerDividerItem, DrawerDividerItem.ViewHolder>() {

    override val layoutRes: Int = R.layout.divider
    override val type: Int = R.id.material_drawer_item_divider
    override fun getViewHolder(v: View): ViewHolder = ViewHolder(v)

    override fun bindView(holder: ViewHolder, payloads: List<Any>) {
        super.bindView(holder, payloads)
        holder.divider.run {
            isClickable = false
            isEnabled = false
            minimumHeight = 1
            ViewCompat.setImportantForAccessibility(
                this,
                ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO
            )
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val divider: View = view
    }
}
