//  MissionFragment.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 11/8/19.
//  Copyright © 2019 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.dronelink.core.CameraFile;
import com.dronelink.core.Convert;
import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.FuncExecutor;
import com.dronelink.core.MissionExecutor;
import com.dronelink.core.adapters.DroneStateAdapter;
import com.dronelink.core.command.CommandError;
import com.dronelink.core.mission.command.Command;
import com.dronelink.core.mission.component.Component;
import com.dronelink.core.mission.core.GeoCoordinate;
import com.dronelink.core.mission.core.Message;
import com.dronelink.core.mission.core.MessageGroup;
import com.squareup.picasso.Picasso;
import com.stfalcon.imageviewer.StfalconImageViewer;
import com.stfalcon.imageviewer.loader.ImageLoader;

import java.util.Collections;
import java.util.Timer;
import java.util.TimerTask;

public class MissionFragment extends Fragment implements Dronelink.Listener, DroneSessionManager.Listener, DroneSession.Listener, MissionExecutor.Listener {
    private DroneSession session;
    private MissionExecutor missionExecutor;

    private ProgressBar activityIndicator;
    private ImageButton primaryButton;
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

                if (session == null) {
                    return;
                }

                final Message[] engageDisallowedReasons = missionExecutor.engageDisallowedReasons(session);
                if (engageDisallowedReasons != null && engageDisallowedReasons.length > 0) {
                    final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                    alertDialog.setTitle(engageDisallowedReasons[0].title);
                    if (engageDisallowedReasons[0].details != null) {
                        alertDialog.setMessage(engageDisallowedReasons[0].details);
                    }
                    alertDialog.setPositiveButton(getString(R.string.Mission_dismiss), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface d, int i) {
                            d.dismiss();
                        }
                    });
                    alertDialog.show();
                    return;
                }

                promptConfirmation();
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

        messagesTextView = getView().findViewById(R.id.detailsTextView);
        messagesTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleMissionExpanded();
            }
        });

        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dronelink.getInstance().getSessionManager().addListener(this);
        Dronelink.getInstance().addListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        Dronelink.getInstance().getSessionManager().removeListener(this);
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
            if (actualTakeoffLocation != null && actualTakeoffLocation.distanceTo(suggestedTakeoffCoordinate.getLocation()) > Convert.FeetToMeters(50)) {
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
            getActivity().runOnUiThread(updateViews);
            if (aborted) {
                Dronelink.getInstance().announce(getString(R.string.Mission_start_cancelled));
            }
        }
    }

    private void engage() {
        engageOnMissionEstimated = false;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (missionExecutor == null || session == null) {
                    return;
                }

                try {
                    missionExecutor.engage(session, new MissionExecutor.EngageDisallowed() {
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
                                    alertDialog.setPositiveButton(getString(R.string.Mission_dismiss), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(final DialogInterface d, int i) {
                                            d.dismiss();
                                        }
                                    });
                                    alertDialog.show();
                                }
                            });
                            getActivity().runOnUiThread(updateViews);
                        }
                    });
                }
                catch (final Dronelink.DroneSerialNumberUnavailableException e) {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                            alertDialog.setTitle(getString(R.string.Mission_start_engage_droneSerialNumberUnavailable_title));
                            alertDialog.setMessage(getString(R.string.Mission_start_engage_droneSerialNumberUnavailable_message));
                            alertDialog.setPositiveButton(getString(R.string.Mission_dismiss), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(final DialogInterface d, int i) {
                                    d.dismiss();
                                }
                            });
                            alertDialog.show();
                        }
                    });
                    getActivity().runOnUiThread(updateViews);
                }
            }
        });
    }

    private void toggleMissionExpanded() {
        toggleMissionExpanded(!expanded);
    }

    private void toggleMissionExpanded(final boolean expanded) {
        this.expanded = expanded;
        getView().getLayoutParams().height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float)(expanded ? 135 : 70), getResources().getDisplayMetrics());
        getView().requestLayout();
        getActivity().runOnUiThread(updateViews);
    }

    private Runnable updateViews = new Runnable() {
        public void run() {
            if (missionExecutor == null) {
                getView().setVisibility(View.GONE);
                return;
            }
            getView().setVisibility(View.VISIBLE);

            titleTextView.setText(missionExecutor.descriptors.toString());

            if (missionExecutor.isEstimating()) {
                activityIndicator.setVisibility(View.VISIBLE);
                primaryButton.setVisibility(View.INVISIBLE);
                subtitleTextView.setVisibility(View.VISIBLE);
                executionDurationTextView.setVisibility(View.INVISIBLE);
                timeRemainingTextView.setVisibility(View.INVISIBLE);
                progressBar.setVisibility(View.INVISIBLE);
                dismissButton.setVisibility(View.INVISIBLE);
                messagesTextView.setVisibility(View.INVISIBLE);

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

                primaryButton.setEnabled(true);
                primaryButton.setBackgroundTintList(primaryEngagedColor);
                primaryButton.setImageResource(R.drawable.baseline_close_white_48);
                subtitleTextView.setText(getString(R.string.Mission_start_countdown) + " " + (countdownRemaining + 1));
                return;
            }

            if (missionExecutor.isEngaging()) {
                activityIndicator.setVisibility(View.VISIBLE);
                primaryButton.setVisibility(View.INVISIBLE);
                subtitleTextView.setVisibility(View.VISIBLE);
                executionDurationTextView.setVisibility(View.INVISIBLE);
                timeRemainingTextView.setVisibility(View.INVISIBLE);
                progressBar.setVisibility(View.INVISIBLE);
                dismissButton.setVisibility(View.INVISIBLE);
                messagesTextView.setVisibility(View.INVISIBLE);

                subtitleTextView.setText(getString(R.string.Mission_start_engaging));
                return;
            }


            activityIndicator.setVisibility(View.INVISIBLE);
            primaryButton.setVisibility(View.VISIBLE);
            subtitleTextView.setVisibility(View.INVISIBLE);
            executionDurationTextView.setVisibility(View.VISIBLE);
            timeRemainingTextView.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.VISIBLE);
            dismissButton.setVisibility(View.VISIBLE);
            messagesTextView.setVisibility(expanded ? View.VISIBLE : View.INVISIBLE);

            primaryButton.setEnabled(session != null);

            final MissionExecutor.Estimate estimate = missionExecutor.getEstimate();
            double totalTime = 0;
            double executionDuration = 0;
            if (estimate != null) {
                totalTime = estimate.time;
                executionDuration = missionExecutor.getExecutionDuration();
            }
            final double timeRemaining = Math.max(totalTime - executionDuration, 0);
            executionDurationTextView.setText(Dronelink.getInstance().format("timeElapsed", executionDuration, "00:00"));
            timeRemainingTextView.setText(Dronelink.getInstance().format("timeElapsed", timeRemaining, "00:00"));
            progressBar.setProgress((int)(Math.min(totalTime == 0 ? 0.0 : executionDuration / totalTime, 1.0) * 100));

            if (missionExecutor.isEngaged()) {
                progressBar.setProgressTintList(progressEngagedColor);

                if (expanded) {
                    final StringBuilder messages = new StringBuilder();
                    for (final MessageGroup messageGroup : missionExecutor.getExecutingMessageGroups()) {
                        if (messages.length() > 0) {
                            messages.append("\n\n");
                        }
                        messages.append(messageGroup.toString());
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
            }
        }
    };

    private boolean estimateMission() {
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
            if (previousEstimateContext.location.distanceTo(estimateContext.location) < 1 && Math.abs(previousEstimateContext.altitude - estimateContext.altitude) < 1) {
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
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onClosed(final DroneSession session) {
        this.session = null;
        session.removeListener(this);
        getActivity().runOnUiThread(updateViews);
    }

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
    public void onMissionLoaded(final MissionExecutor executor) {
        missionExecutor = executor;
        previousEstimateContext = null;
        executor.addListener(this);
        estimateMission();

        if (executor.userInterfaceSettings != null && executor.userInterfaceSettings.missionDetailsExpanded != null && executor.userInterfaceSettings.missionDetailsExpanded != expanded) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    toggleMissionExpanded(executor.userInterfaceSettings.missionDetailsExpanded);
                }
            });
        }

        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onMissionUnloaded(final MissionExecutor executor) {
        missionExecutor = null;
        previousEstimateContext = null;
        executor.removeListener(this);
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onFuncLoaded(final FuncExecutor executor) {
    }

    @Override
    public void onFuncUnloaded(final FuncExecutor executor) {
    }

    @Override
    public void onMissionEstimating(final MissionExecutor executor) {
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onMissionEstimated(final MissionExecutor executor, final MissionExecutor.Estimate estimate) {
        if (engageOnMissionEstimated) {
            engage();
            return;
        }

        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onMissionEngaging(final MissionExecutor executor) {
        Dronelink.getInstance().announce(getString(R.string.Mission_engaging));
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onMissionEngaged(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onMissionExecuted(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {
        if (System.currentTimeMillis() - lastUpdatedMillis > updateMillis) {
            lastUpdatedMillis = System.currentTimeMillis();
            getActivity().runOnUiThread(updateViews);
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
                    alertDialog.setPositiveButton(getString(R.string.Mission_dismiss), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(final DialogInterface d, int i) {
                            d.dismiss();
                        }
                    });
                    alertDialog.show();
                }
            });
        }

        final Component.ExecutionStatus status = executor.getStatus();
        if (status != null && status.completed) {
            Dronelink.getInstance().unloadMission();
            Dronelink.getInstance().announce(reason.title);
            return;
        }

        Dronelink.getInstance().announce(getString(R.string.Mission_disengaged));
        getActivity().runOnUiThread(updateViews);
    }
}
