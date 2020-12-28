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

open class SignalWidget: UpdatableWidget() {
    private var _iconImageView: ImageView? = null
    val iconImageView get() = _iconImageView!!
    private var _levelImageView: ImageView? = null
    val levelImageView get() = _levelImageView!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val constraintLayout = ConstraintLayout(requireContext())
        constraintLayout.id = View.generateViewId()
        constraintLayout.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)

        _iconImageView = ImageView(requireContext())
        _levelImageView = ImageView(requireContext())

        iconImageView.id = View.generateViewId()
        levelImageView.id = View.generateViewId()

        constraintLayout.addView(iconImageView)
        constraintLayout.addView(levelImageView)

        iconImageView.adjustViewBounds = true
        levelImageView.adjustViewBounds = true

        iconImageView.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, 0)

        levelImageView.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, 0)

        val set = ConstraintSet()
        set.clone(constraintLayout)

        set.connect(iconImageView.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP, requireContext().dpToPx(6))
        set.connect(iconImageView.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM, requireContext().dpToPx(6))

        set.connect(levelImageView.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP, requireContext().dpToPx(6))
        set.connect(levelImageView.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM, requireContext().dpToPx(6))

        set.connect(iconImageView.id, ConstraintSet.START, constraintLayout.id, ConstraintSet.START)
        set.connect(iconImageView.id, ConstraintSet.END, levelImageView.id, ConstraintSet.START, requireContext().dpToPx(2))
        set.connect(levelImageView.id, ConstraintSet.START, iconImageView.id, ConstraintSet.END, requireContext().dpToPx(2))
        set.connect(levelImageView.id, ConstraintSet.END, levelImageView.id, ConstraintSet.END)
        set.applyTo(constraintLayout)

        onCreateView(constraintLayout)

        return constraintLayout
    }

    fun updateSignal(signalValue: Double) {
        when {
            signalValue == 0.0 -> levelImageView.setImageResource(R.drawable.signal_level_0_icon)
            signalValue <= 0.2 -> levelImageView.setImageResource(R.drawable.signal_level_1_icon)
            signalValue <= 0.4 -> levelImageView.setImageResource(R.drawable.signal_level_2_icon)
            signalValue <= 0.6 -> levelImageView.setImageResource(R.drawable.signal_level_3_icon)
            signalValue <= 0.8 -> levelImageView.setImageResource(R.drawable.signal_level_4_icon)
            else -> levelImageView.setImageResource(R.drawable.signal_level_5_icon)
        }
    }

    open fun onCreateView(constraintLayout: ConstraintLayout) {}
}