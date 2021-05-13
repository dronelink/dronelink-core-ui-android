package com.dronelink.core.ui.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dronelink.core.ui.R
import com.dronelink.core.ui.databinding.WidgetSignalBinding
import com.dronelink.core.ui.delegates.BindFragment

open class SignalWidget(onSignalWidgetCreated: (() -> Unit)? = null) : UpdatableWidget(onSignalWidgetCreated) {
    val binding: WidgetSignalBinding by BindFragment(R.layout.widget_signal)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        onCreateView(binding.root)
        return binding.root
    }

    fun updateSignal(signalValue: Double) {
        binding.run {
            when {
                signalValue == 0.0 -> levelImageView.setImageResource(R.drawable.signal_level_0_icon)
                signalValue <= 0.2 -> levelImageView.setImageResource(R.drawable.signal_level_1_icon)
                signalValue <= 0.4 -> levelImageView.setImageResource(R.drawable.signal_level_2_icon)
                signalValue <= 0.6 -> levelImageView.setImageResource(R.drawable.signal_level_3_icon)
                signalValue <= 0.8 -> levelImageView.setImageResource(R.drawable.signal_level_4_icon)
                else -> levelImageView.setImageResource(R.drawable.signal_level_5_icon)
            }
        }
    }

    open fun onCreateView(view: View) {}
}