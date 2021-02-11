package com.dronelink.core.ui.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.FragmentContainerView
import com.dronelink.core.ui.R
import com.dronelink.core.ui.util.dpToPx
import com.dronelink.core.ui.widget.camera.EVWidget
import com.dronelink.core.ui.widget.camera.FStopWidget
import com.dronelink.core.ui.widget.camera.ISOWidget
import com.dronelink.core.ui.widget.camera.ShutterWidget
import com.dronelink.core.ui.widget.camera.WBWidget

class DashboardWidget : Widget() {

    private var appLogo: ImageView? = null

    private var statusLabelWidget: StatusLabelWidget? = null
    private var statusGradientWidget: StatusGradientWidget? = null
    private var flightModeWidget: FlightModeWidget? = null
    private var batteryWidget: BatteryWidget? = null
    private var gpsWidget: GPSWidget? = null
    private var downlinkWidget: DownlinkWidget? = null
    private var uplinkWidget: UplinkWidget? = null
    private var cameraFeedWidget: Widget? = null
    private var cameraCaptureWidget: CameraCaptureWidget? = null
    private var cameraModeWidget: CameraModeWidget? = null
    private var shutterWidget: ShutterWidget? = null
    private var fStopWidget: FStopWidget? = null
    private var evWidget: EVWidget? = null
    private var wbWidget: WBWidget? = null
    private var isoWidget: ISOWidget? = null

    private var cameraControlsView: ConstraintLayout? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard_widget, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        statusGradientWidget = refreshWidget(StatusGradientWidget(), R.id.statusGradientWidgetContainer) as? StatusGradientWidget
        addViews(view)

        flightModeWidget = refreshWidget(FlightModeWidget(), R.id.flightModeWidgetContainer) as? FlightModeWidget

        statusLabelWidget = refreshWidget(StatusLabelWidget(), R.id.statusLabelWidgetContainer) as? StatusLabelWidget
        arrangeStatusLabelWidget(view)

