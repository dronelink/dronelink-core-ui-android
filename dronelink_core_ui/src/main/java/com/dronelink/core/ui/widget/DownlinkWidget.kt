package com.dronelink.core.ui.widget

import android.content.res.ColorStateList
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.dronelink.core.ui.R

class DownlinkWidget: SignalWidget() {

    override fun onCreateView(constraintLayout: ConstraintLayout) {
        iconImageView.setImageResource(R.drawable.hd_icon)
        ImageViewCompat.setImageTintList(iconImageView, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.white)))
    }

    override fun update() {
        super.update()
        updateSignal(session?.state?.value?.downlinkSignalStrength ?: 0.0)
    }

}