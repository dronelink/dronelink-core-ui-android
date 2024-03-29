//  MissionFragment.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 11/8/19.
//  Copyright © 2019 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.dronelink.core.CameraFile;
import com.dronelink.core.Convert;
import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.Executor;
import com.dronelink.core.FuncExecutor;
import com.dronelink.core.MissionExecutor;
import com.dronelink.core.ModeExecutor;
import com.dronelink.core.adapters.DroneStateAdapter;
import com.dronelink.core.command.CommandError;
import com.dronelink.core.kernel.command.Command;
import com.dronelink.core.kernel.core.CameraFocusCalibration;
import com.dronelink.core.kernel.core.GeoCoordinate;
import com.dronelink.core.kernel.core.Message;
import com.dronelink.core.kernel.core.MessageGroup;
import com.dronelink.core.kernel.core.enums.ExecutionStatus;
import com.squareup.picasso.Picasso;
import com.stfalcon.imageviewer.StfalconImageViewer;
import com.stfalcon.imageviewer.loader.ImageLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

public class MissionFragment extends Fragment implements Dronelink.Listener, DroneSessionManager.Listener, DroneSession.Listener, MissionExecutor.Listener {
    private DroneSession session;
    private CameraFocusCalibration cameraFocusCalibration;
    private MissionExecutor missionExecutor;

    private ProgressBar activityIndicator;
    private ImageButton primaryButton;
    private Button detailsButton;
    private ImageButton dismissButton;
    private TextView titleTextView;
    private TextView subtitleTextView;
    private TextView executionDurationTextView;
    private TextView timeRemainingTextView;
    private ProgressBar progressBar;
    private TextView messagesTextView;

    private final ColorStateList primaryEngagedColor = ColorStateList.valueOf(Color.parseColor("#f50057"));
    private final ColorStateList primaryDisengagedColor = ColorStateList.valueOf(Color.parseColor("#4527a0"));
    private final ColorStateList primaryDisconnectedColor = ColorStateList.valueOf(Color.parseColor("#616161"));
    private final ColorStateList progressEngagedColor = ColorStateList.valueOf(Color.parseColor("#f50057"));
    private final ColorStateList progressDisengagedColor = ColorStateList.valueOf(Color.parseColor("#7c4dff"));

    private class EstimateContext {
        Location location = new Location("");
        double altitude = 0;
    }
    private EstimateContext previousEstimateContext;
    private boolean engageOnMissionEstimated = false;
    private Timer countdownTimer;
    private int countdownRemaining = 0;
    private final long updateMillis = 500;
    private long lastUpdatedMillis = 0;
    private boolean expanded = false;

