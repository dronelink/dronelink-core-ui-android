package com.dronelink.core.ui.widget

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dronelink.core.Dronelink
import com.dronelink.core.ui.R
import com.dronelink.core.ui.databinding.WidgetBatteryBinding
import com.dronelink.core.ui.delegates.BindFragment

class BatteryWidget(onBatteryWidgetCreated: (() -> Unit)? = null) : UpdatableWidget(onBatteryWidgetCreated) {
    private var defaultColor: ColorStateList = ColorStateList.valueOf(Color.parseColor("#ffffff"))
    var normalColor: ColorStateList = ColorStateList.valueOf(Color.parseColor("#00e676"))
    var lowColor: ColorStateList = ColorStateList.valueOf(Color.parseColor("#ff1744"))
    val binding: WidgetBatteryBinding by BindFragment(R.layout.widget_battery)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return binding.root
    }

    override fun update() {
        super.update()

        val batteryPercent = session?.state?.value?.batteryPercent

        binding.run {
            if (batteryPercent == null) {
                levelTextView.text = getString(R.string.na)
                iconImageView.imageTintList = defaultColor
                return
            }

            levelTextView.text = Dronelink.getInstance().format("percent", batteryPercent, "")

            val lowBatteryThreshold = session?.state?.value?.lowBatteryThreshold
            iconImageView.imageTintList = if (batteryPercent <= (lowBatteryThreshold
                            ?: 0.0)) lowColor else normalColor
        }
    }
}