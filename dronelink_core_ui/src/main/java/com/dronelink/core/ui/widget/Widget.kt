package com.dronelink.core.ui.widget

import android.content.Context
import android.os.Handler
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.dronelink.core.*
import com.dronelink.core.command.CommandError
import com.dronelink.core.kernel.command.Command
import com.dronelink.core.kernel.core.Message

open class Widget : Fragment() {
    protected val gson = Kernel.createGson()
    var droneSessionManager: DroneSessionManager? = null

    val targetDroneSessionManager: DroneSessionManager?
        get() = droneSessionManager ?: Dronelink.getInstance()?.targetDroneSessionManager

    val session: DroneSession?
        get() = targetDroneSessionManager?.session
    val missionExecutor: MissionExecutor?
        get() = Dronelink.getInstance()?.missionExecutor
    val modeExecutor: ModeExecutor?
        get() = Dronelink.getInstance()?.modeExecutor
    val funcExecutor: FuncExecutor?
        get() = Dronelink.getInstance()?.funcExecutor
    val widgetFactory: WidgetFactory?
        get() = (targetDroneSessionManager as? WidgetFactoryProvider)?.widgetFactory
                ?: WidgetFactory.shared
}

open class ListenerWidget(private val onWidgetAttached: (() -> Unit)? = null) : Widget(), Dronelink.Listener, DroneSessionManager.Listener, DroneSession.Listener, MissionExecutor.Listener, ModeExecutor.Listener, FuncExecutor.Listener {

    override fun onStart() {
        super.onStart()
        Dronelink.getInstance()?.addListener(this)
        onWidgetAttached?.invoke()
    }

    override fun onStop() {
        super.onStop()
        Dronelink.getInstance()?.run {
            removeListener(this@ListenerWidget)
            missionExecutor?.removeListener(this@ListenerWidget)
            modeExecutor?.removeListener(this@ListenerWidget)
            funcExecutor?.removeListener(this@ListenerWidget)
            droneSessionManagers?.forEach {
                it.removeListener(this@ListenerWidget)
                it.session?.removeListener(this@ListenerWidget)
            }
        }
    }

    override fun onRegistered(error: String?) {}

    override fun onDroneSessionManagerAdded(manager: DroneSessionManager) {
        manager.addListener(this)
    }

    override fun onMissionLoaded(executor: MissionExecutor) {
        executor.addListener(this)
    }

    override fun onMissionUnloaded(executor: MissionExecutor) {
        executor.removeListener(this)
    }

    override fun onFuncLoaded(executor: FuncExecutor) {
        executor.addListener(this)
    }

    override fun onFuncUnloaded(executor: FuncExecutor) {
        executor.removeListener(this)
    }

    override fun onModeLoaded(executor: ModeExecutor) {
        executor.addListener(this)
    }

    override fun onModeUnloaded(executor: ModeExecutor) {
        executor.removeListener(this)
    }

    override fun onOpened(session: DroneSession) {
        this.session?.addListener(this)
    }

    override fun onClosed(session: DroneSession) {
        this.session?.removeListener(this)
    }

    override fun onInitialized(session: DroneSession) {}

    override fun onLocated(session: DroneSession) {}

    override fun onMotorsChanged(session: DroneSession, value: Boolean) {}

    override fun onCommandExecuted(session: DroneSession, command: Command) {}

    override fun onCommandFinished(session: DroneSession, command: Command, error: CommandError?) {}

    override fun onCameraFileGenerated(session: DroneSession, file: CameraFile) {}

    override fun onMissionEstimating(executor: MissionExecutor) {}

    override fun onMissionEstimated(executor: MissionExecutor, estimate: MissionExecutor.Estimate) {}

    override fun onMissionEngaging(executor: MissionExecutor) {}

    override fun onMissionEngaged(executor: MissionExecutor, engagement: Executor.Engagement) {}

    override fun onMissionExecuted(executor: MissionExecutor, engagement: Executor.Engagement) {}

    override fun onMissionDisengaged(executor: MissionExecutor, engagement: Executor.Engagement, reason: Message) {}

    override fun onModeEngaging(executor: ModeExecutor) {}

    override fun onModeEngaged(executor: ModeExecutor, engagement: Executor.Engagement) {}

    override fun onModeExecuted(executor: ModeExecutor, engagement: Executor.Engagement) {}

    override fun onModeDisengaged(executor: ModeExecutor, engagement: Executor.Engagement, reason: Message) {}

    override fun onFuncInputsChanged(executor: FuncExecutor) {}

    override fun onFuncExecuted(executor: FuncExecutor) {}

}

open class WrapperWidget : Widget() {

    private var _fragment: Fragment? = null

    var containerId: Int = 0

    var fragment: Fragment? = _fragment
        get() = _fragment
        set(value) {
            value?.let {
                childFragmentManager.beginTransaction()
                        .replace(containerId, it)
                        .commit()
                _fragment = fragment
            }
            field = value
        }

}

fun Fragment.createWidget(): WrapperWidget {
    val widget = WrapperWidget()
    widget.fragment = this
    return WrapperWidget()
}

fun View.createWidget(): WrapperWidget {
    val widget = WrapperWidget()
    val viewGroup = widget.fragment?.view as ViewGroup
    viewGroup.addView(this)
    return widget
}

open class UpdatableWidget(onWidgetAttached: (() -> Unit)? = null) : ListenerWidget(onWidgetAttached) {

    open var updateInterval: Long = 1000
    private var updateTimer = Handler()

    private val timerRunnable = Runnable {
        activity?.runOnUiThread {
            update()
        }
        setupTimer()
    }

    private fun setupTimer() {
        updateTimer.postDelayed(timerRunnable, updateInterval)
    }

    override fun onStart() {
        super.onStart()
        setupTimer()
    }

    override fun onStop() {
        super.onStop()
        updateTimer.removeCallbacks(timerRunnable)
    }

    open fun update() {}
}

interface WidgetFactoryProvider {
    val widgetFactory: WidgetFactory?
}

open class WidgetFactory(open val session: DroneSession? = null) {

    companion object {
        val shared = WidgetFactory()
    }

    open fun createCameraFeedWidget(primary: Boolean = true): Widget? = null
    open fun createCameraMenuWidget(primary: Boolean = true): Widget? = null
    open fun createCameraSettingsExposureWidget(primary: Boolean = true): Widget? = null
}