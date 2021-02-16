package com.dronelink.core.ui.widget.distance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dronelink.core.Dronelink
import com.dronelink.core.ui.R
import com.dronelink.core.ui.databinding.DistanceWidgetBinding
import com.dronelink.core.ui.delegates.BindFragment
import com.dronelink.core.ui.widget.UpdatableWidget

class DistanceHomeWidget : UpdatableWidget() {
    val binding: DistanceWidgetBinding by BindFragment(R.layout.distance_widget)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding.run {
            metricText = getString(R.string.distancehomewidget_d)
            valueText = getString(R.string.empty)
        }
        return binding.root
    }

    override fun update() {
        val state = session?.state?.value
        state?.run {
            val droneLocation = state.location
            val homeLocation = state.homeLocation
            if (homeLocation != null && droneLocation != null) {
                binding.valueText = Dronelink.getInstance().format("distance", homeLocation.distanceTo(droneLocation), getString(R.string.na))
            }
        }
    }
}