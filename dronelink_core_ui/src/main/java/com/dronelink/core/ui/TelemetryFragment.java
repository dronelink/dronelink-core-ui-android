//  TelemetryFragment2.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 11/27/19.
//  Copyright © 2019 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.location.Location;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dronelink.core.Convert;
import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.adapters.DroneStateAdapter;
import com.dronelink.core.mission.core.enums.UnitSystem;

import java.util.Timer;
import java.util.TimerTask;

public class TelemetryFragment extends Fragment implements DroneSessionManager.Listener {
    private DroneSession session;

    private TextView distanceTextView;
    private TextView altitudeTextView;
    private TextView horizontalSpeedTextView;
    private TextView verticalSpeedTextView;
    private final long updateMillis = 500;
    private Timer updateTimer;
    private final String largeFormat = "%s %.0f %s";
    private final String smallFormat = "%s %.1f %s";
    private String distancePrefix;
    private String distanceSuffix;
    private String altitudePrefix;
    private String altitudeSuffix;
    private String horizontalSpeedPrefix;
    private String horizontalSpeedSuffix;
    private String verticalSpeedPrefix;
    private String verticalSpeedSuffix;

    private DroneStateAdapter getDroneState() {
        if (session == null || session.getState() == null) {
            return null;
        }
        return session.getState().value;
    }

    public TelemetryFragment() {}

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_telemetry, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        distanceTextView = getView().findViewById(R.id.distanceTextView);
        altitudeTextView = getView().findViewById(R.id.altitudeTextView);
        horizontalSpeedTextView = getView().findViewById(R.id.horizontalSpeedTextView);
        verticalSpeedTextView = getView().findViewById(R.id.verticalSpeedTextView);

        distancePrefix = getString(R.string.Telemetry_distance_prefix);
        distanceSuffix = getString(Dronelink.UNIT_SYSTEM == UnitSystem.IMPERIAL ? R.string.Telemetry_distance_suffix_imperial : R.string.Telemetry_distance_suffix_metric);
        altitudePrefix = getString(R.string.Telemetry_altitude_prefix);
        altitudeSuffix = getString(Dronelink.UNIT_SYSTEM == UnitSystem.IMPERIAL ? R.string.Telemetry_distance_suffix_imperial : R.string.Telemetry_distance_suffix_metric);
        horizontalSpeedPrefix = getString(R.string.Telemetry_horizontalSpeed_prefix);
        horizontalSpeedSuffix = getString(Dronelink.UNIT_SYSTEM == UnitSystem.IMPERIAL ? R.string.Telemetry_horizontalSpeed_suffix_imperial : R.string.Telemetry_horizontalSpeed_suffix_metric);
        verticalSpeedPrefix = getString(R.string.Telemetry_verticalSpeed_prefix);
        verticalSpeedSuffix = getString(Dronelink.UNIT_SYSTEM == UnitSystem.IMPERIAL ? R.string.Telemetry_verticalSpeed_suffix_imperial : R.string.Telemetry_verticalSpeed_suffix_metric);

        updateTimer = new Timer();
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateTimer();
            }
        }, 0, updateMillis);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dronelink.getInstance().getSessionManager().addListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        Dronelink.getInstance().getSessionManager().removeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (updateTimer != null) {
            updateTimer.cancel();
        }
    }

    private void updateTimer() {
        getActivity().runOnUiThread(update);
    }

    private Runnable update = new Runnable() {
        public void run() {
            double distance = 0.0;
            double altitude = 0.0;
            double horizontalSpeed = 0.0;
            double verticalSpeed = 0.0;

            final DroneStateAdapter state = getDroneState();
            if (state != null) {
                final Location droneLocation = state.getLocation();
                if (droneLocation != null) {
                    final Location homeLocation = state.getHomeLocation();
                    if (homeLocation != null) {
                        distance = droneLocation.distanceTo(homeLocation);
                        distance = Double.isNaN(distance) ? 0 : distance;
                    }
                }

                altitude = state.getAltitude();
                horizontalSpeed = state.getHorizontalSpeed();
                verticalSpeed = state.getVerticalSpeed();
            }

            switch (Dronelink.UNIT_SYSTEM) {
                case IMPERIAL:
                    distance = Convert.MetersToFeet(distance);
                    altitude = Convert.MetersToFeet(altitude);
                    horizontalSpeed = Convert.MetersPerSecondToMilesPerHour(horizontalSpeed);
                    verticalSpeed = Convert.MetersToFeet(verticalSpeed);
                    break;

                case METRIC:
                    horizontalSpeed = Convert.MetersPerSecondToKilometersPerHour(horizontalSpeed);
                    break;
            }

            distanceTextView.setText(String.format(distance > 10 ? largeFormat : smallFormat, distancePrefix, distance, distanceSuffix));
            altitudeTextView.setText(String.format(altitude > 10 ? largeFormat : smallFormat, altitudePrefix, altitude, altitudeSuffix));
            horizontalSpeedTextView.setText(String.format(horizontalSpeed > 10 ? largeFormat : smallFormat, horizontalSpeedPrefix, horizontalSpeed, horizontalSpeedSuffix));
            verticalSpeedTextView.setText(String.format(verticalSpeed > 10 ? largeFormat : smallFormat, verticalSpeedPrefix, verticalSpeed, verticalSpeedSuffix));
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