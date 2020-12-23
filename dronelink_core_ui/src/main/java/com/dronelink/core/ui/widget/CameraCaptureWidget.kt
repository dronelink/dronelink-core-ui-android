package com.dronelink.core.ui.widget

import android.content.res.ColorStateList
import android.media.MediaActionSound
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.dronelink.core.CameraFile
import com.dronelink.core.DroneSession
import com.dronelink.core.adapters.CameraStateAdapter
import com.dronelink.core.command.CommandError
import com.dronelink.core.kernel.command.Command
import com.dronelink.core.kernel.command.camera.StartCaptureCameraCommand
import com.dronelink.core.kernel.command.camera.StopCaptureCameraCommand
import com.dronelink.core.kernel.core.enums.CameraMode
import com.dronelink.core.kernel.core.enums.CameraPhotoMode
import com.dronelink.core.kernel.core.enums.CameraStorageLocation
import com.dronelink.core.ui.R
import com.dronelink.core.ui.util.dpToPx

class CameraCaptureWidget: UpdatableWidget() {

    var channel: Int = 0
    private val cameraState: CameraStateAdapter?
        get() = session?.getCameraState(channel)?.value

    override var updateInterval: Long = 100

    private var _button: Button? = null
    val button get() = _button!!
    private var _activityBackgroundImageView: ImageView? = null
    val activityBackgroundImageView get() = _activityBackgroundImageView!!
    private var _extraImageView: ImageView? = null
    val extraImageView get() = _extraImageView!!
    private var _activityIndicator: ProgressBar? = null
    val activityIndicator get() = _activityIndicator!!
    private var _extraLabel: TextView? = null
    val extraLabel get() = _extraLabel!!
    private var _timeLabel: TextView? = null
    val timeLabel get() = _timeLabel!!

    var activityImage = R.drawable.activity_icon
    var startImage = R.drawable.capture_icon
    var sdCardMissing = R.drawable.sd_card_missing_icon
    var aebModeImage = R.drawable.aeb_mode_icon
    var burstModeImage = R.drawable.burst_mode_icon
    var hdrModeImage = R.drawable.hdr_mode_icon
    var hyperModeImage = R.drawable.hyper_mode_icon
    var timerModeImage = R.drawable.timer_mode_icon
    var panoModeImage = R.drawable.pano_mode_icon

    var stopImage = R.drawable.stop_icon
    var videoColor = R.color.red
    var photoColor = R.color.white

    private var pendingCommand: Command? = null
    private var previousCapturing = false
    private var mediaActionSound = MediaActionSound()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        mediaActionSound.load(MediaActionSound.SHUTTER_CLICK)
        mediaActionSound.load(MediaActionSound.START_VIDEO_RECORDING)
        mediaActionSound.load(MediaActionSound.STOP_VIDEO_RECORDING)

