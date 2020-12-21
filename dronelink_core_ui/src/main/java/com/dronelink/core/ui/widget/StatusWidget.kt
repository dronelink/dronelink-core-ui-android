package com.dronelink.core.ui.widget

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.dronelink.core.Dronelink
import com.dronelink.core.kernel.core.Message
import com.dronelink.core.ui.R

open class StatusWidget: UpdatableWidget() {

    override var updateInterval: Long = 500

    data class Status(val message: String, val color: Int)

    internal val status: Status
        get() {
            val dronelinkStatusMessages = Dronelink.getInstance().statusMessages
            dronelinkStatusMessages?.filter { it.level.compare(Message.Level.WARNING) > 0 }?.firstOrNull()?.let {
                return it.getStatus()
            }

            val state = session?.state?.value ?: return getDisconnectedStatus()

            targetDroneSessionManager?.statusMessages?.filter { it.level != Message.Level.INFO }?.sortedWith(Comparator { m1, m2 ->
                return@Comparator m1.level.compare(m2.level)
            })?.first {
                return it.getStatus()
            }

            dronelinkStatusMessages.first {
                return it.getStatus()
            }

            return if (state.isFlying) getManualStatus() else getReadyStatus()

        }

    private fun getDisconnectedStatus(): Status {
        return Status(resources.getString(R.string.StatusWidget_Disconnected), ContextCompat.getColor(requireContext(), R.color.deep_purple))
    }

    private fun getReadyStatus(): Status {
        return Status(resources.getString(R.string.StatusWidget_Ready), ContextCompat.getColor(requireContext(), R.color.green))
    }

    private fun getManualStatus(): Status {
        return Status(resources.getString(R.string.StatusWidget_Manual), ContextCompat.getColor(requireContext(), R.color.green))
    }

    fun Message.getStatusColor(): Int {
        return when (level) {
            Message.Level.INFO -> R.color.green
            Message.Level.WARNING -> R.color.amber
            Message.Level.DANGER, Message.Level.ERROR -> R.color.red
            else -> R.color.overlay
        }
    }

    fun Message.getStatus(): Status = Status(this.toString(), this.getStatusColor())

}

class StatusGradientWidget: StatusWidget() {

    private var _gradient: GradientDrawable? = null
    val gradient get() = _gradient!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val containerView = FrameLayout(requireContext())
        containerView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        _gradient = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(ContextCompat.getColor(requireContext(), R.color.overlay),
                ContextCompat.getColor(requireContext(), R.color.overlay_20))
        );
        gradient.cornerRadius = 0f;
        containerView.background = gradient
        return containerView
    }

    override fun update() {
        super.update()

        gradient.colors = intArrayOf(status.color,
            ContextCompat.getColor(requireContext(), R.color.overlay_20))
    }

}

class StatusLabelWidget: StatusWidget() {

    var colorEnabled = false

    private var _containerView: ConstraintLayout? = null
    private val containerView get() = _containerView!!
    private var _textView: TextView? = null
    val textView get() = _textView!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _containerView = ConstraintLayout(requireContext())
        containerView.id = View.generateViewId()
        containerView.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)

        _textView = TextView(requireContext())
        textView.layoutParams = ViewGroup.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
        textView.id = View.generateViewId()
        textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        textView.textSize = 16.0f
        textView.gravity = Gravity.CENTER_VERTICAL
        textView.ellipsize = TextUtils.TruncateAt.MARQUEE
        textView.marqueeRepeatLimit = -1
        textView.setHorizontallyScrolling(true)
        textView.isSelected = true

        containerView.addView(textView)

        val set = ConstraintSet()
        set.clone(containerView)
        set.connect(textView.id, ConstraintSet.TOP, containerView.id, ConstraintSet.TOP)
        set.connect(textView.id, ConstraintSet.BOTTOM, containerView.id, ConstraintSet.BOTTOM)
        set.connect(textView.id, ConstraintSet.START, containerView.id, ConstraintSet.START)
        set.connect(textView.id, ConstraintSet.END, containerView.id, ConstraintSet.END)

        set.applyTo(containerView)

        return containerView
    }

    override fun update() {
        super.update()
        textView.text = status.message
        if (colorEnabled) {
            containerView.setBackgroundColor(ContextCompat.getColor(requireContext(), status.color))
        }
    }

}