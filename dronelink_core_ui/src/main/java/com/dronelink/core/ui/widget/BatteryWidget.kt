package com.dronelink.core.ui.widget

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.dronelink.core.ui.R
import com.dronelink.core.ui.util.dpToPx

class BatteryWidget: UpdatableWidget() {

    var normalColor: Int? = null
    var lowColor: Int? = null

    private var _imageView: ImageView? = null
    val imageView get() = _imageView!!
    private var _textView: TextView? = null
    val textView get() = _textView!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        normalColor = ContextCompat.getColor(requireContext(), R.color.green)
        lowColor = ContextCompat.getColor(requireContext(), R.color.red)

        val constraintLayout = ConstraintLayout(requireContext())
        constraintLayout.id = View.generateViewId()
        constraintLayout.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)

        _imageView = ImageView(requireContext())
        _textView = TextView(requireContext())

        constraintLayout.addView(_imageView)
        constraintLayout.addView(_textView)

        imageView.id = View.generateViewId()
        imageView.layoutParams = ConstraintLayout.LayoutParams(requireContext().dpToPx(15), requireContext().dpToPx(15))
        imageView.setImageResource(R.drawable.battery_normal_icon)

        textView.id = View.generateViewId()
        textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.green))
        textView.setTypeface(textView.typeface, Typeface.BOLD)
        textView.textSize = 16.0f


        val set = ConstraintSet()
        set.clone(constraintLayout)

        set.connect(textView.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP)
        set.connect(textView.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM)

        set.connect(imageView.id, ConstraintSet.TOP, constraintLayout.id, ConstraintSet.TOP)
        set.connect(imageView.id, ConstraintSet.BOTTOM, constraintLayout.id, ConstraintSet.BOTTOM)

        set.connect(imageView.id, ConstraintSet.START, constraintLayout.id, ConstraintSet.START)
        set.connect(imageView.id, ConstraintSet.END, textView.id, ConstraintSet.START, requireContext().dpToPx(2))
        set.connect(textView.id, ConstraintSet.START, imageView.id, ConstraintSet.END, requireContext().dpToPx(2))
        set.connect(textView.id, ConstraintSet.END, textView.id, ConstraintSet.END)
        set.applyTo(constraintLayout)

        return constraintLayout
    }

    override fun update() {
        super.update()

        val batteryPercent = session?.state?.value?.batteryPercent

        if (batteryPercent == null) {
            textView.text = getString(R.string.Battery_NA)
            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            return
        }

        textView.text = String.format(getString(R.string.Battery_Percent), batteryPercent.toInt())

        val lowBatteryThreshold = session?.state?.value?.lowBatteryThreshold
        val textColor = if (batteryPercent < (lowBatteryThreshold ?: 0.0)) lowColor else normalColor
        textView.setTextColor(textColor ?: ContextCompat.getColor(requireContext(), R.color.green))

    }


}