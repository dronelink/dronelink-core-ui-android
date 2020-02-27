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
import android.widget.ProgressBar;
import android.widget.TextView;

import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.FuncExecutor;
import com.dronelink.core.MissionExecutor;
import com.dronelink.core.mission.component.Component;
import com.dronelink.core.mission.core.GeoCoordinate;
import com.dronelink.core.mission.core.Message;
import com.dronelink.core.mission.core.MessageGroup;

import java.util.Timer;
import java.util.TimerTask;

public class MissionFragment extends Fragment implements Dronelink.Listener, DroneSessionManager.Listener, MissionExecutor.Listener {
    private DroneSession session;
    private MissionExecutor missionExecutor;
    private Timer countdownTimer;
    private int countdownRemaining = 0;
    private boolean engaging = false;
    private ImageButton primaryButton;
    private ImageButton closeButton;
    private TextView titleTextView;
    private TextView timeElapsedTextView;
    private TextView timeRemainingTextView;
    private ProgressBar progressBar;
    private TextView detailsTextView;
    private boolean expanded = false;
    private final long updateMillis = 500;
    private long lastUpdatedMillis = 0;
    private final ColorStateList primaryEngagedColor = ColorStateList.valueOf(Color.parseColor("#f50057"));
    private final ColorStateList primaryDisengagedColor = ColorStateList.valueOf(Color.parseColor("#4527a0"));
    private final ColorStateList primaryDisconnectedColor = ColorStateList.valueOf(Color.parseColor("#616161"));
    private final ColorStateList progressEngagedColor = ColorStateList.valueOf(Color.parseColor("#f50057"));
    private final ColorStateList progressDisengagedColor = ColorStateList.valueOf(Color.parseColor("#7c4dff"));

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
        primaryButton = view.findViewById(R.id.primaryButton);
        primaryButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (session == null || missionExecutor == null) {
                    return;
                }

                if (engaging || missionExecutor.isEngaged()) {
                    missionExecutor.disengage(new Message(getString(R.string.MissionDisengageReason_user_disengaged)));
                    return;
                }

                if (countdownTimer != null) {
                    stopCoundown(true);
                    return;
                }

