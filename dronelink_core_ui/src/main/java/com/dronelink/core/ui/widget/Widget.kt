package com.dronelink.core.ui.widget

import android.os.Handler
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.dronelink.core.*
import com.dronelink.core.command.CommandError
import com.dronelink.core.kernel.command.Command
import com.dronelink.core.kernel.core.Message

open class Widget : Fragment() {

    var droneSessionManager: DroneSessionManager? = null

    val targetDroneSessionManager: DroneSessionManager = droneSessionManager ?: Dronelink.getInstance().targetDroneSessionManager

    var session: DroneSession? = targetDroneSessionManager.session
    var missionExecutor: MissionExecutor? = Dronelink.getInstance().missionExecutor
    var modeExecutor: ModeExecutor? = Dronelink.getInstance().modeExecutor
    var funcExecutor: FuncExecutor? = Dronelink.getInstance().funcExecutor
    var widgetFactory: WidgetFactory? = (targetDroneSessionManager as? WidgetFactoryProvider)?.widgetFactory ?: WidgetFactory.shared

}

open class ListenerWidget: Widget(), Dronelink.Listener, DroneSessionManager.Listener, DroneSession.Listener, MissionExecutor.Listener, ModeExecutor.Listener, FuncExecutor.Listener {

    override fun onStart() {
        super.onStart()
        Dronelink.getInstance().addListener(this)
    }

    override fun onStop() {
        super.onStop()
        Dronelink.getInstance().removeListener(this)
        Dronelink.getInstance().missionExecutor?.removeListener(this)
        Dronelink.getInstance().modeExecutor?.removeListener(this)
        Dronelink.getInstance().funcExecutor?.removeListener(this)
        Dronelink.getInstance().droneSessionManagers?.forEach {
            it.removeListener(this)
            it.session?.removeListener(this)
        }
    }

    override fun onRegistered(error: String?) { }

    override fun onDroneSessionManagerAdded(manager: DroneSessionManager?) { }

    override fun onMissionLoaded(executor: MissionExecutor?) { }

    override fun onMissionUnloaded(executor: MissionExecutor?) { }

    override fun onFuncLoaded(executor: FuncExecutor?) { }

    override fun onFuncUnloaded(executor: FuncExecutor?) { }

    override fun onModeLoaded(executor: ModeExecutor?) { }

    override fun onModeUnloaded(executor: ModeExecutor?) { }

    override fun onOpened(session: DroneSession?) {
        this.session = session
    }

    override fun onClosed(session: DroneSession?) {
        this.session = null
    }

    override fun onInitialized(session: DroneSession?) { }

    override fun onLocated(session: DroneSession?) { }

    override fun onMotorsChanged(session: DroneSession?, value: Boolean) { }

    override fun onCommandExecuted(session: DroneSession?, command: Command?) { }

    override fun onCommandFinished(session: DroneSession?, command: Command?, error: CommandError?) { }

    override fun onCameraFileGenerated(session: DroneSession?, file: CameraFile?) { }

    override fun onMissionEstimating(executor: MissionExecutor?) { }

    override fun onMissionEstimated(executor: MissionExecutor?, estimate: MissionExecutor.Estimate?) { }

    override fun onMissionEngaging(executor: MissionExecutor?) { }

    override fun onMissionEngaged(executor: MissionExecutor?, engagement: Executor.Engagement?) { }

    override fun onMissionExecuted(executor: MissionExecutor?, engagement: Executor.Engagement?) { }

    override fun onMissionDisengaged(executor: MissionExecutor?, engagement: Executor.Engagement?, reason: Message?) { }

    override fun onModeEngaging(executor: ModeExecutor?) { }

    override fun onModeEngaged(executor: ModeExecutor?, engagement: Executor.Engagement?) { }

    override fun onModeExecuted(executor: ModeExecutor?, engagement: Executor.Engagement?) { }

    override fun onModeDisengaged(executor: ModeExecutor?, engagement: Executor.Engagement?, reason: Message?) { }

    override fun onFuncInputsChanged(executor: FuncExecutor?) { }

    override fun onFuncExecuted(executor: FuncExecutor?) { }

}

open class WrapperWidget: Widget() {

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

open class UpdatableWidget: ListenerWidget() {

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

    open fun update() { }

}

interface WidgetFactoryProvider {
    val widgetFactory: WidgetFactory?
}

open class WidgetFactory(open val session: DroneSession? = null) {

    companion object {
        val shared = WidgetFactory()
    }

    open fun createCameraFeedWidget(primary: Boolean = true): Widget? = null

}