    public MissionFragment() {}

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mission, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable final Bundle savedInstanceState) {
        activityIndicator = view.findViewById(R.id.activityIndicator);
        activityIndicator.setProgressTintList(primaryEngagedColor);

        primaryButton = view.findViewById(R.id.primaryButton);
        primaryButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (missionExecutor == null) {
                    return;
                }

                if (countdownTimer != null) {
                    stopCoundown(true);
                    return;
                }

                if (missionExecutor.isEngaged()) {
                    missionExecutor.disengage(new Message(getString(R.string.MissionDisengageReason_user_disengaged)));
                    return;
                }

                final DroneSession sessionLocal = session;
                if (sessionLocal == null) {
                    return;
                }

                final Message[] engageDisallowedReasons = missionExecutor.engageDisallowedReasons(sessionLocal);
                if (engageDisallowedReasons != null && engageDisallowedReasons.length > 0) {
                    final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                    alertDialog.setTitle(engageDisallowedReasons[0].title);
                    if (engageDisallowedReasons[0].details != null) {
                        alertDialog.setMessage(engageDisallowedReasons[0].details);
                    }
                    alertDialog.setPositiveButton(getString(R.string.Executable_dismiss), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface d, int i) {
                            d.dismiss();
                        }
                    });
                    alertDialog.show();
                    return;
                }

                if (missionExecutor.cameraFocusCalibrationsRequired != null) {
                    final ArrayList<CameraFocusCalibration> calibrationsPending = new ArrayList<>();
                    for (final CameraFocusCalibration calibration : missionExecutor.cameraFocusCalibrationsRequired) {
                        if (Dronelink.getInstance().getCameraFocusCalibration(calibration.withDroneSerialNumber(sessionLocal.getSerialNumber())) == null) {
                            calibrationsPending.add(calibration);
                        }
                    }

                    if (calibrationsPending.size() > 0) {
                        final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                        alertDialog.setTitle(getString(R.string.Mission_cameraFocusCalibrationsRequired_title));
                        alertDialog.setMessage(getString(calibrationsPending.size() == 1 ? R.string.Mission_cameraFocusCalibrationsRequired_message_single : R.string.Mission_cameraFocusCalibrationsRequired_message_multiple));
                        alertDialog.setPositiveButton(getString(R.string._continue), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface d, int i) {
                                d.dismiss();
                                Dronelink.getInstance().requestCameraFocusCalibration(calibrationsPending.get(0));
                            }
                        });
                        alertDialog.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(final DialogInterface d, int i) {
                                d.dismiss();
                            }
                        });
                        alertDialog.show();
                        return;
                    }
                }

                promptConfirmation();
            }
        });
        detailsButton = view.findViewById(R.id.detailsButton);
        detailsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (missionExecutor == null) {
                    return;
                }

                final Intent intent = new Intent(getActivity(), EmbedActivity.class);
                intent.putExtra("network.error", getString(R.string.Mission_details_network_error));
                startActivity(intent);
            }
        });
        dismissButton = getView().findViewById(R.id.dismissButton);
        dismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Dronelink.getInstance().unloadMission();
            }
        });
        titleTextView = getView().findViewById(R.id.titleTextView);
        titleTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleMissionExpanded();
            }
        });
        subtitleTextView = getView().findViewById(R.id.subtitleTextView);
        executionDurationTextView = getView().findViewById(R.id.executionDurationTextView);
        timeRemainingTextView = getView().findViewById(R.id.timeRemainingTextView);
        progressBar = getView().findViewById(R.id.progressBar);
        progressBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleMissionExpanded();
            }
        });

        messagesTextView = getView().findViewById(R.id.messagesTextView);
        messagesTextView.setMovementMethod(new ScrollingMovementMethod());

        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onStart() {
        super.onStart();
        final DroneSessionManager manager = Dronelink.getInstance().getTargetDroneSessionManager();
        if (manager != null) {
            manager.addListener(this);
        }
        Dronelink.getInstance().addListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        final DroneSessionManager manager = Dronelink.getInstance().getTargetDroneSessionManager();
        if (manager != null) {
            manager.removeListener(this);
        }
        Dronelink.getInstance().removeListener(this);
        if (missionExecutor != null) {
            missionExecutor.removeListener(this);
        }
    }

    private void promptConfirmation() {
        if (missionExecutor == null || session == null) {
            return;
        }

        if (missionExecutor.getEngagementCount() > 0 && missionExecutor.reengagementRules.confirmationMessage != null) {
            final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
            alertDialog.setTitle(missionExecutor.reengagementRules.confirmationMessage.title);
            alertDialog.setMessage(missionExecutor.reengagementRules.confirmationMessage.details);
            alertDialog.setPositiveButton(getString(R.string.resume), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface d, int i) {
                    d.dismiss();
                    promptTakeoffLocationWarning();
                }
            });
            alertDialog.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface d, int i) {
                    d.dismiss();
                }
            });
            if (missionExecutor.reengagementRules.confirmationInstructionsImageUrl != null) {
                alertDialog.setNeutralButton(getString(R.string.learnMore), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface d, int i) {
                        d.dismiss();
                        new StfalconImageViewer.Builder<>(getContext(), Collections.singletonList(missionExecutor.reengagementRules.confirmationInstructionsImageUrl), new ImageLoader<String>() {
                            @Override
                            public void loadImage(final ImageView imageView, final String imageUrl) {
                                Picasso.get().load(imageUrl).into(imageView);
                            }
                        }).show();
                    }
                });
            }
            alertDialog.show();
            return;
        }

        promptTakeoffLocationWarning();
    }

    private void promptTakeoffLocationWarning() {
        if (missionExecutor == null || session == null) {
            return;
        }

        missionExecutor.droneTakeoffAltitudeAlternate = null;
        if (missionExecutor.requiredTakeoffArea == null) {
            final Location actualTakeoffLocation = session.getState().value.getTakeoffLocation();
            final GeoCoordinate suggestedTakeoffCoordinate = missionExecutor.takeoffCoordinate;
            if (actualTakeoffLocation != null && actualTakeoffLocation.distanceTo(suggestedTakeoffCoordinate.getLocation()) > Convert.FeetToMeters(300)) {
                final Location deviceLocation = Dronelink.getInstance().getLocation();
                if (deviceLocation != null && deviceLocation.hasAltitude()) {
                    missionExecutor.droneTakeoffAltitudeAlternate = Dronelink.getInstance().getAltitude();
                }

                final String distance = Dronelink.getInstance().format("distance", new Double(actualTakeoffLocation.distanceTo(suggestedTakeoffCoordinate.getLocation())), "");
                final String altitude = missionExecutor.droneTakeoffAltitudeAlternate == null ? null : Dronelink.getInstance().format("altitude", missionExecutor.droneTakeoffAltitudeAlternate, "");
                String message = "";
                if (altitude != null && missionExecutor.elevationsRequired) {
                    message = getString(R.string.Mission_start_takeoffLocationWarning_message_deviceAltitudeAvailable, distance, altitude);
                }
                else {
                    message = getString(R.string.Mission_start_takeoffLocationWarning_message_deviceAltitudeUnavailable, distance);
                }

                final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                alertDialog.setTitle(R.string.Mission_start_takeoffLocationWarning_title);
                alertDialog.setMessage(message);
                alertDialog.setPositiveButton(getString(R.string.Mission_continue), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface d, int i) {
                        d.dismiss();
                        startCountdown();
                    }
                });
                alertDialog.setNegativeButton(getString(R.string.Mission_cancel), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface d, int i) {
                        d.dismiss();
                    }
                });
                alertDialog.show();
                return;
            }
        }

        startCountdown();
    }

    private void startCountdown() {
        countdownRemaining = 3;
        countdownTimer = new Timer();
        countdownTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (session == null || missionExecutor == null) {
                    stopCoundown(true);
                    return;
                }

                if (countdownRemaining == 0) {
                    stopCoundown(false);
                    engageOnMissionEstimated = true;
                    if (!estimateMission()) {
                        engage();
                    }
                }
                else {
                    countdownRemaining--;
                    getActivity().runOnUiThread(updateViews);
                    Dronelink.getInstance().announce("" + (countdownRemaining + 1));
                }
            }
        }, 0, 1000);
    }

    private void stopCoundown(final boolean aborted) {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
            final Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(updateViews);
                if (aborted) {
                    Dronelink.getInstance().announce(getString(R.string.Executable_start_cancelled));
                }
            }
        }
    }

    private void engage() {
        engageOnMissionEstimated = false;
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (missionExecutor == null || session == null) {
                        return;
                    }

                    missionExecutor.engage(session, new Executor.EngageDisallowed() {
                        @Override
                        public void disallowed(final Message reason) {
                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                                    alertDialog.setTitle(reason.title);
                                    if (reason.details != null) {
                                        alertDialog.setMessage(reason.details);
                                    }
                                    alertDialog.setPositiveButton(getString(R.string.Executable_dismiss), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(final DialogInterface d, int i) {
                                            d.dismiss();
                                        }
                                    });
                                    alertDialog.show();
                                }
                            });
                            activity.runOnUiThread(updateViews);
                        }
                    });
                }
            });
        }
    }

    private void toggleMissionExpanded() {
        toggleMissionExpanded(!expanded);
    }

    private void toggleMissionExpanded(final boolean expanded) {
        this.expanded = expanded;
        getView().getLayoutParams().height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float)(expanded ? 145 : 70), getResources().getDisplayMetrics());
        getView().requestLayout();
        getActivity().runOnUiThread(updateViews);
    }

    private Runnable updateViews = new Runnable() {
        public void run() {
            final View view = getView();
            if (view == null) {
                return;
            }

            final MissionExecutor missionExecutorLocal = missionExecutor;
            if (cameraFocusCalibration != null || missionExecutorLocal == null) {
                view.setVisibility(View.GONE);
                return;
            }
            view.setVisibility(View.VISIBLE);

            titleTextView.setText(missionExecutorLocal.descriptors.toString());

            if (missionExecutorLocal.isEstimating()) {
                activityIndicator.setVisibility(View.VISIBLE);
                primaryButton.setVisibility(View.INVISIBLE);
                subtitleTextView.setVisibility(View.VISIBLE);
                executionDurationTextView.setVisibility(View.INVISIBLE);
                timeRemainingTextView.setVisibility(View.INVISIBLE);
                progressBar.setVisibility(View.INVISIBLE);
                dismissButton.setVisibility(View.INVISIBLE);
                messagesTextView.setVisibility(View.INVISIBLE);
                detailsButton.setVisibility(missionExecutorLocal.isEngaged() || true ? View.INVISIBLE : View.VISIBLE);

                subtitleTextView.setText(getString(R.string.Mission_estimating));
                return;
            }

            if (countdownTimer != null) {
                activityIndicator.setVisibility(View.INVISIBLE);
                primaryButton.setVisibility(View.VISIBLE);
                subtitleTextView.setVisibility(View.VISIBLE);
                executionDurationTextView.setVisibility(View.INVISIBLE);
                timeRemainingTextView.setVisibility(View.INVISIBLE);
                progressBar.setVisibility(View.INVISIBLE);
                dismissButton.setVisibility(View.VISIBLE);
                messagesTextView.setVisibility(View.INVISIBLE);
                detailsButton.setVisibility(View.INVISIBLE);

                primaryButton.setEnabled(true);
                primaryButton.setBackgroundTintList(primaryEngagedColor);
                primaryButton.setImageResource(R.drawable.baseline_close_white_48);
                subtitleTextView.setText(getString(R.string.Executable_start_countdown) + " " + (countdownRemaining + 1));
                return;
            }

            if (missionExecutorLocal.isEngaging()) {
                activityIndicator.setVisibility(View.VISIBLE);
                primaryButton.setVisibility(View.INVISIBLE);
                subtitleTextView.setVisibility(View.VISIBLE);
                executionDurationTextView.setVisibility(View.INVISIBLE);
                timeRemainingTextView.setVisibility(View.INVISIBLE);
                progressBar.setVisibility(View.INVISIBLE);
                dismissButton.setVisibility(View.INVISIBLE);
                messagesTextView.setVisibility(View.INVISIBLE);
                detailsButton.setVisibility(View.INVISIBLE);

                subtitleTextView.setText(getString(R.string.Executable_start_engaging));
                return;
            }

            final MissionExecutor.Estimate estimate = missionExecutorLocal.getEstimate();
            double estimateTime = 0;
            double executionDuration = 0;
            if (estimate != null) {
                estimateTime = estimate.time;
                executionDuration = missionExecutorLocal.getExecutionDuration();
            }
            final double timeRemaining = estimateTime - executionDuration;

            activityIndicator.setVisibility(View.INVISIBLE);
            primaryButton.setVisibility(View.VISIBLE);
            subtitleTextView.setVisibility(View.INVISIBLE);
            executionDurationTextView.setVisibility(View.VISIBLE);
            timeRemainingTextView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            dismissButton.setVisibility(missionExecutorLocal.isEngaged() ? View.INVISIBLE : View.VISIBLE);
            messagesTextView.setVisibility(expanded ? View.VISIBLE : View.INVISIBLE);
            detailsButton.setVisibility(missionExecutorLocal.isEngaged() || true ? View.INVISIBLE : View.VISIBLE);

            primaryButton.setEnabled(session != null);

            executionDurationTextView.setText(Dronelink.getInstance().format("timeElapsed", executionDuration, "00:00"));
            timeRemainingTextView.setText((timeRemaining < 0 ? "+" : "") + Dronelink.getInstance().format("timeElapsed", Math.abs(timeRemaining), "00:00"));
            progressBar.setProgress((int)(Math.min(estimateTime == 0 ? 0.0 : executionDuration / estimateTime, 1.0) * 100));

            if (missionExecutorLocal.isEngaged()) {
                progressBar.setProgressTintList(progressEngagedColor);

                if (expanded) {
                    final StringBuilder messages = new StringBuilder();
                    final MessageGroup[] messageGroups = missionExecutorLocal.getExecutingMessageGroups();
                    if (messageGroups != null) {
                        for (final MessageGroup messageGroup : messageGroups) {
                            if (messages.length() > 0) {
                                messages.append("\n\n");
                            }
                            messages.append(messageGroup.toString());
                        }
                    }
                    messagesTextView.setText(messages.toString());
                }
                else {
                    messagesTextView.setText("");
                }

                primaryButton.setBackgroundTintList(primaryEngagedColor);
                primaryButton.setImageResource(R.drawable.baseline_pause_white_48);
            }
            else {
                progressBar.setProgressTintList(progressDisengagedColor);
                primaryButton.setBackgroundTintList(session == null ? primaryDisconnectedColor : primaryDisengagedColor);
                primaryButton.setImageResource(R.drawable.baseline_play_arrow_white_48);
                messagesTextView.setText("");
            }
        }
    };

    private boolean estimateMission() {
        final MissionExecutor missionExecutor = this.missionExecutor;
        if (missionExecutor == null || missionExecutor.isEstimating()) {
            return false;
        }

        final EstimateContext estimateContext = new EstimateContext();
        if (session != null) {
            final DroneStateAdapter state = session.getState().value;
            if (state != null) {
                final Location location = state.getLocation();
                if (location != null) {
                    estimateContext.location = location;
                    estimateContext.altitude = state.getAltitude();
                }
            }
        }

        if (previousEstimateContext != null) {
            final double tolerance = 4.0;
            if (previousEstimateContext.location.distanceTo(estimateContext.location) < tolerance && Math.abs(previousEstimateContext.altitude - estimateContext.altitude) < tolerance) {
                return false;
            }
        }

        previousEstimateContext = estimateContext;
        missionExecutor.estimate(session, true,true);
        return true;
    }

    @Override
    public void onOpened(final DroneSession session) {
        this.session = session;
        session.addListener(this);
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateViews);
        }
    }

    @Override
    public void onClosed(final DroneSession session) {
        this.session = null;
        session.removeListener(this);
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateViews);
        }
    }

    @Override
    public void onDroneSessionManagerAdded(final DroneSessionManager droneSessionManager) {}

    @Override
    public void onRegistered(final String error) {}

    @Override
    public void onInitialized(final DroneSession session) {}

    @Override
    public void onLocated(final DroneSession session) {
        estimateMission();
    }

    @Override
    public void onMotorsChanged(final DroneSession session, final boolean value) {}

    @Override
    public void onCommandExecuted(final DroneSession session, final Command command) {}

    @Override
    public void onCommandFinished(final DroneSession session, final Command command, final CommandError error) {}

    @Override
    public void onCameraFileGenerated(final DroneSession session, final CameraFile file) {}

    @Override
    public void onVideoFeedSourceUpdated(final DroneSession session, final Integer channel) {}

    @Override
    public void onMissionLoaded(final MissionExecutor executor) {
        missionExecutor = executor;
        previousEstimateContext = null;
        executor.addListener(this);
        final Activity activity = getActivity();
        if (activity != null) {
            if (executor.userInterfaceSettings != null && executor.userInterfaceSettings.missionDetailsExpanded != null && executor.userInterfaceSettings.missionDetailsExpanded != expanded) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toggleMissionExpanded(executor.userInterfaceSettings.missionDetailsExpanded);
                    }
                });
            }

            activity.runOnUiThread(updateViews);
        }
        estimateMission();
    }

    @Override
    public void onMissionUnloaded(final MissionExecutor executor) {
        missionExecutor = null;
        previousEstimateContext = null;
        executor.removeListener(this);
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateViews);
        }
    }

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
        cameraFocusCalibration = value;
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateViews);
        }
    }

    @Override
    public void onCameraFocusCalibrationUpdated(final CameraFocusCalibration value) {
        cameraFocusCalibration = null;
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateViews);
        }
    }

    @Override
    public void onMissionEstimating(final MissionExecutor executor) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateViews);
        }
    }

    @Override
    public void onMissionEstimated(final MissionExecutor executor, final MissionExecutor.Estimate estimate) {
        if (engageOnMissionEstimated) {
            engage();
            return;
        }

        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateViews);
        }
    }

    @Override
    public Message[] missionEngageDisallowedReasons(final MissionExecutor executor) {
        return null;
    }

    @Override
    public void onMissionEngaging(final MissionExecutor executor) {
        Dronelink.getInstance().announce(getString(R.string.Mission_engaging));
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateViews);
        }
    }

    @Override
    public void onMissionEngaged(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateViews);
        }
    }

    @Override
    public void onMissionExecuted(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {
        if (System.currentTimeMillis() - lastUpdatedMillis > updateMillis) {
            lastUpdatedMillis = System.currentTimeMillis();
            final Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(updateViews);
            }
        }
    }

    @Override
    public void onMissionDisengaged(final MissionExecutor executor, final MissionExecutor.Engagement engagement, final Message reason) {
        if (!getString(R.string.MissionDisengageReason_user_disengaged).equals(reason.title)) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                    alertDialog.setTitle(reason.title);
                    if (reason.details != null) {
                        alertDialog.setMessage(reason.details);
                    }
                    alertDialog.setPositiveButton(getString(R.string.Executable_dismiss), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface d, int i) {
                            d.dismiss();
                        }
                    });
                    alertDialog.show();
                }
            });
        }

        final ExecutionStatus status = executor.getStatus();
        if (status != null && status.completed) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Dronelink.getInstance().unloadMission();
                    Dronelink.getInstance().announce(reason.title);
                }
            });
            return;
        }

        Dronelink.getInstance().announce(getString(R.string.Mission_disengaged));
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateViews);
        }
    }

    @Override
    public void onMissionUpdatedDisconnected(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {}
}
