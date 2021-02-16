package com.dronelink.core.ui.widget.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dronelink.core.kernel.core.enums.CameraISO
import com.dronelink.core.ui.R
import com.dronelink.core.ui.R.layout
import com.dronelink.core.ui.databinding.CameraSettingWidgetBinding
import com.dronelink.core.ui.delegates.BindFragment
import com.dronelink.core.ui.util.enumToString
import com.dronelink.core.ui.widget.UpdatableWidget

class CameraISOWidget : UpdatableWidget() {
    val binding: CameraSettingWidgetBinding by BindFragment(layout.camera_setting_widget)

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding.run {
            titleText = getString(R.string.cameraisowidget_iso)
            valueText = getString(R.string.na)
        }
        return binding.root
    }

    override fun update() {
        super.update()
        session?.let {
            val value = it.getCameraState(0)?.value
            val customValue = (value?.isoSensitivity ?: 0).toString()
            val iso = if (value?.iso == CameraISO.FIXED || value?.iso == CameraISO.AUTO) customValue else value?.iso?.enumToString(gson)
            val titleResource = if (value?.iso == CameraISO.AUTO) R.string.cameraisowidget_iso_auto else R.string.cameraisowidget_iso
            binding.titleText = getString(titleResource)
            binding.valueText = iso ?: getString(R.string.na)
        }
    }
}