                final Message[] engageDisallowedReasons = missionExecutor.engageDisallowedReasons(session);
                if (engageDisallowedReasons == null || engageDisallowedReasons.length == 0) {
                    missionExecutor.droneTakeoffAltitudeAlternate = null;
                    if (missionExecutor.getRequiredTakeoffArea() == null) {
                        final Location actualTakeoffLocation = session.getState().value.getTakeoffLocation();
                        final GeoCoordinate suggestedTakeoffCoordinate = missionExecutor.getTakeoffCoordinate();
                        if (actualTakeoffLocation.distanceTo(suggestedTakeoffCoordinate.getLocation()) > 10) {
                            final Location deviceLocation = Dronelink.getInstance().getLocation();
                            if (deviceLocation != null && deviceLocation.hasAltitude()) {
                                missionExecutor.droneTakeoffAltitudeAlternate = Dronelink.getInstance().getAltitude();
                            }

                            final String distance = Dronelink.getInstance().format("distance", new Double(actualTakeoffLocation.distanceTo(suggestedTakeoffCoordinate.getLocation())), "");
                            final String altitude = missionExecutor.droneTakeoffAltitudeAlternate == null ? null : Dronelink.getInstance().format("altitude", missionExecutor.droneTakeoffAltitudeAlternate, "");
                            String message = "";
                            if (altitude == null) {
                                message = getString(R.string.Mission_start_takeoffLocationWarning_message_deviceAltitudeUnavailable, distance);
                            }
                            else {
                                message = getString(R.string.Mission_start_takeoffLocationWarning_message_deviceAltitudeAvailable, distance, altitude);
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
                    return;
                }

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
            }
        });
        closeButton = getView().findViewById(R.id.closeButton);
        closeButton.setOnClickListener(new View.OnClickListener() {
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
        timeElapsedTextView = getView().findViewById(R.id.timeElapsedTextView);
        timeRemainingTextView = getView().findViewById(R.id.timeRemainingTextView);
        progressBar = getView().findViewById(R.id.progressBar);
        progressBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleMissionExpanded();
            }
        });

        detailsTextView = getView().findViewById(R.id.detailsTextView);
        detailsTextView.setOnClickListener(new View.OnClickListener() {
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
        Dronelink.getInstance().addListener(this);
        Dronelink.getInstance().getSessionManager().addListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        Dronelink.getInstance().removeListener(this);
        Dronelink.getInstance().getSessionManager().removeListener(this);
        if (missionExecutor != null) {
            missionExecutor.removeListener(this);
        }
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
                    engaging = true;
                    stopCoundown(false);
                    try {
                        missionExecutor.engage(session);
                    }
                    catch (final MissionExecutor.DroneSerialNumberUnavailableException e) {
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
                        engaging = false;
                        getActivity().runOnUiThread(updateViews);
                    }
                }
                else {
                    countdownRemaining--;
                    Dronelink.getInstance().announce("" + (countdownRemaining + 1));
                    getActivity().runOnUiThread(updateViews);
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

    private void toggleMissionExpanded() {
        expanded = !expanded;
        getView().getLayoutParams().height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float)(expanded ? 135 : 70), getResources().getDisplayMetrics());
        getView().requestLayout();
        getActivity().runOnUiThread(updateViews);
    }

    private Runnable updateViews = new Runnable() {
        public void run() {
            final int visibility = missionExecutor == null ? View.INVISIBLE : View.VISIBLE;
            getView().setVisibility(visibility == View.INVISIBLE ? View.GONE : View.VISIBLE);
            detailsTextView.setVisibility(visibility == View.INVISIBLE || !expanded ? View.INVISIBLE : View.VISIBLE);

            if (missionExecutor == null) {
                primaryButton.setEnabled(false);
                primaryButton.setImageResource(R.drawable.baseline_play_arrow_white_48);
                return;
            }

            if (countdownTimer != null) {
                primaryButton.setBackgroundTintList(primaryEngagedColor);
                primaryButton.setImageResource(R.drawable.baseline_close_white_48);
                titleTextView.setText(getString(R.string.Mission_start_countdown) + " " + (countdownRemaining + 1));
                progressBar.setProgressTintList(primaryEngagedColor);
                closeButton.setVisibility(View.INVISIBLE);
                return;
            }

            final boolean engaged = engaging || missionExecutor.isEngaged();
            final double totalTime = missionExecutor.getEstimateTotalTime();
            final double timeElapsed = missionExecutor.getMissionExecutionDuration();
            final double timeRemaining = Math.max(totalTime - timeElapsed, 0);
            primaryButton.setEnabled(session != null);
            primaryButton.setImageResource(engaged ? R.drawable.baseline_pause_white_48 : R.drawable.baseline_play_arrow_white_48);
            primaryButton.setBackgroundTintList(session == null ? primaryDisconnectedColor : engaged ? primaryEngagedColor : primaryDisengagedColor);
            progressBar.setProgressTintList(engaged ? progressEngagedColor : progressDisengagedColor);
            progressBar.setProgress((int)(Math.min(totalTime == 0 ? 0.0 : timeElapsed / totalTime, 1.0) * 100));
            closeButton.setVisibility(engaged ? View.INVISIBLE : View.VISIBLE);
            titleTextView.setText(engaging ? getString(R.string.Mission_start_engaging) : missionExecutor.missionDescriptors.toString());
            timeElapsedTextView.setText(Dronelink.getInstance().format("timeElapsed", timeElapsed, "00:00"));
            timeRemainingTextView.setText(Dronelink.getInstance().format("timeElapsed", timeRemaining, "00:00"));
            if (expanded && missionExecutor.isEngaged()) {
                final StringBuilder messages = new StringBuilder();
                for (final MessageGroup messageGroup : missionExecutor.getExecutingMessageGroups()) {
                    if (messages.length() > 0) {
                        messages.append("\n\n");
                    }
                    messages.append(messageGroup.toString());
                }
                detailsTextView.setText(messages.toString());
            }
            else {
                detailsTextView.setText("");
            }
        }
    };

    @Override
    public void onOpened(final DroneSession session) {
        this.session = session;
        getActivity().runOnUiThread(updateViews);
        final MissionExecutor missionExecutor = Dronelink.getInstance().getMissionExecutor();
        if (missionExecutor != null) {
            new Thread(new Runnable() {
                public void run() {
                    missionExecutor.estimate(session);
                }
            }).start();
        }
    }

    @Override
    public void onClosed(final DroneSession session) {
        this.session = null;
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onRegistered(final String error) {
    }

    @Override
    public void onMissionLoaded(final MissionExecutor executor) {
        executor.addListener(this);
        missionExecutor = executor;
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onMissionUnloaded(final MissionExecutor executor) {
        executor.removeListener(this);
        missionExecutor = null;
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onFuncLoaded(final FuncExecutor executor) {
    }

    @Override
    public void onFuncUnloaded(final FuncExecutor executor) {
    }

    @Override
    public void onMissionEstimated(final MissionExecutor executor, final long durationMillis) {
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onMissionEngaged(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onMissionExecuted(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {
        engaging = false;
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

        engaging = false;
        final Component.ExecutionStatus status = executor.getStatus();
        if (status != null && status.completed) {
            Dronelink.getInstance().unloadMission();
            return;
        }

        getActivity().runOnUiThread(updateViews);
    }
}
