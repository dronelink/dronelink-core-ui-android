//  CameraOffsetsFragment.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 1/13/21.
//  Copyright © 2020 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dronelink.core.CameraFile;
import com.dronelink.core.DatedValue;
import com.dronelink.core.DroneOffsets;
import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.Kernel;
import com.dronelink.core.MissionExecutor;
import com.dronelink.core.adapters.CameraStateAdapter;
import com.dronelink.core.adapters.RemoteControllerStateAdapter;
import com.dronelink.core.command.CommandError;
import com.dronelink.core.kernel.command.Command;
import com.dronelink.core.kernel.command.camera.ExposureCompensationStepCameraCommand;
import com.dronelink.core.kernel.core.enums.CameraExposureCompensation;
import com.google.gson.Gson;

import java.util.Timer;
import java.util.TimerTask;

public class CameraOffsetsFragment extends Fragment implements DroneSessionManager.Listener, DroneSession.Listener {
    private static final Gson gson = Kernel.createGson();
    private DroneSession session;
    private ExposureCompensationStepCameraCommand exposureCommand = null;

    private ImageButton c1Button;
    private ImageButton c2Button;
    private TextView cTextView;

    private final long updateMillis = 500;
    private Timer updateTimer;
    private final long listenRCButtonsMillis = 100;
    private Timer listenRCButtonsTimer;
    private boolean c1PressedPrevious = false;
    private boolean c2PressedPrevious = false;
    private Integer evStepsPending;
    private Timer evStepsTimer;

    public CameraOffsetsFragment() {}

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera_offsets, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable final Bundle savedInstanceState) {
        c1Button = getView().findViewById(R.id.c1Button);
        c1Button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.darkGray)));
        c1Button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (exposureCommand != null) {
                    return;
                }

                onEV(-1);
            }
        });

        c2Button = getView().findViewById(R.id.c2Button);
        c2Button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.darkGray)));
        c2Button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (exposureCommand != null) {
                    return;
                }

                onEV(1);
            }
        });

        cTextView = getView().findViewById(R.id.cTextView);

        updateTimer = new Timer();
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateTimer();
            }
        }, 0, updateMillis);

        listenRCButtonsTimer = new Timer();
        listenRCButtonsTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                listenRCButtonsTimer();
            }
        }, 0, listenRCButtonsMillis);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dronelink.getInstance().getSessionManager().addListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (updateTimer != null) {
            updateTimer.cancel();
        }

        if (listenRCButtonsTimer != null) {
            listenRCButtonsTimer.cancel();
        }
        Dronelink.getInstance().getSessionManager().removeListener(this);
    }

    private void onEV(final int steps) {
        final Timer evStepsTimer = this.evStepsTimer;
        if (evStepsTimer != null) {
            evStepsTimer.cancel();
        }

        evStepsPending = evStepsPending == null ? steps : evStepsPending + steps;

        final Timer evStepsTimerNew = new Timer();
        evStepsTimerNew.schedule(new TimerTask() {
            @Override
            public void run() {
                evStepsTimerNew.cancel();

                final DroneSession sessionLocal = session;
                final Integer steps = evStepsPending;
                evStepsPending = null;
                if (sessionLocal != null && steps != null && steps != 0) {
                    final ExposureCompensationStepCameraCommand command = new ExposureCompensationStepCameraCommand();
                    command.exposureCompensationSteps = steps;
                    exposureCommand = command;
                    Dronelink.getInstance().droneOffsets.cameraExposureCompensationSteps += steps;
                    try {
                        sessionLocal.addCommand(exposureCommand);
                    } catch (final Exception e) {}
                    updateTimer();
                }
            }
        }, 750, 750);

        this.evStepsTimer = evStepsTimer;
        updateTimer();
    }

    private void updateTimer() {
        getActivity().runOnUiThread(update);
    }

    private Runnable update = new Runnable() {
        public void run() {
            if (!isAdded()) {
                return;
            }

            final DroneSession sessionLocal = session;
            CameraExposureCompensation exposureCompensation = null;
            if (sessionLocal != null) {
                final DatedValue<CameraStateAdapter> cameraState = sessionLocal.getCameraState(0);
                if (cameraState != null && cameraState.value != null) {
                    exposureCompensation = cameraState.value.getExposureCompensation();
                }
            }

            c1Button.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(exposureCommand == null ? R.color.darkGray : R.color.secondary)));
            c1Button.setEnabled(exposureCompensation != null);
            c2Button.setBackgroundTintList(c1Button.getBackgroundTintList());
            c2Button.setEnabled(c1Button.isEnabled());

            final Integer steps = evStepsPending;
            if (steps != null && steps != 0) {
                cTextView.setText((steps > 0 ? "+" : "") + steps);
            }
            else {
                cTextView.setText(exposureCompensation == null ? "" : Dronelink.getInstance().formatEnum("CameraExposureCompensation", gson.toJson(exposureCompensation).replace("\"", ""), ""));
            }
        }
    };

    private void listenRCButtonsTimer() {
        getActivity().runOnUiThread(listenRCButtons);
    }

    private Runnable listenRCButtons = new Runnable() {
        public void run() {
            if (!isAdded() && getView() == null || getView().getVisibility() != View.VISIBLE) {
                return;
            }

            final MissionExecutor missionExecutor = Dronelink.getInstance().getMissionExecutor();
            if (missionExecutor == null || !missionExecutor.isEngaged()) {
                return;
            }

            final DroneSession sessionLocal = session;
            RemoteControllerStateAdapter remoteControllerState = null;
            if (sessionLocal != null) {
                final DatedValue<RemoteControllerStateAdapter> remoteControllerStateDated = sessionLocal.getRemoteControllerState(0);
                if (remoteControllerStateDated != null && remoteControllerStateDated.value != null) {
                    remoteControllerState = remoteControllerStateDated.value;
                }
            }

            final ExposureCompensationStepCameraCommand exposureCommandLocal = exposureCommand;
            if (exposureCommandLocal == null && remoteControllerState != null) {
                if (c1PressedPrevious && !remoteControllerState.getC1Button().pressed) {
                    onEV(-1);
                }

                if (c2PressedPrevious && !remoteControllerState.getC2Button().pressed) {
                    onEV(1);
                }
            }

            c1PressedPrevious = remoteControllerState != null && remoteControllerState.getC1Button().pressed;
            c2PressedPrevious = remoteControllerState != null && remoteControllerState.getC2Button().pressed;
        }
    };

    @Override
    public void onOpened(final DroneSession session) {
        this.session = session;
        session.addListener(this);
    }

    @Override
    public void onClosed(final DroneSession session) {
        this.session = null;
        session.removeListener(this);
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
        final ExposureCompensationStepCameraCommand exposureCommandLocal = exposureCommand;
        if (exposureCommandLocal == null) {
            return;
        }

        if (exposureCommandLocal.id == command.id) {
            exposureCommand = null;
            updateTimer();
        }
    }

    @Override
    public void onCameraFileGenerated(final DroneSession session, final CameraFile file) {}
}