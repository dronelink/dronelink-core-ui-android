//  CameraFocusCalibrationFragment.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 6/14/21.
//  Copyright © 2021 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.dronelink.core.CameraFile;
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
import com.dronelink.core.command.CommandError;
import com.dronelink.core.kernel.command.Command;
import com.dronelink.core.kernel.command.camera.FocusCameraCommand;
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

public class CameraFocusCalibrationFragment extends Fragment implements Dronelink.Listener, DroneSessionManager.Listener, DroneSession.Listener {
    private DroneSession session;

    private ImageView titleImageView;
    private TextView titleTextView;
    private Button type0Button;
    private Button type1Button;
    private TextView detailsTextView;
    private Button markReferenceButton;
    private Button primaryButton;
    private ImageButton dismissButton;

    private final long updateMillis = 100;
    private Timer updateTimer;

    private final int referenceInvalidColor = Color.parseColor("#f50057");
    private final int referenceValidColor = Color.parseColor("#00c853");
    private final int calibratingColor = Color.parseColor("#f50057");

    private int type = 0;
    private CameraFocusCalibration calibration;
    private boolean calibrating = false;
    private FocusCameraCommand calibrationFocusCommand;
    private Location referenceLocation;
    private List<Double> cameraFocusRingValues = new ArrayList<>();
    private List<Double> getCameraFocusRingValuesInRange(final CameraFocusCalibration calibration, final CameraStateAdapter cameraState, final double focusRingMax) {
        final List<Double> cameraFocusRingValuesInRange = new ArrayList<>();
        if (cameraFocusRingValues.size() > 0) {
            final double median = cameraFocusRingValues.get((int) (((double) cameraFocusRingValues.size() / 2.0) - 0.5)) / focusRingMax;
            for (final Double value : cameraFocusRingValues) {
                if (Math.abs(median - (value / focusRingMax)) <= calibration.ringValueRange) {
                    cameraFocusRingValuesInRange.add(value);
                }
            }
        }
        return cameraFocusRingValuesInRange;
    }

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
        primaryButton = getView().findViewById(R.id.primaryButton);
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

        primaryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                onPrimary();
            }
        });
    }

    public void setType(final int type) {
        this.type = type;
        reset();
    }

    public void reset() {
        referenceLocation = null;
        cancelCalibration();

        final DroneSession sessionLocal = session;
        if (sessionLocal != null) {
            final OrientationGimbalCommand command = new OrientationGimbalCommand();
            command.orientation.x = type == 0 ? Convert.DegreesToRadians(-90) : 0;
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
        Dronelink.getInstance().removeListener(this);
        final DroneSessionManager manager = Dronelink.getInstance().getTargetDroneSessionManager();
        if (manager != null) {
            manager.removeListener(this);
        }
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
        cancelCalibration();
    }

    private void onPrimary() {
        if (!calibrating) {
            if (isReferenceValid()) {
                calibrating = true;
                startFocus();
                getActivity().runOnUiThread(updateViews);
            }
            return;
        }

        cancelCalibration();
    }

    private void cancelCalibration() {
        calibrating = false;
        calibrationFocusCommand = null;
        cameraFocusRingValues.clear();
        getActivity().runOnUiThread(updateViews);
    }

    private boolean isReferenceValid() {
        final CameraFocusCalibration calibrationLocal = calibration;
        if (calibrationLocal == null) {
            return false;
        }

        final DroneSession sessionLocal = session;
        if (sessionLocal == null) {
            return false;
        }

        final DatedValue<DroneStateAdapter> state = sessionLocal.getState();
        if (state == null || state.value == null) {
            return false;
        }

        final DatedValue<GimbalStateAdapter> gimbalState = sessionLocal.getGimbalState(0);
        final DatedValue<CameraStateAdapter> cameraState = sessionLocal.getCameraState(0);
        if (gimbalState == null || gimbalState.value == null || cameraState == null || cameraState.value == null) {
            return false;
        }

        //altitude
        if (type == 0) {
            final Location takeoffLocation = state.value.getTakeoffLocation();
            final Location location = state.value.getLocation();
            final Double altitude = state.value.getAltitude();
            if (location == null || takeoffLocation == null || altitude == null) {
                return false;
            }

            final double distance = location.distanceTo(takeoffLocation);
            return distance < 2
                    && Math.abs(calibrationLocal.distance - altitude) < 1
                    && Math.abs(gimbalState.value.getOrientation().getPitch() - Convert.DegreesToRadians(-90)) < Convert.DegreesToRadians(1);
        }

        //distance
        if (type == 1) {
            final Location location = state.value.getLocation();
            final Orientation3 orientation = state.value.getOrientation();
            if (location == null || orientation == null) {
                return false;
            }

            if (referenceLocation == null) {
                return false;
            }

            final double distance = location.distanceTo(referenceLocation);
            return Math.abs(calibration.distance - distance) < 1
                    && Math.abs(gimbalState.value.getOrientation().getPitch()) < Convert.DegreesToRadians(1)
                    && Math.abs(Convert.AngleDifferenceSigned(orientation.getYaw(), Convert.DegreesToRadians(location.bearingTo(referenceLocation)))) < Convert.DegreesToRadians(15);
        }

        return false;
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
            type0Button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(type == 0 ? R.color.darkGray : R.color.lightGray)));
            type1Button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(type == 1 ? R.color.darkGray : R.color.lightGray)));
            markReferenceButton.setVisibility(type == 0 || calibrating ? View.INVISIBLE : View.VISIBLE);

            final ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams)primaryButton.getLayoutParams();
            lp.bottomToTop = R.id.nextButton;
            if (type == 1 && !calibrating) {
                lp.leftMargin = 12;
                lp.startToEnd = R.id.markReferenceButton;
                lp.startToStart = -1;
            }
            else {
                lp.leftMargin = 0;
                lp.startToStart = R.id.type0Button;
                lp.startToEnd = -1;
            }
            primaryButton.setLayoutParams(lp);
        }
    };

    private void updateTimer() {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(update);
        }
    }

    private Runnable update = new Runnable() {
        public void run() {
            final CameraFocusCalibration calibrationLocal = calibration;
            if (calibrationLocal == null) {
                return;
            }

            markReferenceButton.setEnabled(false);
            primaryButton.setVisibility(View.INVISIBLE);

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

            final Double focusRingMax = cameraState.value.getFocusRingMax();
            if (focusRingMax == null || focusRingMax <= 0) {
                detailsTextView.setText(R.string.CameraFocusCalibration_camera_unavailable);
                return;
            }

            final boolean referenceValid = isReferenceValid();
            if (!referenceValid && calibrating) {
                cancelCalibration();
            }

            //altitude
            if (type == 0) {
                final Location takeoffLocation = state.value.getTakeoffLocation();
                final Location location = state.value.getLocation();
                final Double altitude = state.value.getAltitude();
                if (location == null || takeoffLocation == null) {
                    detailsTextView.setText(R.string.CameraFocusCalibration_location_unavailable);
                    return;
                }

                if (referenceValid) {
                    primaryButton.setText(calibrating ? R.string.cancel : R.string.CameraFocusCalibration_start);
                }
                else {
                    final double distance = location.distanceTo(takeoffLocation);
                    final StringBuilder referenceText = new StringBuilder();
                    referenceText.append(getString(R.string.Telemetry_distance_prefix)).append(" ").append(Dronelink.getInstance().format("distance", distance, ""));
                    referenceText.append(" | ");
                    referenceText.append(getString(R.string.Telemetry_altitude_prefix)).append(" ").append(Dronelink.getInstance().format("altitude", altitude, ""));
                    referenceText.append(" | ");
                    referenceText.append(Dronelink.getInstance().format("angle", gimbalState.value.getOrientation().getPitch(), new Object[]{false}, ""));
                    primaryButton.setText(referenceText.toString());
                }
                primaryButton.setBackgroundColor(calibrating ? calibratingColor : referenceValid ? referenceValidColor : referenceInvalidColor);
                primaryButton.setVisibility(View.VISIBLE);

                if (!referenceValid) {
                    detailsTextView.setText(getString(R.string.CameraFocusCalibration_details_move_altitude, Dronelink.getInstance().format("altitude", calibration.distance, "")));
                    return;
                }
            }
            //distance
            else if (type == 1) {
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
                if (referenceValid) {
                    primaryButton.setText(calibrating ? R.string.cancel : R.string.CameraFocusCalibration_start);
                }
                else {
                    final StringBuilder referenceText = new StringBuilder();
                    referenceText.append(Dronelink.getInstance().format("distance", distance, ""));
                    referenceText.append(" | ");
                    referenceText.append(Dronelink.getInstance().format("angle", gimbalState.value.getOrientation().getPitch(), new Object[]{false}, ""));
                    primaryButton.setText(referenceText.toString());
                }
                primaryButton.setBackgroundColor(calibrating ? calibratingColor : referenceValid ? referenceValidColor : referenceInvalidColor);
                primaryButton.setVisibility(View.VISIBLE);

                if (!referenceValid) {
                    detailsTextView.setText(getString(R.string.CameraFocusCalibration_details_move_distance, Dronelink.getInstance().format("distance", calibration.distance, "")));
                    return;
                }
            }

            final List<Double> cameraFocusRingValuesInRange = getCameraFocusRingValuesInRange(calibrationLocal, cameraState.value, focusRingMax);
            detailsTextView.setText(calibrating
                    ? getString(R.string.CameraFocusCalibration_details_busy, Dronelink.getInstance().format("percent", (double)cameraFocusRingValuesInRange.size() / calibration.minRingValues, ""))
                    : getString(R.string.CameraFocusCalibration_details_ready));

        }
    };

    private void startFocus() {
        final DroneSession sessionLocal = session;
        if (sessionLocal == null) {
            return;
        }

        if (calibrationFocusCommand == null) {
            calibrationFocusCommand = new FocusCameraCommand();
            try {
                sessionLocal.addCommand(calibrationFocusCommand);
            } catch (final Exception e) {}
        }
    }

    @Override
    public void onOpened(final DroneSession session) {
        this.session = session;
        session.addListener(this);
        if (calibration != null) {
            reset();
        }
    }

    @Override
    public void onClosed(final DroneSession session) {
        this.session = null;
        session.removeListener(this);
    }

    @Override
    public void onDroneSessionManagerAdded(final DroneSessionManager droneSessionManager) {}

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
        cancelCalibration();
    }

    private void showToast(final String message) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onInitialized(final DroneSession session) {}

    @Override
    public void onLocated(final DroneSession session) {}

    @Override
    public void onMotorsChanged(final DroneSession session, final boolean value) {}

    @Override
    public void onCommandExecuted(final DroneSession session, final Command command) {}

    @Override
    public void onCommandFinished(final DroneSession session, final Command command, final CommandError error) {
        final FocusCameraCommand commandCurrent = calibrationFocusCommand;
        if (commandCurrent != null && commandCurrent.id == command.id) {
            calibrationFocusCommand = null;
            if (error != null) {
                showToast(error.description);
                cancelCalibration();
                return;
            }

            checkCalibrationFinished(0);
        }
    }

    @Override
    public void onCameraFileGenerated(final DroneSession session, final CameraFile file) {}

    @Override
    public void onVideoFeedSourceUpdated(final DroneSession session, final Integer channel) {}

    private void checkCalibrationFinished(final int attempt) {
        if (attempt > 5) {
            showToast(getString(R.string.CameraFocusCalibration_finish_error));
            return;
        }

        final CameraFocusCalibration calibrationLocal = calibration;
        final DroneSession sessionLocal = session;
        if (calibrationLocal == null || sessionLocal == null || sessionLocal.getSerialNumber() == null) {
            showToast(getString(R.string.CameraFocusCalibration_finish_error));
            return;
        }

        final DatedValue<CameraStateAdapter> cameraState = sessionLocal.getCameraState(0);
        if (cameraState == null || cameraState.value == null) {
            showToast(getString(R.string.CameraFocusCalibration_finish_error));
            return;
        }

        final Double focusRingValue = cameraState.value.getFocusRingValue();
        final Double focusRingMax = cameraState.value.getFocusRingMax();
        if (focusRingValue == null || focusRingMax == null || focusRingMax <= 0) {
            showToast(getString(R.string.CameraFocusCalibration_finish_error));
            return;
        }

        if (cameraState.value.isBusy()) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    checkCalibrationFinished(attempt + 1);
                }
            }, 1000);
            return;
        }

        cameraFocusRingValues.add(focusRingValue);
        Collections.sort(cameraFocusRingValues);
        final List<Double> cameraFocusRingValuesInRange = getCameraFocusRingValuesInRange(calibrationLocal, cameraState.value, focusRingMax);
        if (cameraFocusRingValuesInRange.size() > calibrationLocal.minRingValues) {
            final double ringValue = cameraFocusRingValuesInRange.get((int)(((double)cameraFocusRingValuesInRange.size() / 2.0) - 0.5));
            calibrationLocal.ringValue = ringValue;
            calibrationLocal.droneSerialNumber = sessionLocal.getSerialNumber();
            Dronelink.getInstance().updateCameraFocusCalibration(calibrationLocal);
            showToast(getString(R.string.CameraFocusCalibration_finished));
            //showToast(getString(R.string.CameraFocusCalibration_finished) + " (" + (int)ringValue + ")");
            return;
        }

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                startFocus();
            }
        }, 1500);
    }
}