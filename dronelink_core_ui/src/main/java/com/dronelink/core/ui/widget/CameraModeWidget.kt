package com.dronelink.core.ui.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.dronelink.core.DroneSession
import com.dronelink.core.adapters.CameraStateAdapter
import com.dronelink.core.command.CommandError
import com.dronelink.core.kernel.command.Command
import com.dronelink.core.kernel.command.camera.ModeCameraCommand
import com.dronelink.core.kernel.core.enums.CameraMode
import com.dronelink.core.ui.R
import com.dronelink.core.ui.util.dpToPx
import java.lang.Exception

class CameraModeWidget: UpdatableWidget() {

    var channel: Int = 0
    private val cameraState: CameraStateAdapter?
        get() = session?.getCameraState(channel)?.value

    private var _button: Button? = null
    val button get() = _button!!

    var photoImage = R.drawable.camera_photo_mode_icon
    var videoImage = R.drawable.camera_video_mode_icon

    private var pendingCommand: ModeCameraCommand? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val constraintLayout = ConstraintLayout(requireContext())
        constraintLayout.id = View.generateViewId()
        constraintLayout.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)

        _button = Button(requireContext())

        constraintLayout.addView(_button)

        button.id = View.generateViewId()
        button.layoutParams = ConstraintLayout.LayoutParams(requireContext().dpToPx(60), requireContext().dpToPx(60))
        button.setBackgroundResource(if (cameraState?.mode == CameraMode.VIDEO) videoImage else photoImage)
        button.isEnabled = false
        button.setOnClickListener(::onTapped)

        val buttonSet = ConstraintSet()
        buttonSet.clone(constraintLayout)
        buttonSet.connect(button.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP)
        buttonSet.connect(button.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM)
        buttonSet.connect(button.id, ConstraintSet.START, constraintLayout.id, ConstraintSet.START)
        buttonSet.connect(button.id, ConstraintSet.END, constraintLayout.id, ConstraintSet.END)
        buttonSet.applyTo(constraintLayout)


        return constraintLayout
    }

    fun onTapped(view: View) {
        button.isEnabled = false
        val command = ModeCameraCommand()
        command.mode = if (cameraState?.mode == CameraMode.PHOTO) CameraMode.VIDEO else CameraMode.PHOTO
        try {
            session?.addCommand(command)
            pendingCommand = command
        } catch (ex: Exception) { }
    }

    override fun update() {
        super.update()
        if (pendingCommand != null) {
            button.setBackgroundResource(if (pendingCommand?.mode == CameraMode.VIDEO) videoImage else photoImage)
        } else {
            button.setBackgroundResource(if (cameraState?.mode == CameraMode.VIDEO) videoImage else photoImage)
        }
        button.isEnabled = session != null && pendingCommand == null
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

}