        val constraintLayout = ConstraintLayout(requireContext())
        constraintLayout.id = View.generateViewId()
        constraintLayout.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)

        _button = Button(requireContext())

        constraintLayout.addView(_button)

        button.id = View.generateViewId()
        button.layoutParams = ConstraintLayout.LayoutParams(requireContext().dpToPx(60), requireContext().dpToPx(60))
        button.setBackgroundResource(startImage)
        button.setOnClickListener(::onTapped)
        button.isEnabled = false

        val buttonSet = ConstraintSet()
        buttonSet.clone(constraintLayout)
        buttonSet.connect(button.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP)
        buttonSet.connect(button.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM)
        buttonSet.connect(button.id, ConstraintSet.START, constraintLayout.id, ConstraintSet.START)
        buttonSet.connect(button.id, ConstraintSet.END, constraintLayout.id, ConstraintSet.END)
        buttonSet.applyTo(constraintLayout)

        _activityBackgroundImageView = ImageView(requireContext())

        constraintLayout.addView(_activityBackgroundImageView)

        activityBackgroundImageView.id = View.generateViewId()
        activityBackgroundImageView.layoutParams = ConstraintLayout.LayoutParams(requireContext().dpToPx(60), requireContext().dpToPx(60))
        activityBackgroundImageView.setImageResource(activityImage)
        activityBackgroundImageView.isEnabled = false

        val activityImageSet = ConstraintSet()
        activityImageSet.clone(constraintLayout)
        activityImageSet.connect(activityBackgroundImageView.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP)
        activityImageSet.connect(activityBackgroundImageView.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM)
        activityImageSet.connect(activityBackgroundImageView.id, ConstraintSet.START, constraintLayout.id, ConstraintSet.START)
        activityImageSet.connect(activityBackgroundImageView.id, ConstraintSet.END, constraintLayout.id, ConstraintSet.END)
        activityImageSet.applyTo(constraintLayout)

        _extraImageView = ImageView(requireContext())

        constraintLayout.addView(_extraImageView)

        extraImageView.id = View.generateViewId()
        extraImageView.layoutParams = ConstraintLayout.LayoutParams(requireContext().dpToPx(22), requireContext().dpToPx(22))
        ImageViewCompat.setImageTintList(extraImageView, ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.black)))
        extraImageView.isEnabled = false

        val extraImageSet = ConstraintSet()
        extraImageSet.clone(constraintLayout)
        extraImageSet.connect(extraImageView.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP)
        extraImageSet.connect(extraImageView.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM)
        extraImageSet.connect(extraImageView.id, ConstraintSet.START, constraintLayout.id, ConstraintSet.START)
        extraImageSet.connect(extraImageView.id, ConstraintSet.END, constraintLayout.id, ConstraintSet.END)
        activityImageSet.applyTo(constraintLayout)

        _activityIndicator = ProgressBar(requireContext())

        constraintLayout.addView(_activityIndicator)

        activityIndicator.id = View.generateViewId()
        activityIndicator.layoutParams = ConstraintLayout.LayoutParams(requireContext().dpToPx(22), requireContext().dpToPx(22))
        activityIndicator.isIndeterminate = true
        activityIndicator.isEnabled = false
        activityIndicator.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.pink))

        val activityIndicatorSet = ConstraintSet()
        activityIndicatorSet.clone(constraintLayout)
        activityIndicatorSet.connect(activityIndicator.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP)
        activityIndicatorSet.connect(activityIndicator.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM)
        activityIndicatorSet.connect(activityIndicator.id, ConstraintSet.START, constraintLayout.id, ConstraintSet.START)
        activityIndicatorSet.connect(activityIndicator.id, ConstraintSet.END, constraintLayout.id, ConstraintSet.END)
        activityIndicatorSet.applyTo(constraintLayout)

        _extraLabel = TextView(requireContext())
        _timeLabel = TextView(requireContext())

        return constraintLayout
    }

    fun onTapped(view: View) {
        button.isEnabled = false
        val command: Command = if (cameraState?.isCapturingContinuous == true) {
            StopCaptureCameraCommand()
        } else {
            StartCaptureCameraCommand()
        }
        try {
            session?.addCommand(command)
            pendingCommand = command
        } catch (ex: Exception) { }
    }

    override fun update() {
        super.update()

        var sound: Int? = null

        if (cameraState?.isCapturingPhotoInterval != true) {
            if (cameraState?.isCapturing == true) {
                if (!previousCapturing) {
                    sound = when (cameraState?.mode) {
                        CameraMode.PHOTO -> MediaActionSound.SHUTTER_CLICK
                        CameraMode.VIDEO -> MediaActionSound.START_VIDEO_RECORDING
                        else -> null
                    }
                }
            } else {
                if (previousCapturing) {
                    if (cameraState?.mode == CameraMode.VIDEO) {
                        sound = MediaActionSound.STOP_VIDEO_RECORDING
                    }
                }
            }
        }

        sound?.let {
            mediaActionSound.play(sound)
        }

        previousCapturing = cameraState?.isCapturing ?: false

        if (cameraState?.mode == CameraMode.VIDEO) {
            button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), videoColor))
        } else {
            button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), photoColor))
        }

        button.isEnabled = session != null && pendingCommand == null && (!(cameraState?.isCapturing ?: false) || cameraState?.isCapturingContinuous ?: false)
        if (cameraState?.isCapturingContinuous == true) {
            button.setBackgroundResource(stopImage)
        } else {
            button.setBackgroundResource(startImage)
        }

        activityBackgroundImageView.alpha = if (button.isEnabled) 1.0f else 0.5f
        if (pendingCommand == null && cameraState?.isBusy != true) {
            if (activityIndicator.visibility == View.VISIBLE) {
                activityIndicator.visibility = View.GONE
            }
        } else {
            if (activityIndicator.visibility == View.GONE) {
                activityIndicator.visibility = View.VISIBLE
            }
        }

        var extraImage: Int? = null
        var extraText: String? = null

        if (cameraState?.storageLocation == CameraStorageLocation.SD_CARD && cameraState?.isSDCardInserted == false) {
            extraImage = sdCardMissing
        }

//        if (extraImage == null && cameraState?.mode == CameraMode.PHOTO) {
//            when (cameraState?.photoMode) {
//                CameraPhotoMode.AEB -> {
//                    extraImage = aebModeImage
//                }
//            }
//        }

    }

    override fun onClosed(session: DroneSession?) {
        super.onClosed(session)
        pendingCommand = null
    }

    override fun onCommandFinished(session: DroneSession?, command: Command?, error: CommandError?) {
        super.onCommandFinished(session, command, error)

        if (pendingCommand?.id == command?.id) {
            pendingCommand = null
            error?.let {
                Toast.makeText(requireContext(), error.description, Toast.LENGTH_LONG).show()
            }
            activity?.runOnUiThread { update() }
        }
    }

    override fun onCameraFileGenerated(session: DroneSession?, file: CameraFile?) {
        super.onCameraFileGenerated(session, file)
        if (cameraState?.mode == CameraMode.PHOTO && cameraState?.photoMode == CameraPhotoMode.INTERVAL) {
            activity?.runOnUiThread { mediaActionSound.play(MediaActionSound.SHUTTER_CLICK) }
        }
    }


}