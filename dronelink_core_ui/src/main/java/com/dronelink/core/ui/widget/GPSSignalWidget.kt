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

class GPSSignalWidget: UpdatableWidget() {

    private var _gpsImageView: ImageView? = null
    val gpsImageView get() = _gpsImageView!!
    private var _gpsSignalLevelImageView: ImageView? = null
    val gpsSignalLevelImageView get() = _gpsSignalLevelImageView!!
    private var _gpsSignalLevelTextView: TextView? = null
    val gpsSignalLevelTextView get() = _gpsSignalLevelTextView!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val constraintLayout = ConstraintLayout(requireContext())
        constraintLayout.id = View.generateViewId()
        constraintLayout.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)

        _gpsImageView = ImageView(requireContext())
        _gpsSignalLevelImageView = ImageView(requireContext())
        _gpsSignalLevelTextView = TextView(requireContext())

        gpsImageView.id = View.generateViewId()
        gpsSignalLevelImageView.id = View.generateViewId()
        gpsSignalLevelTextView.id = View.generateViewId()

        constraintLayout.addView(_gpsImageView)
        constraintLayout.addView(_gpsSignalLevelImageView)

        gpsImageView.setImageResource(R.drawable.gps_icon)
        gpsImageView.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, 0)
        gpsImageView.adjustViewBounds = true

        gpsSignalLevelImageView.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, 0)
        gpsSignalLevelImageView.adjustViewBounds = true

        val set = ConstraintSet()
        set.clone(constraintLayout)

        set.connect(gpsSignalLevelImageView.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP, requireContext().dpToPx(6))
        set.connect(gpsSignalLevelImageView.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM, requireContext().dpToPx(6))

        set.connect(gpsImageView.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP, requireContext().dpToPx(6))
        set.connect(gpsImageView.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM, requireContext().dpToPx(6))

        set.connect(gpsImageView.id, ConstraintSet.START, constraintLayout.id, ConstraintSet.START)
        set.connect(gpsImageView.id, ConstraintSet.END, gpsSignalLevelImageView.id, ConstraintSet.START, requireContext().dpToPx(2))
        set.connect(gpsSignalLevelImageView.id, ConstraintSet.START, gpsImageView.id, ConstraintSet.END, requireContext().dpToPx(2))
        set.connect(gpsSignalLevelImageView.id, ConstraintSet.END, gpsSignalLevelImageView.id, ConstraintSet.END)
        set.applyTo(constraintLayout)

        return constraintLayout
    }

    override fun update() {
        super.update()

        val satellites = session?.state?.value?.gpsSatellites
        val strength = session?.state?.value?.gpsSignalStrength

        if (satellites == null || strength == null) {
            gpsSignalLevelTextView.text = ""
            gpsSignalLevelTextView.visibility = View.GONE
            gpsSignalLevelImageView.setImageResource(R.drawable.signal_level_0_icon)
            return
        }

        gpsSignalLevelTextView.text = "$satellites"
        gpsSignalLevelTextView.visibility = View.VISIBLE

        when {
            strength == 0 -> gpsSignalLevelImageView.setImageResource(R.drawable.signal_level_0_icon)
            isLessThan(strength, 2) -> gpsSignalLevelImageView.setImageResource(R.drawable.signal_level_1_icon)
            isLessThan(strength, 4) -> gpsSignalLevelImageView.setImageResource(R.drawable.signal_level_2_icon)
            isLessThan(strength, 6) -> gpsSignalLevelImageView.setImageResource(R.drawable.signal_level_3_icon)
            isLessThan(strength, 8) -> gpsSignalLevelImageView.setImageResource(R.drawable.signal_level_4_icon)
            strength == 10 -> gpsSignalLevelImageView.setImageResource(R.drawable.signal_level_5_icon)
            else -> gpsSignalLevelImageView.setImageResource(R.drawable.signal_level_0_icon)
        }

    }

    private fun isLessThan(v1: Int, v2: Int) = v1 < v2


}