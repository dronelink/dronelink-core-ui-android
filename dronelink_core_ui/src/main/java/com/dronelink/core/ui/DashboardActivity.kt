package com.dronelink.core.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dronelink.core.ui.widget.ListenerWidget
import com.dronelink.core.ui.widget.Widget


class DashboardActivity: AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
    }

}