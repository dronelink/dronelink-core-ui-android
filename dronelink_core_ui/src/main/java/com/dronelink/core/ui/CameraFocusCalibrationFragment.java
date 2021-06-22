//  CameraFocusCalibrationFragment.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 6/14/21.
//  Copyright © 2021 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dronelink.core.Convert;
import com.dronelink.core.DatedValue;
import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.FuncExecutor;
import com.dronelink.core.MissionExecutor;
import com.dronelink.core.ModeExecutor;
import com.dronelink.core.adapters.CameraStateAdapter;
import com.dronelink.core.adapters.DroneStateAdapter;
import com.dronelink.core.adapters.GimbalStateAdapter;
import com.dronelink.core.kernel.command.camera.FocusModeCameraCommand;
import com.dronelink.core.kernel.command.camera.ModeCameraCommand;
import com.dronelink.core.kernel.command.camera.StopCaptureCameraCommand;
import com.dronelink.core.kernel.command.gimbal.OrientationGimbalCommand;
import com.dronelink.core.kernel.core.CameraFocusCalibration;
import com.dronelink.core.kernel.core.Orientation3;
import com.dronelink.core.kernel.core.enums.CameraFocusMode;
import com.dronelink.core.kernel.core.enums.CameraMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class CameraFocusCalibrationFragment extends Fragment implements Dronelink.Listener, DroneSessionManager.Listener {
    private DroneSession session;

    private ImageView titleImageView;
    private TextView titleTextView;
    private Button type0Button;
    private Button type1Button;
    private TextView detailsTextView;
    private Button markReferenceButton;
    private TextView referenceTextView;
    private ImageButton dismissButton;

    private final long updateMillis = 100;
    private Timer updateTimer;

    private final int referenceInvalidColor = Color.parseColor("#d50000");
    private final int referenceValidColor = Color.parseColor("#00c853");

    private int type = 0;
    private CameraFocusCalibration calibration;
    private Location referenceLocation;
    private Double previousCameraFocusRingValue;
    private boolean previousCameraBusyFocused = false;
    private List<Double> cameraFocusRingValues = new ArrayList<>();

    public CameraFocusCalibrationFragment() {}

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera_focus_calibration, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable final Bundle savedInstanceState) {
        titleImageView = getView().findViewById(R.id.titleImageView);
        titleTextView = getView().findViewById(R.id.titleTextView);
        type0Button = getView().findViewById(R.id.type0Button);
        type0Button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setType(0);
            }
        });

        type1Button = getView().findViewById(R.id.type1Button);
        type1Button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setType(1);
            }
        });
        detailsTextView = getView().findViewById(R.id.detailsTextView);
        markReferenceButton = getView().findViewById(R.id.markReferenceButton);
        referenceTextView = getView().findViewById(R.id.referenceTextView);
        dismissButton = getView().findViewById(R.id.dismissButton);
        dismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                Dronelink.getInstance().updateCameraFocusCalibration(calibration);
            }
        });

        markReferenceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                onMarkReference();
            }
        });

        updateTimer = new Timer();
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateTimer();
            }
        }, 0, updateMillis);
    }

    public void setType(final int type) {
        this.type = type;
        reset();
    }

    public void reset() {
        referenceLocation = null;
        cameraFocusRingValues.clear();

        final DroneSession sessionLocal = session;
        if (sessionLocal != null) {
            final OrientationGimbalCommand command = new OrientationGimbalCommand();
            command.orientation.x = type == 0 ? 0 : Convert.DegreesToRadians(-90);
            try {
                sessionLocal.addCommand(command);
                sessionLocal.addCommand(new StopCaptureCameraCommand());
                sessionLocal.addCommand(new ModeCameraCommand(CameraMode.PHOTO));
                sessionLocal.addCommand(new FocusModeCameraCommand(CameraFocusMode.AUTO));
            } catch (final Exception e) {}
        }

        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dronelink.getInstance().addListener(this);
        Dronelink.getInstance().getSessionManager().addListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (updateTimer != null) {
            updateTimer.cancel();
        }
        Dronelink.getInstance().removeListener(this);
        Dronelink.getInstance().getSessionManager().removeListener(this);
    }

    private void onMarkReference() {
        final DroneSession session = this.session;
        if (session == null) {
            return;
        }

        final DatedValue<DroneStateAdapter> state = session.getState();
        if (state == null || state.value == null) {
            return;
        }

        referenceLocation = state.value.getLocation();
        cameraFocusRingValues.clear();
    }

    private Runnable updateViews = new Runnable() {
        public void run() {
            final View view = getView();
            if (view == null) {
                return;
            }

            final CameraFocusCalibration calibrationLocal = calibration;
            if (calibrationLocal == null) {
                view.setVisibility(View.GONE);
                return;
            }

            view.setVisibility(View.VISIBLE);
            type0Button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(type == 0 ? R.color.lightGray : R.color.darkGray)));
            type1Button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(type == 1 ? R.color.lightGray : R.color.darkGray)));
            markReferenceButton.setVisibility(type == 0 ? View.VISIBLE : View.INVISIBLE);

            final ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams)referenceTextView.getLayoutParams();
            lp.bottomToTop = R.id.nextButton;
            if (type == 0) {
                lp.leftMargin = 12;
                lp.startToEnd = R.id.markReferenceButton;
                lp.startToStart = -1;
            }
            else {
                lp.leftMargin = 0;
                lp.startToStart = R.id.type0Button;
                lp.startToEnd = -1;
            }
            referenceTextView.setLayoutParams(lp);
        }
    };

    private void updateTimer() {
        getActivity().runOnUiThread(update);
    }

    private Runnable update = new Runnable() {
        public void run() {
            final CameraFocusCalibration calibrationLocal = calibration;
            if (calibrationLocal == null) {
                return;
            }

            markReferenceButton.setEnabled(false);
            referenceTextView.setText("");
            referenceTextView.setBackgroundColor(Color.TRANSPARENT);

            final DroneSession sessionLocal = session;
            if (sessionLocal == null) {
                detailsTextView.setText(R.string.CameraFocusCalibration_drone_unavailable);
                return;
            }

            final DatedValue<DroneStateAdapter> state = sessionLocal.getState();
            if (state == null || state.value == null) {
                detailsTextView.setText(R.string.CameraFocusCalibration_drone_unavailable);
                return;
            }

            if (sessionLocal.getSerialNumber() == null) {
                detailsTextView.setText(R.string.CameraFocusCalibration_serial_number_unavailable);
                return;
            }

            final DatedValue<GimbalStateAdapter> gimbalState = sessionLocal.getGimbalState(0);
            final DatedValue<CameraStateAdapter> cameraState = sessionLocal.getCameraState(0);
            if (gimbalState == null || gimbalState.value == null || cameraState == null || cameraState.value == null) {
                detailsTextView.setText(R.string.CameraFocusCalibration_camera_unavailable);
                return;
            }

            final Double focusRingValue = cameraState.value.getFocusRingValue();
            final Double focusRingMax = cameraState.value.getFocusRingMax();
            if (focusRingValue == null || focusRingMax == null || focusRingMax <= 0) {
                detailsTextView.setText(R.string.CameraFocusCalibration_camera_unavailable);
                return;
            }

            //distance
            if (type == 0) {
                final Location location = state.value.getLocation();
                final Orientation3 orientation = state.value.getOrientation();
                if (location == null || orientation == null) {
                    detailsTextView.setText(R.string.CameraFocusCalibration_location_unavailable);
                    return;
                }

                markReferenceButton.setEnabled(true);

                if (referenceLocation == null) {
                    detailsTextView.setText(R.string.CameraFocusCalibration_details_mark_reference);
                    return;
                }

                final double distance = location.distanceTo(referenceLocation);
                final boolean referenceValid = Math.abs(calibration.distance - distance) < 1
                        && Math.abs(gimbalState.value.getOrientation().getPitch()) < Convert.DegreesToRadians(1)
                        && Math.abs(Convert.AngleDifferenceSigned(orientation.getYaw(), Convert.DegreesToRadians(location.bearingTo(referenceLocation)))) < Convert.DegreesToRadians(15);
                final StringBuilder referenceText = new StringBuilder();
                referenceText.append(Dronelink.getInstance().format("distance", distance, ""));
                referenceText.append(" | ");
                referenceText.append(Dronelink.getInstance().format("angle", gimbalState.value.getOrientation().getPitch(), new Object[]{false}, ""));
                referenceTextView.setText(referenceText.toString());
                referenceTextView.setBackgroundColor(referenceValid ? referenceValidColor : referenceInvalidColor);

                if (!referenceValid) {
                    detailsTextView.setText(getString(R.string.CameraFocusCalibration_details_move_distance, Dronelink.getInstance().format("distance", calibration.distance, "")));
                    return;
                }

                detailsTextView.setText(cameraState.value.isBusy() ? getString(R.string.CameraFocusCalibration_details_busy) : getString(R.string.CameraFocusCalibration_details_focus_distance, cameraFocusRingValues.size() > 0 ? String.valueOf(cameraFocusRingValues.size()) : "--"));
            }
            //altitude
            else if (type == 1) {
                final Location takeoffLocation = state.value.getTakeoffLocation();
                final Location location = state.value.getLocation();
                final Double altitude = state.value.getAltitude();
                if (location == null || takeoffLocation == null || altitude == null) {
                    detailsTextView.setText(R.string.CameraFocusCalibration_location_unavailable);
                    return;
                }

                final double distance = location.distanceTo(takeoffLocation);
                final boolean referenceValid = distance < 2
                        && Math.abs(calibrationLocal.distance - altitude) < 1
                        && Math.abs(gimbalState.value.getOrientation().getPitch() - Convert.DegreesToRadians(-90)) < Convert.DegreesToRadians(1);
                final StringBuilder referenceText = new StringBuilder();
                referenceText.append(getString(R.string.Telemetry_distance_prefix)).append(" ").append(Dronelink.getInstance().format("distance", distance, ""));
                referenceText.append(" | ");
                referenceText.append(getString(R.string.Telemetry_altitude_prefix)).append(" ").append(Dronelink.getInstance().format("altitude", altitude, ""));
                referenceText.append(" | ");
                referenceText.append(Dronelink.getInstance().format("angle", gimbalState.value.getOrientation().getPitch(), new Object[]{false}, ""));
                referenceTextView.setText(referenceText.toString());
                referenceTextView.setBackgroundColor(referenceValid ? referenceValidColor : referenceInvalidColor);

                if (!referenceValid) {
                    detailsTextView.setText(getString(R.string.CameraFocusCalibration_details_move_altitude, Dronelink.getInstance().format("altitude", calibration.distance, "")));
                    return;
                }

                detailsTextView.setText(cameraState.value.isBusy() ? getString(R.string.CameraFocusCalibration_details_busy) : getString(R.string.CameraFocusCalibration_details_focus_altitude, cameraFocusRingValues.size() > 0 ? String.valueOf(cameraFocusRingValues.size()) : "--"));
            }

            if (cameraState.value.isBusy()) {
                previousCameraBusyFocused |= previousCameraFocusRingValue != focusRingValue;
            }
            else {
                if (previousCameraBusyFocused) {
                    cameraFocusRingValues.add(focusRingValue);
                    Collections.sort(cameraFocusRingValues);
                    final double median = cameraFocusRingValues.get((int)(((double)cameraFocusRingValues.size() / 2.0) - 0.5)) / focusRingMax;
                    final List<Double> cameraFocusRingValuesInRange = new ArrayList<>();
                    for (final Double value : cameraFocusRingValues) {
                        if (Math.abs(median - (value / focusRingMax)) <= calibrationLocal.ringValueRange) {
                            cameraFocusRingValuesInRange.add(value);
                        }
                    }

                    if (cameraFocusRingValuesInRange.size() > calibrationLocal.minRingValues) {
                        final double ringValue = cameraFocusRingValuesInRange.get((int)(((double)cameraFocusRingValuesInRange.size() / 2.0) - 0.5));
                        calibrationLocal.ringValue = ringValue;
                        calibrationLocal.droneSerialNumber = sessionLocal.getSerialNumber();
                        Dronelink.getInstance().updateCameraFocusCalibration(calibrationLocal);
                        showToast(getString(R.string.CameraFocusCalibration_finished, (int)ringValue));
                    }
                }
                previousCameraFocusRingValue = focusRingValue;
                previousCameraBusyFocused = false;
            }
        }
    };

    @Override
    public void onOpened(final DroneSession session) {
        this.session = session;
        if (calibration != null) {
            reset();
        }
    }

    @Override
    public void onClosed(final DroneSession session) {
        this.session = null;
    }

    @Override
    public void onRegistered(final String error) {}

    @Override
    public void onMissionLoaded(final MissionExecutor executor) {}

    @Override
    public void onMissionUnloaded(final MissionExecutor executor) {}

    @Override
    public void onFuncLoaded(final FuncExecutor executor) {}

    @Override
    public void onFuncUnloaded(final FuncExecutor executor) {}

    @Override
    public void onModeLoaded(final ModeExecutor executor) {}

    @Override
    public void onModeUnloaded(final ModeExecutor executor) {}

    @Override
    public void onCameraFocusCalibrationRequested(final CameraFocusCalibration value) {
        reset();
        calibration = value;
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onCameraFocusCalibrationUpdated(final CameraFocusCalibration value) {
        calibration = null;
        type = 0;
        referenceLocation = null;
        previousCameraFocusRingValue = null;
        previousCameraBusyFocused = false;
        cameraFocusRingValues.clear();
        getActivity().runOnUiThread(updateViews);
    }

    private void showToast(final String message) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }
}