        cameraFeedWidget = refreshWidget(widgetFactory?.createCameraFeedWidget(true), R.id.cameraFeedWidgetContainer)
        batteryWidget = refreshWidget(BatteryWidget(), R.id.batteryWidgetContainer) as? BatteryWidget
        gpsWidget = refreshWidget(GPSWidget(), R.id.gpsWidgetContainer) as? GPSWidget
        downlinkWidget = refreshWidget(DownlinkWidget(), R.id.downlinkWidgetContainer) as? DownlinkWidget
        uplinkWidget = refreshWidget(UplinkWidget(), R.id.uplinkWidgetContainer) as? UplinkWidget
        shutterWidget = refreshWidget(ShutterWidget(), R.id.shutterWidgetContainer) as? ShutterWidget
        fStopWidget = refreshWidget(FStopWidget(), R.id.fStopWidgetContainer) as? FStopWidget
        evWidget = refreshWidget(EVWidget(), R.id.evWidgetContainer) as? EVWidget
        wbWidget = refreshWidget(WBWidget(), R.id.wbWidgetContainer) as? WBWidget
        isoWidget = refreshWidget(ISOWidget(), R.id.isoWidgetContainer) as? ISOWidget
    }

    private fun addViews(view: View) {
        createLogoButtonView(view)
        createCameraControlsView(view)
    }

    private fun createLogoButtonView(view: View) {
        val constraintLayout = view.findViewById<ConstraintLayout>(R.id.containerView)
        appLogo = ImageView(requireContext())
        appLogo?.id = View.generateViewId()
        appLogo?.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, 0)
        appLogo?.setImageResource(R.drawable.dronelink_logo)

        constraintLayout.addView(appLogo)

        val set = ConstraintSet()
        set.clone(constraintLayout)
        set.connect(appLogo?.id ?: 0, ConstraintSet.TOP, statusGradientWidget?.id ?: 0, ConstraintSet.TOP, requireContext().dpToPx(4))
        set.connect(appLogo?.id ?: 0, ConstraintSet.BOTTOM, statusGradientWidget?.id ?: 0, ConstraintSet.BOTTOM, requireContext().dpToPx(4))
        set.connect(appLogo?.id ?: 0, ConstraintSet.START, statusGradientWidget?.id ?: 0, ConstraintSet.START, requireContext().dpToPx(16))
        set.applyTo(constraintLayout)
    }

    private fun createCameraControlsView(view: View) {
        val constraintLayout = view.findViewById<ConstraintLayout>(R.id.containerView)
        cameraControlsView = ConstraintLayout(requireContext())
        cameraControlsView?.id = View.generateViewId()
        constraintLayout.addView(cameraControlsView)

        cameraControlsView?.setBackgroundResource(R.drawable.bg_camera_controls)
        val padding = requireContext().dpToPx(10)
        cameraControlsView?.setPadding(padding, padding, padding, padding )

        val parentSet = ConstraintSet()
        parentSet.clone(constraintLayout)
        parentSet.connect(cameraControlsView?.id ?: 0, ConstraintSet.TOP, constraintLayout?.id ?: 0, ConstraintSet.TOP)
        parentSet.connect(cameraControlsView?.id ?: 0, ConstraintSet.BOTTOM, constraintLayout?.id ?: 0, ConstraintSet.BOTTOM)
        parentSet.connect(cameraControlsView?.id ?: 0, ConstraintSet.END, constraintLayout?.id ?: 0, ConstraintSet.END, requireContext().dpToPx(16))
        parentSet.applyTo(constraintLayout)

        val cameraCaptureContainerView = FragmentContainerView(requireContext())
        cameraCaptureContainerView.id = View.generateViewId()
        cameraControlsView?.addView(cameraCaptureContainerView)

        val cameraModeContainerView = FragmentContainerView(requireContext())
        cameraModeContainerView.id = View.generateViewId()
        cameraControlsView?.addView(cameraModeContainerView)

        val cameraCaptureSet = ConstraintSet()
        cameraCaptureSet.clone(cameraControlsView)
        cameraCaptureSet.connect(cameraCaptureContainerView.id, ConstraintSet.TOP, cameraModeContainerView.id, ConstraintSet.BOTTOM)
        cameraCaptureSet.connect(cameraCaptureContainerView.id, ConstraintSet.BOTTOM, cameraControlsView?.id ?: 0, ConstraintSet.BOTTOM)
        cameraCaptureSet.connect(cameraCaptureContainerView.id , ConstraintSet.START, cameraControlsView?.id ?: 0, ConstraintSet.START)
        cameraCaptureSet.connect(cameraCaptureContainerView.id , ConstraintSet.END, cameraControlsView?.id ?: 0, ConstraintSet.END)
        cameraCaptureSet.applyTo(cameraControlsView)

        cameraCaptureWidget = refreshWidget(CameraCaptureWidget(), cameraCaptureContainerView.id) as? CameraCaptureWidget

        val cameraModeSet = ConstraintSet()
        cameraModeSet.clone(cameraControlsView)
        cameraModeSet.connect(cameraModeContainerView.id, ConstraintSet.TOP, cameraControlsView?.id ?: 0, ConstraintSet.TOP)
        cameraModeSet.connect(cameraModeContainerView.id, ConstraintSet.BOTTOM, cameraCaptureContainerView.id, ConstraintSet.TOP, requireContext().dpToPx(4))
        cameraModeSet.connect(cameraModeContainerView.id , ConstraintSet.START, cameraControlsView?.id ?: 0, ConstraintSet.START)
        cameraModeSet.connect(cameraModeContainerView.id , ConstraintSet.END, cameraControlsView?.id ?: 0, ConstraintSet.END)
        cameraModeSet.applyTo(cameraControlsView)

        cameraModeWidget = refreshWidget(CameraModeWidget(), cameraModeContainerView.id) as? CameraModeWidget
    }

    private fun arrangeStatusLabelWidget(view: View) {
        val constraintLayout = view.findViewById<ConstraintLayout>(R.id.containerView)

        val set = ConstraintSet()
        set.clone(constraintLayout)
        set.clear(statusLabelWidget?.id ?: 0, ConstraintSet.START)
        set.connect(statusLabelWidget?.id ?: 0, ConstraintSet.START, appLogo?.id ?: 0, ConstraintSet.END, requireContext().dpToPx(16))
        set.connect(statusLabelWidget?.id ?: 0, ConstraintSet.END, flightModeWidget?.id ?: 0, ConstraintSet.START, requireContext().dpToPx(16))
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