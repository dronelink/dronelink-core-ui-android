package com.dronelink.core.ui.widget.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dronelink.core.ui.R
import com.dronelink.core.ui.R.layout
import com.dronelink.core.ui.databinding.CameraSettingWidgetBinding
import com.dronelink.core.ui.delegates.BindFragment
import com.dronelink.core.ui.util.enumToString
import com.dronelink.core.ui.widget.UpdatableWidget

class CameraShutterWidget : UpdatableWidget() {
    val binding: CameraSettingWidgetBinding by BindFragment(layout.camera_setting_widget)

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding.run {
            titleText = getString(R.string.camerashutterwidget_shutter)
            valueText = getString(R.string.na)
        }
        return binding.root
    }

    override fun update() {
        super.update()
        session?.let {
            binding.valueText =
                    it.getCameraState(0)?.value?.shutterSpeed?.enumToString(gson)
                            ?: getString(R.string.na)
        }
    }
}