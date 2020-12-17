package com.dronelink.core.ui.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.dronelink.core.ui.R

class DashboardWidget : Widget() {

    var statusLabelWidget: StatusLabelWidget? = null
    var statusGradientWidget: StatusGradientWidget? = null
    var cameraFeedWidget: Widget? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_dashboard_widget, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusLabelWidget = refreshWidget(StatusLabelWidget(), R.id.statusLabelWidgetContainer) as? StatusLabelWidget
        statusGradientWidget = refreshWidget(StatusGradientWidget(), R.id.statusGradientWidgetContainer) as? StatusGradientWidget

        cameraFeedWidget = refreshWidget(widgetFactory?.createCameraFeedWidget(true), R.id.cameraFeedWidgetContainer)

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