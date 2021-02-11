package com.dronelink.core.ui.widget.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dronelink.core.ui.R
import com.dronelink.core.ui.R.layout
import com.dronelink.core.ui.databinding.WidgetCameraSettingsBinding
import com.dronelink.core.ui.delegates.BindFragment
import com.dronelink.core.ui.util.enumToString
import com.dronelink.core.ui.widget.UpdatableWidget

class ShutterWidget : UpdatableWidget() {
    private val binding: WidgetCameraSettingsBinding by BindFragment(layout.widget_camera_settings)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding.run {
            titleText = getString(R.string.shutter)
            valueText = getString(R.string.na)
        }
        return binding.root
    }

    override fun update() {
        super.update()
        session?.let {
            binding.valueText =
                it.getCameraState(0)?.value?.shutterSpeed?.enumToString() ?: getString(R.string.na)
        }
    }
}