//  TelemetryFragment2.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 11/27/19.
//  Copyright © 2019 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.app.Activity;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.dronelink.core.Convert;
import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.adapters.DroneStateAdapter;
import com.dronelink.core.kernel.core.enums.UnitSystem;

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
    private String distanceSuffixMetric;
    private String distanceSuffixImperial;
    private String altitudePrefix;
    private String altitudeSuffixMetric;
    private String altitudeSuffixImperial;
    private String horizontalSpeedPrefix;
    private String horizontalSpeedSuffixMetric;
    private String horizontalSpeedSuffixImperial;
    private String verticalSpeedPrefix;
    private String verticalSpeedSuffixMetric;
    private String verticalSpeedSuffixImperial;

    private DroneStateAdapter getDroneState() {
        final DroneSession session = this.session;
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
        distanceSuffixMetric = getString(R.string.Telemetry_distance_suffix_metric);
        distanceSuffixImperial = getString(R.string.Telemetry_distance_suffix_imperial);
        altitudePrefix = getString(R.string.Telemetry_altitude_prefix);
        altitudeSuffixMetric = getString(R.string.Telemetry_distance_suffix_metric);
        altitudeSuffixImperial = getString(R.string.Telemetry_distance_suffix_imperial);
        horizontalSpeedPrefix = getString(R.string.Telemetry_horizontalSpeed_prefix);
        horizontalSpeedSuffixMetric = getString(R.string.Telemetry_horizontalSpeed_suffix_metric);
        horizontalSpeedSuffixImperial = getString(R.string.Telemetry_horizontalSpeed_suffix_imperial);
        verticalSpeedPrefix = getString(R.string.Telemetry_verticalSpeed_prefix);
        verticalSpeedSuffixMetric = getString(R.string.Telemetry_verticalSpeed_suffix_metric);
        verticalSpeedSuffixImperial = getString(R.string.Telemetry_verticalSpeed_suffix_imperial);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dronelink.getInstance().getTargetDroneSessionManager().addListener(this);
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
        Dronelink.getInstance().getTargetDroneSessionManager().removeListener(this);
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

            switch (Dronelink.getInstance().getUnitSystem()) {
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

            distanceTextView.setText(String.format(distance > 10 ? largeFormat : smallFormat, distancePrefix, distance, Dronelink.getInstance().getUnitSystem() == UnitSystem.IMPERIAL ? distanceSuffixImperial : distanceSuffixMetric));
            altitudeTextView.setText(String.format(altitude > 10 ? largeFormat : smallFormat, altitudePrefix, altitude, Dronelink.getInstance().getUnitSystem() == UnitSystem.IMPERIAL ? altitudeSuffixImperial : altitudeSuffixMetric));
            horizontalSpeedTextView.setText(String.format(horizontalSpeed > 10 ? largeFormat : smallFormat, horizontalSpeedPrefix, horizontalSpeed, Dronelink.getInstance().getUnitSystem() == UnitSystem.IMPERIAL ? horizontalSpeedSuffixImperial : horizontalSpeedSuffixMetric));
            verticalSpeedTextView.setText(String.format(verticalSpeed > 10 ? largeFormat : smallFormat, verticalSpeedPrefix, verticalSpeed, Dronelink.getInstance().getUnitSystem() == UnitSystem.IMPERIAL ? verticalSpeedSuffixImperial : verticalSpeedSuffixMetric));
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