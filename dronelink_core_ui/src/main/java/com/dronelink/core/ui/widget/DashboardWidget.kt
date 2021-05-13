package com.dronelink.core.ui.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.dronelink.core.ui.R
import com.dronelink.core.ui.util.dpToPx
import com.dronelink.core.ui.widget.camera.*
import com.dronelink.core.ui.widget.distance.AltitudeWidget
import com.dronelink.core.ui.widget.distance.DistanceHomeWidget
import com.dronelink.core.ui.widget.speed.HorizontalSpeedWidget
import com.dronelink.core.ui.widget.speed.VerticalSpeedWidget

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
    private var cameraShutterWidget: CameraShutterWidget? = null
    private var cameraApertureWidget: CameraApertureWidget? = null
    private var cameraExposureCompensationWidget: CameraExposureCompensationWidget? = null
    private var cameraWhiteBalanceWidget: CameraWhiteBalanceWidget? = null
    private var cameraIsoWidget: CameraISOWidget? = null
    private var distanceFromHomeWidget: DistanceHomeWidget? = null
    private var altitudeWidget: AltitudeWidget? = null
    private var horizontalSpeedWidget: HorizontalSpeedWidget? = null
    private var verticalSpeedWidget: VerticalSpeedWidget? = null
    private var cameraMenuWidget: Widget? = null
    private var cameraSettingsExposureWidget: Widget? = null

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
        cameraShutterWidget = refreshWidget(CameraShutterWidget(), R.id.shutterWidgetContainer) as? CameraShutterWidget
        cameraApertureWidget = refreshWidget(CameraApertureWidget(), R.id.fStopWidgetContainer) as? CameraApertureWidget
        cameraExposureCompensationWidget = refreshWidget(CameraExposureCompensationWidget(), R.id.evWidgetContainer) as? CameraExposureCompensationWidget
        cameraWhiteBalanceWidget = refreshWidget(CameraWhiteBalanceWidget(), R.id.wbWidgetContainer) as? CameraWhiteBalanceWidget
        cameraIsoWidget = refreshWidget(CameraISOWidget(), R.id.isoWidgetContainer) as? CameraISOWidget

        distanceFromHomeWidget = refreshWidget(DistanceHomeWidget(), R.id.distanceHomeContainer) as? DistanceHomeWidget
        altitudeWidget = refreshWidget(AltitudeWidget(), R.id.altitudeContainer) as? AltitudeWidget
        horizontalSpeedWidget = refreshWidget(HorizontalSpeedWidget(), R.id.horizontalSpeedContainer) as? HorizontalSpeedWidget
        verticalSpeedWidget = refreshWidget(VerticalSpeedWidget(), R.id.verticalSpeedContainer) as? VerticalSpeedWidget
        cameraMenuWidget = refreshWidget(widgetFactory?.createCameraMenuWidget(true), R.id.cameraMenuContainer)
        cameraSettingsExposureWidget = refreshWidget(widgetFactory?.createCameraSettingsExposureWidget(true), R.id.cameraSettingsExposureContainer)
        cameraCaptureWidget = refreshWidget(CameraCaptureWidget(), R.id.cameraCaptureContainer) as? CameraCaptureWidget
        cameraModeWidget = refreshWidget(CameraModeWidget(), R.id.cameraModeContainer) as? CameraModeWidget

        setupMenuButtons(view)
    }

    private fun setupMenuButtons(view: View) {
        val cameraMenuButton = view.findViewById<Button>(R.id.btnCameraMenu)
        val cameraSettingsExposureButton = view.findViewById<ImageButton>(R.id.btnCameraSettingsExposure)
        cameraMenuButton.setOnClickListener {
            cameraSettingsExposureWidget?.view?.run {
                if (visibility == View.VISIBLE) {
                    visibility = View.GONE
                }
            }
            cameraMenuWidget?.view?.run {
                visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        cameraSettingsExposureButton.setOnClickListener {
            cameraMenuWidget?.view?.run {
                if (visibility == View.VISIBLE) {
                    visibility = View.GONE
                }
            }
            cameraSettingsExposureWidget?.view?.run {
                visibility = if (visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
    }

    private fun addViews(view: View) {
        createLogoButtonView(view)
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
        set.connect(appLogo?.id ?: 0, ConstraintSet.TOP, statusGradientWidget?.id
                ?: 0, ConstraintSet.TOP, requireContext().dpToPx(4))
        set.connect(appLogo?.id ?: 0, ConstraintSet.BOTTOM, statusGradientWidget?.id
                ?: 0, ConstraintSet.BOTTOM, requireContext().dpToPx(4))
        set.connect(appLogo?.id ?: 0, ConstraintSet.START, statusGradientWidget?.id
                ?: 0, ConstraintSet.START, requireContext().dpToPx(16))
        set.applyTo(constraintLayout)
    }

    private fun arrangeStatusLabelWidget(view: View) {
        val constraintLayout = view.findViewById<ConstraintLayout>(R.id.containerView)

        val set = ConstraintSet()
        set.clone(constraintLayout)
        set.clear(statusLabelWidget?.id ?: 0, ConstraintSet.START)
        set.connect(statusLabelWidget?.id ?: 0, ConstraintSet.START, appLogo?.id
                ?: 0, ConstraintSet.END, requireContext().dpToPx(16))
        set.connect(statusLabelWidget?.id ?: 0, ConstraintSet.END, flightModeWidget?.id
                ?: 0, ConstraintSet.START, requireContext().dpToPx(16))
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