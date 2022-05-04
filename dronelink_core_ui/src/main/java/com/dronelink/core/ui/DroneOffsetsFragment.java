//  DroneOffsetsFragment.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 1/13/21.
//  Copyright © 2020 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dronelink.core.CameraFile;
import com.dronelink.core.Convert;
import com.dronelink.core.DatedValue;
import com.dronelink.core.DroneOffsets;
import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.MissionExecutor;
import com.dronelink.core.adapters.DroneStateAdapter;
import com.dronelink.core.adapters.GimbalAdapter;
import com.dronelink.core.adapters.RemoteControllerStateAdapter;
import com.dronelink.core.command.CommandError;
import com.dronelink.core.kernel.command.Command;
import com.dronelink.core.kernel.command.camera.ExposureCompensationStepCameraCommand;
import com.dronelink.core.kernel.command.gimbal.OrientationGimbalCommand;
import com.dronelink.core.kernel.core.Vector2;

import java.util.Timer;
import java.util.TimerTask;

public class DroneOffsetsFragment extends Fragment implements DroneSessionManager.Listener, DroneSession.Listener {
    public enum Style {
        ALT_YAW,
        POSITION
    }

    public boolean stylesEnabled = true;
    private Style style = Style.ALT_YAW;

    private DroneSession session;

    private boolean debug = false;

    private Button styleButton;
    private Button style0Button;
    private Button style1Button;
    private TextView detailsTextView;
    private ImageButton rcInputsToggleButton;
    private ImageButton moreButton;
    private ImageButton clearButton;
    private ImageButton leftButton;
    private ImageButton rightButton;
    private ImageButton upButton;
    private ImageButton downButton;
    private ImageButton c1Button;
    private ImageButton c2Button;
    private TextView cTextView;

    private final long updateMillis = 250;
    private Timer updateTimer;
    private boolean rcInputsEnabled = false;
    private boolean rollVisible = false;
    private int rollValue = 0;
    private SensorManager sensorManager;
    private Sensor pressureSensor;
    private boolean relativeAltitudeUpdating = false;
    private boolean relativeAltitudeActive = false;
    private Float relativeAltitudeStartingPressure;
    private final SensorEventListener pressureSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(final SensorEvent sensorEvent) {
            final float pressure = sensorEvent.values[0];
            final Float relativeAltitudeStartingPressureLocal = relativeAltitudeStartingPressure;
            if (relativeAltitudeStartingPressureLocal == null) {
                relativeAltitudeStartingPressure = pressure;
                return;
            }

            if (!relativeAltitudeActive) {
                return;
            }

            Dronelink.getInstance().droneOffsets.droneAltitude = -SensorManager.getAltitude(relativeAltitudeStartingPressureLocal, pressure);
        }

