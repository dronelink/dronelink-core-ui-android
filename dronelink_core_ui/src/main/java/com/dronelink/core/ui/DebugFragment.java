//  DebugFragment.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 6/3/21.
//  Copyright © 2021 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dronelink.core.DatedValue;
import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.adapters.CameraStateAdapter;
import com.dronelink.core.adapters.DroneStateAdapter;
import com.dronelink.core.adapters.GimbalAdapter;
import com.dronelink.core.adapters.GimbalStateAdapter;

import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;

public class DebugFragment extends Fragment implements DroneSessionManager.Listener {
    private DroneSession session;

    private TextView textView;

    private final long updateMillis = 250;
    private Timer updateTimer;

    private DroneStateAdapter getDroneState() {
        final DroneSession session = this.session;
        if (session == null) {
            return null;
        }

        final DatedValue<DroneStateAdapter> droneState = session.getState();
        if (droneState == null) {
            return null;
        }

        return droneState.value;
    }

    private CameraStateAdapter getCameraState() {
        final DroneSession session = this.session;
        if (session == null) {
            return null;
        }

        final DatedValue<CameraStateAdapter> cameraState = session.getCameraState(0);
        if (cameraState == null) {
            return null;
        }

        return cameraState.value;
    }

    public DebugFragment() {}

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_debug, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        textView = getView().findViewById(R.id.textView);
    }

    @Override
    public void onStart() {
        super.onStart();
        final DroneSessionManager manager = Dronelink.getInstance().getTargetDroneSessionManager();
        if (manager != null) {
            manager.addListener(this);
        }

        updateTimer = new Timer();
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateTimer();
            }
        }, 0, updateMillis);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (updateTimer != null) {
            updateTimer.cancel();
        }

        final DroneSessionManager manager = Dronelink.getInstance().getTargetDroneSessionManager();
        if (manager != null) {
            manager.removeListener(this);
        }
    }

    private void updateTimer() {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(update);
        }
    }

    private Runnable update = new Runnable() {
        public void run() {
            if (!isAdded()) {
                return;
            }

            final DroneStateAdapter droneState = getDroneState();
            if (droneState == null) {
                textView.setText("Drone disconnected...");
                return;
            }

            final StringBuilder value = new StringBuilder();
            final Double ultrasonicAltitude = droneState.getUltrasonicAltitude();
            if (ultrasonicAltitude != null) {
                value.append(String.format("Ultrasonic altitude: %.1f", ultrasonicAltitude)).append("\n");
            }

            final CameraStateAdapter cameraState = getCameraState();
            if (cameraState != null) {
                final Double focusRingValue = cameraState.getFocusRingValue();
                if (focusRingValue != null) {
                    value.append("Focus ring value: " + focusRingValue).append("\n");
                }

                final Double focusRingMax = cameraState.getFocusRingMax();
                if (focusRingMax != null) {
                    value.append("Focus ring max: " + focusRingMax).append("\n");
                }
            }

            final DroneSession sessionLocal = session;
            if (sessionLocal != null) {
                final Collection<GimbalAdapter> gimbals = sessionLocal.getDrone().getGimbals();
                if (gimbals != null) {
                    for (final GimbalAdapter gimbal : gimbals) {
                        final DatedValue<GimbalStateAdapter> state = session.getGimbalState(gimbal.getIndex());
                        if (state != null) {
                            value.append("Gimbal " + gimbal.getIndex() + " heading: " + Dronelink.getInstance().format("angle", state.value.getOrientation().getYaw(), "")).append("\n");
                        }
                    }
                }
            }

            textView.setText(value.toString());
        }
    };

    @Override
    public void onOpened(final DroneSession session) {
        this.session = session;
    }

    @Override
    public void onClosed(final DroneSession session) {
        this.session = null;
    }
}