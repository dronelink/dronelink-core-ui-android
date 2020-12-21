package com.dronelink.core.ui.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.dronelink.core.ui.R
import com.dronelink.core.ui.util.dpToPx

open class GenericSignalWidget: UpdatableWidget() {

    private var _iconImageView: ImageView? = null
    val iconImageView get() = _iconImageView!!
    private var _signalLevelImageView: ImageView? = null
    val signalLevelImageView get() = _signalLevelImageView!!


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val constraintLayout = ConstraintLayout(requireContext())
        constraintLayout.id = View.generateViewId()
        constraintLayout.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)

        _iconImageView = ImageView(requireContext())
        _signalLevelImageView = ImageView(requireContext())

        iconImageView.id = View.generateViewId()
        signalLevelImageView.id = View.generateViewId()

        constraintLayout.addView(iconImageView)
        constraintLayout.addView(signalLevelImageView)

        iconImageView.layoutParams = ConstraintLayout.LayoutParams(requireContext().dpToPx(15), requireContext().dpToPx(15))

        signalLevelImageView.layoutParams = ConstraintLayout.LayoutParams(requireContext().dpToPx(17), requireContext().dpToPx(15))

        val set = ConstraintSet()
        set.clone(constraintLayout)

        set.connect(iconImageView.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP)
        set.connect(iconImageView.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM)

        set.connect(signalLevelImageView.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP)
        set.connect(signalLevelImageView.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM)

        set.connect(iconImageView.id, ConstraintSet.START, constraintLayout.id, ConstraintSet.START)
        set.connect(iconImageView.id, ConstraintSet.END, signalLevelImageView.id, ConstraintSet.START, requireContext().dpToPx(2))
        set.connect(signalLevelImageView.id, ConstraintSet.START, iconImageView.id, ConstraintSet.END, requireContext().dpToPx(2))
        set.connect(signalLevelImageView.id, ConstraintSet.END, signalLevelImageView.id, ConstraintSet.END)
        set.applyTo(constraintLayout)

        onCreateView(constraintLayout)

        return constraintLayout
    }


    fun updateSignal(signalValue: Double) {
        when {
            signalValue == 0.0 -> signalLevelImageView.setImageResource(R.drawable.signal_level_0_icon)
            isLessThan(signalValue, 0.2) -> signalLevelImageView.setImageResource(R.drawable.signal_level_1_icon)
            isLessThan(signalValue, 0.4) -> signalLevelImageView.setImageResource(R.drawable.signal_level_2_icon)
            isLessThan(signalValue, 0.6) -> signalLevelImageView.setImageResource(R.drawable.signal_level_3_icon)
            isLessThan(signalValue, 0.8) -> signalLevelImageView.setImageResource(R.drawable.signal_level_4_icon)
            isLessThan(signalValue, 1.0) -> signalLevelImageView.setImageResource(R.drawable.signal_level_5_icon)
            else -> signalLevelImageView.setImageResource(R.drawable.signal_level_0_icon)
        }
    }

    private fun isLessThan(v1: Double, v2: Double) = v1 <= v2

    open fun onCreateView(constraintLayout: ConstraintLayout) { }

}