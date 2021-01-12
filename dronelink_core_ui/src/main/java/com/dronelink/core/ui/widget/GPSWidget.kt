package com.dronelink.core.ui.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.dronelink.core.ui.R
import com.dronelink.core.ui.util.dpToPx

class GPSWidget: UpdatableWidget() {

    private var _iconImageView: ImageView? = null
    val iconImageView get() = _iconImageView!!
    private var _signalLevelImageView: ImageView? = null
    val signalLevelImageView get() = _signalLevelImageView!!
    private var _signalLevelTextView: TextView? = null
    val signalLevelTextView get() = _signalLevelTextView!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val constraintLayout = ConstraintLayout(requireContext())
        constraintLayout.id = View.generateViewId()
        constraintLayout.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)

        _iconImageView = ImageView(requireContext())
        _signalLevelImageView = ImageView(requireContext())
        _signalLevelTextView = TextView(requireContext())

        iconImageView.id = View.generateViewId()
        signalLevelImageView.id = View.generateViewId()
        signalLevelTextView.id = View.generateViewId()

        constraintLayout.addView(_iconImageView)
        constraintLayout.addView(_signalLevelImageView)

        iconImageView.setImageResource(R.drawable.gps_icon)
        iconImageView.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, 0)
        iconImageView.adjustViewBounds = true

        signalLevelImageView.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, 0)
        signalLevelImageView.adjustViewBounds = true

        val set = ConstraintSet()
        set.clone(constraintLayout)

        set.connect(signalLevelImageView.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP, requireContext().dpToPx(6))
        set.connect(signalLevelImageView.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM, requireContext().dpToPx(6))

        set.connect(iconImageView.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP, requireContext().dpToPx(6))
        set.connect(iconImageView.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM, requireContext().dpToPx(6))

        set.connect(iconImageView.id, ConstraintSet.START, constraintLayout.id, ConstraintSet.START)
        set.connect(iconImageView.id, ConstraintSet.END, signalLevelImageView.id, ConstraintSet.START, requireContext().dpToPx(2))
        set.connect(signalLevelImageView.id, ConstraintSet.START, iconImageView.id, ConstraintSet.END, requireContext().dpToPx(2))
        set.connect(signalLevelImageView.id, ConstraintSet.END, signalLevelImageView.id, ConstraintSet.END)
        set.applyTo(constraintLayout)

        return constraintLayout
    }

    override fun update() {
        super.update()

        val satellites = session?.state?.value?.gpsSatellites
        val strength = session?.state?.value?.gpsSignalStrength

        if (satellites == null || strength == null) {
            signalLevelTextView.text = ""
            signalLevelTextView.visibility = View.INVISIBLE
            signalLevelImageView.setImageResource(R.drawable.signal_level_0_icon)
            return
        }

        signalLevelTextView.text = "$satellites"
        signalLevelTextView.visibility = View.VISIBLE

        when {
            strength == 0 -> signalLevelImageView.setImageResource(R.drawable.signal_level_0_icon)
            strength <= 0.2 -> signalLevelImageView.setImageResource(R.drawable.signal_level_1_icon)
            strength <= 0.4 -> signalLevelImageView.setImageResource(R.drawable.signal_level_2_icon)
            strength <= 0.6 -> signalLevelImageView.setImageResource(R.drawable.signal_level_3_icon)
            strength <= 0.8 -> signalLevelImageView.setImageResource(R.drawable.signal_level_4_icon)
            else -> signalLevelImageView.setImageResource(R.drawable.signal_level_5_icon)
        }
    }
}