        @Override
        public void onAccuracyChanged(final Sensor sensor, final int i) {}
    };

    public DroneOffsetsFragment() {}

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sensorManager = (SensorManager)getActivity().getSystemService(Context.SENSOR_SERVICE);
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_drone_offsets, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable final Bundle savedInstanceState) {
        styleButton = getView().findViewById(R.id.styleButton);
        style0Button = getView().findViewById(R.id.style0Button);
        style1Button = getView().findViewById(R.id.style1Button);

        detailsTextView = getView().findViewById(R.id.detailsTextView);

        style0Button = getView().findViewById(R.id.style0Button);
        style0Button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setStyle(Style.ALT_YAW);
            }
        });

        style1Button = getView().findViewById(R.id.style1Button);
        style1Button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setStyle(Style.POSITION);
            }
        });

        rcInputsToggleButton = getView().findViewById(R.id.rcInputsToggleButton);
        rcInputsToggleButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                rcInputsEnabled = !rcInputsEnabled;
                if (rcInputsEnabled) {
                    showToast(getString(R.string.DroneOffsets_rc_inputs));
                }
                updateTimer();
            }
        });

        moreButton = getView().findViewById(R.id.moreButton);
        moreButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final PopupMenu menu = new PopupMenu(getContext(), moreButton);
                menu.getMenu().add(getString(R.string.DroneOffsets_more_levelGimbal));
                menu.getMenu().add(getString(R.string.DroneOffsets_more_nadirGimbal));
                menu.getMenu().add(getString(R.string.DroneOffsets_more_resetGimbal));
                menu.getMenu().add(getString(R.string.DroneOffsets_more_rollTrim));
                final DroneSession sessionLocal = session;
                if (sessionLocal != null && sessionLocal.getSerialNumber() != null) {
                    menu.getMenu().add(getString(R.string.DroneOffsets_more_clearCameraFocusCalibrations));
                }

                menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(final MenuItem menuItem) {
                        if (menuItem.getTitle() == getString(R.string.DroneOffsets_more_rollTrim)) {
                            updateRollVisible(true);
                            return true;
                        }

                        final DroneSession sessionLocal = session;
                        if (sessionLocal == null) {
                            return true;
                        }

                        if (menuItem.getTitle() == getString(R.string.DroneOffsets_more_levelGimbal)) {
                            final OrientationGimbalCommand command = new OrientationGimbalCommand();
                            command.orientation.x = Convert.DegreesToRadians(0);
                            try {
                                sessionLocal.addCommand(command);
                            } catch (final Exception e) {}
                            return true;
                        }

                        if (menuItem.getTitle() == getString(R.string.DroneOffsets_more_nadirGimbal)) {
                            final OrientationGimbalCommand command = new OrientationGimbalCommand();
                            command.orientation.x = Convert.DegreesToRadians(-90);
                            try {
                                sessionLocal.addCommand(command);
                            } catch (final Exception e) {}
                            return true;
                        }

                        if (menuItem.getTitle() == getString(R.string.DroneOffsets_more_resetGimbal)) {
                            final GimbalAdapter gimbal = sessionLocal.getDrone().getGimbal(0);
                            if (gimbal != null) {
                                gimbal.reset();
                            }
                        }

                        if (menuItem.getTitle() == getString(R.string.DroneOffsets_more_clearCameraFocusCalibrations)) {
                            final int cleared = Dronelink.getInstance().clearCameraFocusCalibrations(sessionLocal.getSerialNumber());
                            showToast(getString(R.string.DroneOffsets_more_clearCameraFocusCalibrations_finished, cleared));
                        }

                        return true;
                    }
                });
                menu.show();
            }
        });

        clearButton = getView().findViewById(R.id.clearButton);
        clearButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (rollVisible) {
                    updateRollValue(0);
                }
                else {
                    final DroneOffsets offsets = Dronelink.getInstance().droneOffsets;
                    switch (style) {
                        case ALT_YAW:
                            relativeAltitudeActive = false;
                            offsets.droneAltitude = 0;
                            offsets.droneYaw = 0;
                            break;

                        case POSITION:
                            offsets.droneCoordinate = new Vector2();
                            break;
                    }
                }

                updateTimer();
            }
        });

        leftButton = getView().findViewById(R.id.leftButton);
        leftButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.darkGray)));
        leftButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final DroneSession sessionLocal = session;
                if (sessionLocal == null) {
                    return;
                }

                if (rollVisible) {
                    updateRollValue(rollValue - 1);
                }
                else {
                    final DroneOffsets offsets = Dronelink.getInstance().droneOffsets;
                    switch (style) {
                        case ALT_YAW:
                            offsets.droneYaw += Convert.DegreesToRadians(-3.0);
                            break;

                        case POSITION:
                            final DatedValue<DroneStateAdapter> state = sessionLocal.getState();
                            if (state == null || state.value == null) {
                                return;
                            }

                            offsets.droneCoordinate = offsets.droneCoordinate.add(
                                    new Vector2(state.value.getOrientation().getYaw() - (Math.PI / 2),
                                    Convert.FeetToMeters(1.0)));
                            break;
                    }

                    updateTimer();
                }
            }
        });

        rightButton = getView().findViewById(R.id.rightButton);
        rightButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.darkGray)));
        rightButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final DroneSession sessionLocal = session;
                if (sessionLocal == null) {
                    return;
                }

                if (rollVisible) {
                    updateRollValue(rollValue + 1);
                }
                else {
                    final DroneOffsets offsets = Dronelink.getInstance().droneOffsets;
                    switch (style) {
                        case ALT_YAW:
                            offsets.droneYaw += Convert.DegreesToRadians(3.0);
                            break;

                        case POSITION:
                            final DatedValue<DroneStateAdapter> state = sessionLocal.getState();
                            if (state == null || state.value == null) {
                                return;
                            }

                            offsets.droneCoordinate = offsets.droneCoordinate.add(
                                    new Vector2(state.value.getOrientation().getYaw() + (Math.PI / 2),
                                            Convert.FeetToMeters(1.0)));
                            break;
                    }

                    updateTimer();
                }
            }
        });

        upButton = getView().findViewById(R.id.upButton);
        upButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.darkGray)));
        upButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final DroneSession sessionLocal = session;
                if (sessionLocal == null) {
                    return;
                }

                final DroneOffsets offsets = Dronelink.getInstance().droneOffsets;
                switch (style) {
                    case ALT_YAW:
                        incrementDroneAltitudeOffset(Convert.FeetToMeters(1.0));
                        break;

                    case POSITION:
                        final DatedValue<DroneStateAdapter> state = sessionLocal.getState();
                        if (state == null || state.value == null) {
                            return;
                        }

                        offsets.droneCoordinate = offsets.droneCoordinate.add(
                                new Vector2(state.value.getOrientation().getYaw(),
                                        Convert.FeetToMeters(1.0)));
                        break;
                }

                updateTimer();
            }
        });

        downButton = getView().findViewById(R.id.downButton);
        downButton.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.darkGray)));
        downButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final DroneSession sessionLocal = session;
                if (sessionLocal == null) {
                    return;
                }

                final DroneOffsets offsets = Dronelink.getInstance().droneOffsets;
                switch (style) {
                    case ALT_YAW:
                        incrementDroneAltitudeOffset(Convert.FeetToMeters(-1.0));
                        break;

                    case POSITION:
                        final DatedValue<DroneStateAdapter> state = sessionLocal.getState();
                        if (state == null || state.value == null) {
                            return;
                        }

                        offsets.droneCoordinate = offsets.droneCoordinate.add(
                                new Vector2(state.value.getOrientation().getYaw() + Math.PI,
                                        Convert.FeetToMeters(1.0)));
                        break;
                }

                updateTimer();
            }
        });

        c1Button = getView().findViewById(R.id.c1Button);
        c1Button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final DroneSession sessionLocal = session;
                if (sessionLocal == null) {
                    return;
                }

                final DatedValue<DroneStateAdapter> state = sessionLocal.getState();
                if (state == null || state.value == null) {
                    return;
                }

                final DroneOffsets offsets = Dronelink.getInstance().droneOffsets;
                switch (style) {
                    case ALT_YAW:
                        if (relativeAltitudeActive) {
                            showToast(getString(R.string.DroneOffsets_relative_altitude_disabled));
                        }
                        else {
                            showToast(getString(R.string.DroneOffsets_relative_altitude_enabled));
                        }
                        relativeAltitudeActive = !relativeAltitudeActive;
                        break;

                    case POSITION:
                        offsets.droneCoordinateReference = state.value.getLocation();
                        break;
                }

                updateTimer();
            }
        });

        c2Button = getView().findViewById(R.id.c2Button);
        c2Button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (rollVisible) {
                    updateRollVisible(false);
                    return;
                }

                final DroneSession sessionLocal = session;
                if (sessionLocal == null) {
                    return;
                }

                final DatedValue<DroneStateAdapter> state = sessionLocal.getState();
                if (state == null || state.value == null) {
                    return;
                }

                final DroneOffsets offsets = Dronelink.getInstance().droneOffsets;
                switch (style) {
                    case ALT_YAW:
                        Double droneAltitudeReference = offsets.droneAltitudeReference;
                        if (droneAltitudeReference != null) {
                            offsets.droneAltitude = droneAltitudeReference - state.value.getAltitude();
                        }
                        break;

                    case POSITION:
                        Location current = state.value.getLocation();
                        Location droneCoordinateReference = offsets.droneCoordinateReference;
                        if (current != null && droneCoordinateReference != null) {
                            offsets.droneCoordinate = new Vector2(Convert.DegreesToRadians(droneCoordinateReference.bearingTo(current)), droneCoordinateReference.distanceTo(current));
                        }
                        break;
                }

                updateTimer();
            }
        });

        cTextView = getView().findViewById(R.id.cLabel);

        updateRollVisible(rollVisible);
    }

    private void incrementDroneAltitudeOffset(final double value) {
        relativeAltitudeActive = false;
        Dronelink.getInstance().droneOffsets.droneAltitude += value;
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
        stopRelativeAltitudeUpdates();
        Dronelink.getInstance().getTargetDroneSessionManager().removeListener(this);
        if (session != null) {
            session.removeListener(this);
        }
    }

    private void stopRelativeAltitudeUpdates() {
        if (relativeAltitudeUpdating) {
            sensorManager.unregisterListener(pressureSensorEventListener);
            relativeAltitudeUpdating = false;
            relativeAltitudeActive = false;
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

            final MissionExecutor missionExecutor = Dronelink.getInstance().getMissionExecutor();
            final boolean missionEngaged = missionExecutor != null && missionExecutor.isEngaged();
            final DroneOffsets offsets = Dronelink.getInstance().droneOffsets;
            final DroneSession sessionLocal = session;

            upButton.setEnabled(sessionLocal != null);
            downButton.setEnabled(upButton.isEnabled());
            leftButton.setEnabled(upButton.isEnabled());
            rightButton.setEnabled(upButton.isEnabled());
            rcInputsToggleButton.setImageTintList(ColorStateList.valueOf(getResources().getColor(rcInputsEnabled ? R.color.secondary : R.color.white)));

            if (rollVisible) {
                c1Button.setVisibility(View.INVISIBLE);
                leftButton.setVisibility(View.VISIBLE);
                rightButton.setVisibility(View.VISIBLE);
                upButton.setVisibility(View.INVISIBLE);
                downButton.setVisibility(View.INVISIBLE);
                rcInputsToggleButton.setVisibility(View.INVISIBLE);
                moreButton.setVisibility(View.INVISIBLE);
                clearButton.setVisibility(rollValue == 0 ? View.INVISIBLE : View.VISIBLE);
                detailsTextView.setText(clearButton.getVisibility() != View.VISIBLE ? "" : (((double)rollValue) / 10) + "");
                c2Button.setVisibility(upButton.isEnabled() ? View.VISIBLE : View.INVISIBLE);
            }
            else {
                String cText = "";
                switch (style) {
                    case ALT_YAW:
                        leftButton.setVisibility(View.VISIBLE);
                        rightButton.setVisibility(View.VISIBLE);
                        upButton.setVisibility(View.VISIBLE);
                        downButton.setVisibility(View.VISIBLE);

                        final StringBuilder details = new StringBuilder();
                        if (offsets.droneYaw != 0) {
                            details.append(Dronelink.getInstance().format("angle", offsets.droneYaw, new Object[] { false }, ""));
                        }

                        if (offsets.droneAltitude != 0) {
                            if (details.length() > 0) {
                                details.append(" / ");
                            }
                            details.append(Dronelink.getInstance().format("altitude", offsets.droneAltitude, ""));
                        }

                        rcInputsToggleButton.setVisibility(sessionLocal == null || sessionLocal.getRemoteControllerState(0) == null ? View.INVISIBLE : View.VISIBLE);
                        moreButton.setVisibility(sessionLocal == null ? View.INVISIBLE : View.VISIBLE);
                        clearButton.setVisibility(details.length() == 0 ? View.INVISIBLE : View.VISIBLE);
                        detailsTextView.setText(details.toString());

                        Double altitude;
                        if (sessionLocal != null) {
                            final Double reference = offsets.droneAltitudeReference;
                            final DatedValue<DroneStateAdapter> state = sessionLocal.getState();
                            if (state != null && state.value != null) {
                                if (reference != null) {
                                    altitude = state.value.getAltitude();
                                    cText = Dronelink.getInstance().format("distance", reference - altitude, "");
                                }
                                c1Button.setVisibility(relativeAltitudeUpdating ? View.VISIBLE : View.INVISIBLE);
                                c1Button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(relativeAltitudeActive ? R.color.amber_a700 : R.color.darkGray)));
                            }
                            else {
                                c1Button.setVisibility(View.INVISIBLE);
                            }
                        }
                        else {
                            c1Button.setVisibility(View.INVISIBLE);
                        }
                        cTextView.setText(cText);

                        c2Button.setVisibility(debug && !missionEngaged && !cText.isEmpty() ? View.VISIBLE : View.INVISIBLE);
                        break;

                    case POSITION:
                        leftButton.setVisibility(View.VISIBLE);
                        rightButton.setVisibility(View.VISIBLE);
                        upButton.setVisibility(View.VISIBLE);
                        downButton.setVisibility(View.VISIBLE);
                        rcInputsToggleButton.setVisibility(session == null || sessionLocal.getRemoteControllerState(0) == null ? View.INVISIBLE : View.VISIBLE);
                        moreButton.setVisibility(session == null || !stylesEnabled ? View.INVISIBLE : View.VISIBLE);
                        clearButton.setVisibility(offsets.droneCoordinate.magnitude == 0 ? View.INVISIBLE : View.VISIBLE);
                        detailsTextView.setText(clearButton.getVisibility() != View.VISIBLE ? "" : displayVector(offsets.droneCoordinate));

                        Location location = null;
                        if (sessionLocal != null) {
                            final Location reference = offsets.droneCoordinateReference;
                            final DatedValue<DroneStateAdapter> state = sessionLocal.getState();
                            if (reference != null && state != null && state.value != null) {
                                location = state.value.getLocation();
                                if (location != null) {
                                    cText = displayVector(new Vector2(Convert.DegreesToRadians(reference.bearingTo(location)), reference.distanceTo(location)));
                                }
                            }
                        }
                        cTextView.setText(cText);

                        c1Button.setVisibility(debug && location != null && !missionEngaged ? View.VISIBLE : View.INVISIBLE);
                        c2Button.setVisibility(!missionEngaged && !cText.isEmpty() ? View.VISIBLE : View.INVISIBLE);
                        break;
                }

                if (rcInputsEnabled && missionEngaged && sessionLocal != null) {
                    final DatedValue<RemoteControllerStateAdapter> remoteControllerState = sessionLocal.getRemoteControllerState(0);
                    if (remoteControllerState != null && remoteControllerState.value != null) {
                        final double deadband = 0.15;

                        switch (style) {
                            case ALT_YAW:
                                final double yawPercent = remoteControllerState.value.getLeftStick().x;
                                if (Math.abs(yawPercent) > deadband) {
                                    offsets.droneYaw += Convert.DegreesToRadians(1.0 * yawPercent);
                                }

                                final double altitudePercent = remoteControllerState.value.getLeftStick().y;
                                if (Math.abs(altitudePercent) > deadband) {
                                    incrementDroneAltitudeOffset(Convert.FeetToMeters(0.5 * altitudePercent));
                                }
                                break;

                            case POSITION:
                                final double positionXPercent = remoteControllerState.value.getRightStick().x;
                                if (Math.abs(positionXPercent) > deadband) {
                                    final DatedValue<DroneStateAdapter> state = sessionLocal.getState();
                                    if (state == null || state.value == null) {
                                        return;
                                    }

                                    offsets.droneCoordinate = offsets.droneCoordinate.add(new Vector2(
                                            state.value.getOrientation().getYaw() + (positionXPercent >= 0 ? (Math.PI / 2) : -(Math.PI / 2)),
                                            Convert.FeetToMeters(0.4 * Math.abs(positionXPercent))));
                                }

                                final double positionYPercent = remoteControllerState.value.getRightStick().y;
                                if (Math.abs(positionYPercent) > deadband) {
                                    final DatedValue<DroneStateAdapter> state = sessionLocal.getState();
                                    if (state == null || state.value == null) {
                                        return;
                                    }

                                    offsets.droneCoordinate = offsets.droneCoordinate.add(new Vector2(
                                            state.value.getOrientation().getYaw() + (positionYPercent >= 0 ? 0 : Math.PI),
                                            Convert.FeetToMeters(0.4 * Math.abs(positionYPercent))));
                                }
                                break;
                        }
                    }
                }
            }
        }
    };

    public void setStyle(final Style style) {
        this.style = style;
        updateRollVisible(false);
    }

    private void updateRollVisible(final boolean visible) {
        rollVisible = visible;

        styleButton.setVisibility(stylesEnabled ? View.INVISIBLE : View.VISIBLE);
        styleButton.setEnabled(false);
        styleButton.setText(style == Style.ALT_YAW ? (rollVisible ? R.string.DroneOffsets_roll_trim : R.string.DroneOffsets_style_altYaw) : R.string.DroneOffsets_style_position);
        style0Button.setText(rollVisible ? R.string.DroneOffsets_roll_trim : R.string.DroneOffsets_style_altYaw);
        style0Button.setVisibility(stylesEnabled ? View.VISIBLE : View.INVISIBLE);
        style0Button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(style == Style.ALT_YAW ? R.color.lightGray : R.color.darkGray)));
        style1Button.setVisibility(stylesEnabled ? View.VISIBLE : View.INVISIBLE);
        style1Button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(style == Style.POSITION ? R.color.lightGray : R.color.darkGray)));

        if (rollVisible) {
            leftButton.setImageDrawable(getResources().getDrawable(R.drawable.baseline_rotate_left_white_48));
            rightButton.setImageDrawable(getResources().getDrawable(R.drawable.baseline_rotate_right_white_48));
            c2Button.setImageDrawable(getResources().getDrawable(R.drawable.baseline_check_white_48));
            c2Button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.lightBlue_a400)));
        }
        else {
            leftButton.setImageDrawable(getResources().getDrawable(R.drawable.baseline_arrow_left_white_48));
            rightButton.setImageDrawable(getResources().getDrawable(R.drawable.baseline_arrow_right_white_48));
            c1Button.setImageDrawable(getResources().getDrawable(R.drawable.baseline_timeline_white_48));
            c2Button.setImageDrawable(getResources().getDrawable(R.drawable.baseline_arrow_upward_white_48));
            c2Button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(style == Style.ALT_YAW ? R.color.purple_a400 : R.color.pink_a400)));
        }

        updateTimer();
    }

    private void updateRollValue(final int value) {
        rollValue = value;
        final DroneSession sessionLocal = session;
        if (sessionLocal != null) {
            final GimbalAdapter gimbal = sessionLocal.getDrone().getGimbal(0);
            if (gimbal != null) {
                gimbal.fineTuneRoll(Convert.DegreesToRadians((double)rollValue / 10));
            }
        }
        updateTimer();
    }

    private String displayVector(final Vector2 vector) {
        final String angle = Dronelink.getInstance().format("angle", vector.direction, "");
        final String distance = Dronelink.getInstance().format("distance", vector.magnitude, "");
        return angle + " → " + distance;
    }

    @Override
    public void onOpened(final DroneSession session) {
        this.session = session;
        session.addListener(this);
    }

    @Override
    public void onClosed(final DroneSession session) {
        this.session = null;
        stopRelativeAltitudeUpdates();
    }

    @Override
    public void onInitialized(final DroneSession session) {}

    @Override
    public void onLocated(final DroneSession session) {}

    @Override
    public void onMotorsChanged(final  DroneSession session, final boolean value) {
        if (pressureSensor == null) {
            return;
        }

        if (value) {
            //disabling this for now until we do more testing
//            relativeAltitudeStartingPressure = null;
//            relativeAltitudeUpdating = true;
//            sensorManager.registerListener(pressureSensorEventListener, pressureSensor, SensorManager.SENSOR_DELAY_UI);
        }
        else {
            stopRelativeAltitudeUpdates();
        }
    }

    @Override
    public void onCommandExecuted(final DroneSession session, final Command command) {}

    @Override
    public void onCommandFinished(final DroneSession session, final Command command, final CommandError error) {}

    @Override
    public void onCameraFileGenerated(final DroneSession session, final CameraFile file) {}

    @Override
    public void onVideoFeedSourceUpdated(final DroneSession session, final Integer channel) {}

    private void showToast(final String message) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
            }
        });
    }
}