package com.dronelink.core.ui.widget

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.dronelink.core.ui.R

class GPSWidget(onWidgetAttached: (() -> Unit)? = null) : SignalWidget(onWidgetAttached) {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding.run {
            iconImageView.setImageResource(R.drawable.gps_icon)
            ImageViewCompat.setImageTintList(iconImageView, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white)))
        }

        return binding.root
    }

    override fun update() {
        super.update()

        val satellites = session?.state?.value?.gpsSatellites
        val strength = session?.state?.value?.gpsSignalStrength
        binding.run {
            if (satellites == null || strength == null) {
                levelImageView.setImageResource(R.drawable.signal_level_0_icon)
                return
            }

            when {
                strength == 0 -> levelImageView.setImageResource(R.drawable.signal_level_0_icon)
                strength <= 0.2 -> levelImageView.setImageResource(R.drawable.signal_level_1_icon)
                strength <= 0.4 -> levelImageView.setImageResource(R.drawable.signal_level_2_icon)
                strength <= 0.6 -> levelImageView.setImageResource(R.drawable.signal_level_3_icon)
                strength <= 0.8 -> levelImageView.setImageResource(R.drawable.signal_level_4_icon)
                else -> levelImageView.setImageResource(R.drawable.signal_level_5_icon)
            }
        }
    }
}