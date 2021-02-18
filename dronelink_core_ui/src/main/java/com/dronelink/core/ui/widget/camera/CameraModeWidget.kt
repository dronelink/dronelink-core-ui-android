package com.dronelink.core.ui.widget.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.dronelink.core.DroneSession
import com.dronelink.core.adapters.CameraStateAdapter
import com.dronelink.core.command.CommandError
import com.dronelink.core.kernel.command.Command
import com.dronelink.core.kernel.command.camera.ModeCameraCommand
import com.dronelink.core.kernel.core.enums.CameraMode
import com.dronelink.core.ui.R
import com.dronelink.core.ui.databinding.CameraModeWidgetBinding
import com.dronelink.core.ui.delegates.BindFragment
import com.dronelink.core.ui.widget.UpdatableWidget

class CameraModeWidget : UpdatableWidget() {

    val binding: CameraModeWidgetBinding by BindFragment(R.layout.camera_mode_widget)
    var channel: Int = 0
    private val cameraState: CameraStateAdapter?
        get() = session?.getCameraState(channel)?.value

    private var pendingCommand: ModeCameraCommand? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding.run {
            isVideo = cameraState?.mode == CameraMode.VIDEO
            modeSwitch.setOnCheckedChangeListener { _, isChecked ->
                onModeChanged(isChecked)
            }
        }

        return binding.root
    }

    private fun onModeChanged(isChecked: Boolean) {
        val command = ModeCameraCommand()
        command.mode = if (isChecked) CameraMode.VIDEO else CameraMode.PHOTO
        try {
            session?.addCommand(command)
            pendingCommand = command
        } catch (ex: Exception) {
        }
    }

    override fun update() {
        super.update()
        binding.run {
            isVideo = if (pendingCommand != null) {
                pendingCommand?.mode == CameraMode.VIDEO
            } else {
                cameraState?.mode == CameraMode.VIDEO
            }
            modeSwitch.isEnabled = session != null && pendingCommand == null
        }
    }

    override fun onClosed(session: DroneSession) {
        super.onClosed(session)
        pendingCommand = null
    }

    override fun onCommandFinished(session: DroneSession, command: Command, error: CommandError?) {
        super.onCommandFinished(session, command, error)
        if (pendingCommand?.id == command.id) {
            pendingCommand = null
            error?.let {
                Toast.makeText(requireContext(), it.description, Toast.LENGTH_LONG).show()
            }
            activity?.runOnUiThread { update() }
        }
    }
}