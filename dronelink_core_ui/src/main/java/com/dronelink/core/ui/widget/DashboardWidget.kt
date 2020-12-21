package com.dronelink.core.ui.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.dronelink.core.ui.R
import com.dronelink.core.ui.util.dpToPx

class DashboardWidget : Widget() {

    var appLogo: Button? = null

    var statusLabelWidget: StatusLabelWidget? = null
    var statusGradientWidget: StatusGradientWidget? = null
    var flyingModeWidget: FlyingModeWidget? = null
    var batteryWidget: BatteryWidget? = null
    var gpsSignalWidget: GPSSignalWidget? = null
    var downlinkWidget: DownlinkWidget? = null
    var uplinkWidget: UplinkWidget? = null

    var cameraFeedWidget: Widget? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard_widget, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusGradientWidget = refreshWidget(StatusGradientWidget(), R.id.statusGradientWidgetContainer) as? StatusGradientWidget
        addViews(view)
        flyingModeWidget = refreshWidget(FlyingModeWidget(), R.id.flyingModeWidgetContainer) as? FlyingModeWidget
        statusLabelWidget = refreshWidget(StatusLabelWidget(), R.id.statusLabelWidgetContainer) as? StatusLabelWidget
        arrangeStatusLabelWidget(view)
        cameraFeedWidget = refreshWidget(widgetFactory?.createCameraFeedWidget(true), R.id.cameraFeedWidgetContainer)
        batteryWidget = refreshWidget(BatteryWidget(), R.id.batteryWidgetContainer) as? BatteryWidget
        gpsSignalWidget = refreshWidget(GPSSignalWidget(), R.id.gpsSignalWidgetContainer) as? GPSSignalWidget
        downlinkWidget = refreshWidget(DownlinkWidget(), R.id.downlinkWidgetContainer) as? DownlinkWidget
        uplinkWidget = refreshWidget(UplinkWidget(), R.id.uplinkWidgetContainer) as? UplinkWidget

    }

    private fun addViews(view: View) {
        // add logo button
        createLogoButtonView(view)
    }

    private fun createLogoButtonView(view: View) {
        val constraintLayout = view.findViewById<ConstraintLayout>(R.id.containerView)
        appLogo = Button(requireContext())
        appLogo?.id = View.generateViewId()
        appLogo?.layoutParams = ConstraintLayout.LayoutParams(requireContext().dpToPx(17), requireContext().dpToPx(20))
        appLogo?.background = ContextCompat.getDrawable(requireContext(), R.drawable.dronelink_logo)

        constraintLayout.addView(appLogo)

        val set = ConstraintSet()
        set.clone(constraintLayout)
        set.connect(appLogo?.id ?: 0, ConstraintSet.TOP, statusGradientWidget?.id ?: 0, ConstraintSet.TOP)
        set.connect(appLogo?.id ?: 0, ConstraintSet.BOTTOM, statusGradientWidget?.id ?: 0, ConstraintSet.BOTTOM)
        set.connect(appLogo?.id ?: 0, ConstraintSet.START, statusGradientWidget?.id ?: 0, ConstraintSet.START, requireContext().dpToPx(16))
        set.applyTo(constraintLayout)
    }

    private fun arrangeStatusLabelWidget(view: View) {
        val constraintLayout = view.findViewById<ConstraintLayout>(R.id.containerView)

        val set = ConstraintSet()
        set.clone(constraintLayout)
        set.clear(statusLabelWidget?.id ?: 0, ConstraintSet.START)
        set.connect(statusLabelWidget?.id ?: 0, ConstraintSet.START, appLogo?.id ?: 0, ConstraintSet.END, requireContext().dpToPx(16))
        set.connect(statusLabelWidget?.id ?: 0, ConstraintSet.END, flyingModeWidget?.id ?: 0, ConstraintSet.START, requireContext().dpToPx(16))
        set.applyTo(constraintLayout)
    }

    private fun refreshWidget(widget: Widget?, containerId: Int): Widget? {
        widget?.let {
            childFragmentManager.beginTransaction()
                .replace(containerId, it)
                .commit()
        }
        return widget
    }


}