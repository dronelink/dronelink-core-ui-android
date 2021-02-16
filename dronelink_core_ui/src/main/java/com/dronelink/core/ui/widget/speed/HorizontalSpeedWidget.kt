package com.dronelink.core.ui.widget.speed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dronelink.core.Dronelink
import com.dronelink.core.ui.R
import com.dronelink.core.ui.databinding.SpeedWidgetBinding
import com.dronelink.core.ui.delegates.BindFragment
import com.dronelink.core.ui.widget.UpdatableWidget

class HorizontalSpeedWidget : UpdatableWidget() {
    val binding: SpeedWidgetBinding by BindFragment(R.layout.speed_widget)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding.run {
            metricText = getString(R.string.horizontalspeedwidget_hs)
            valueText = getString(R.string.empty)
        }

        return binding.root
    }

    override fun update() {
        super.update()
        session?.state?.value?.run {
            binding.valueText = Dronelink.getInstance().format("velocityHorizontal", horizontalSpeed, getString(R.string.na))
        }
    }
}