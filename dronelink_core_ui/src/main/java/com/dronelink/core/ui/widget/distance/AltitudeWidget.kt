package com.dronelink.core.ui.widget.distance

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dronelink.core.Dronelink
import com.dronelink.core.ui.R
import com.dronelink.core.ui.databinding.WidgetDistanceBinding
import com.dronelink.core.ui.delegates.BindFragment
import com.dronelink.core.ui.widget.UpdatableWidget

class AltitudeWidget : UpdatableWidget() {
    val binding: WidgetDistanceBinding by BindFragment(R.layout.widget_distance)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding.run {
            metricText = getString(R.string.altitudewidget_a)
            valueText = getString(R.string.empty)
        }
        return binding.root
    }

    override fun update() {
        val state = session?.state?.value
        state?.run {
            binding.valueText = Dronelink.getInstance().format("altitude", altitude, getString(R.string.na))
        }
    }
}