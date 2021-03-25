package com.dronelink.core.ui.widget

import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.dronelink.core.ui.R

class UplinkWidget(onUplinkWidgetCreated: (() -> Unit)? = null): SignalWidget(onUplinkWidgetCreated) {

    override fun onCreateView(view: View) {
        binding.iconImageView.setImageResource(R.drawable.remote_controller_icon)
    }

    override fun update() {
        super.update()
        updateSignal(session?.state?.value?.uplinkSignalStrength ?: 0.0)
    }
}