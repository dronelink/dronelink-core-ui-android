package com.dronelink.core.ui.widget.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dronelink.core.Dronelink
import com.dronelink.core.kernel.core.enums.CameraWhiteBalancePreset
import com.dronelink.core.ui.R
import com.dronelink.core.ui.R.layout
import com.dronelink.core.ui.databinding.CameraSettingWidgetBinding
import com.dronelink.core.ui.delegates.BindFragment
import com.dronelink.core.ui.util.enumToString
import com.dronelink.core.ui.widget.UpdatableWidget

class CameraWhiteBalanceWidget(onWhiteBalanceCreated: (() -> Unit)? = null) : UpdatableWidget(onWhiteBalanceCreated) {
    val binding: CameraSettingWidgetBinding by BindFragment(layout.camera_setting_widget)

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding.run {
            titleText = getString(R.string.camerawhitebalance_wb)
            valueText = getString(R.string.na)
        }
        return binding.root
    }

    override fun update() {
        super.update()
        session?.let {
            val value = it.getCameraState(0)?.value
            val customValue = value?.whiteBalanceColorTemperature?.run {
                Dronelink.getInstance().format("absoluteTemperature", this * 100, null)
            }
            val whiteBalance = if (value?.whiteBalancePreset != CameraWhiteBalancePreset.CUSTOM) value?.whiteBalancePreset?.enumToString(gson) else customValue
            binding.valueText = whiteBalance ?: getString(R.string.na)
        }
    }
}