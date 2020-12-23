package com.dronelink.core.ui.widget

import androidx.constraintlayout.widget.ConstraintLayout
import com.dronelink.core.ui.R

class UplinkWidget: SignalWidget() {

    override fun onCreateView(constraintLayout: ConstraintLayout) {
        iconImageView.setImageResource(R.drawable.remote_controller_icon)
    }

    override fun update() {
        super.update()
        updateSignal(session?.state?.value?.uplinkSignalStrength ?: 0.0)
    }

}