//  ModeFragment.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 10/29/20.
//  Copyright © 2020 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.Executor;
import com.dronelink.core.FuncExecutor;
import com.dronelink.core.MissionExecutor;
import com.dronelink.core.ModeExecutor;
import com.dronelink.core.kernel.core.Message;
import com.dronelink.core.kernel.core.MessageGroup;

import java.util.Timer;
import java.util.TimerTask;

public class ModeFragment extends Fragment implements Dronelink.Listener, DroneSessionManager.Listener, ModeExecutor.Listener {
    private DroneSession session;
    private ModeExecutor modeExecutor;

    private ImageButton primaryButton;
    private TextView titleTextView;
    private TextView subtitleTextView;
    private TextView executionDurationTextView;
    private TextView messagesTextView;
    private ImageButton dismissButton;

    private final ColorStateList primaryEngagedColor = ColorStateList.valueOf(Color.parseColor("#f50057"));
    private final ColorStateList primaryDisengagedColor = ColorStateList.valueOf(Color.parseColor("#4527a0"));
    private final ColorStateList primaryDisconnectedColor = ColorStateList.valueOf(Color.parseColor("#616161"));

    private Timer countdownTimer;
    private int countdownRemaining = 0;
    private final long updateMillis = 500;
    private long lastUpdatedMillis = 0;
    private boolean expanded = false;

    public ModeFragment() {}

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_mode, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable final Bundle savedInstanceState) {
        primaryButton = view.findViewById(R.id.primaryButton);
        primaryButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (modeExecutor == null) {
                    return;
                }

                if (countdownTimer != null) {
                    stopCoundown(true);
                    return;
                }

                if (modeExecutor.isEngaged()) {
                    modeExecutor.disengage(new Message(getString(R.string.ModeDisengageReason_user_disengaged)));
                    return;
                }

                if (session == null) {
                    return;
                }

                startCountdown();
            }
        });
        dismissButton = getView().findViewById(R.id.dismissButton);
        dismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Dronelink.getInstance().unloadMode();
            }
        });
        titleTextView = getView().findViewById(R.id.titleTextView);
        titleTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleModeExpanded();
            }
        });
        subtitleTextView = getView().findViewById(R.id.subtitleTextView);
        executionDurationTextView = getView().findViewById(R.id.executionDurationTextView);
        messagesTextView = getView().findViewById(R.id.detailsTextView);
        messagesTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleModeExpanded();
            }
        });

        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dronelink.getInstance().getTargetDroneSessionManager().addListener(this);
        Dronelink.getInstance().addListener(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        Dronelink.getInstance().getTargetDroneSessionManager().removeListener(this);
        Dronelink.getInstance().removeListener(this);
        final ModeExecutor modeExecutor = this.modeExecutor;
        if (modeExecutor != null) {
            modeExecutor.removeListener(this);
        }
    }

    private void startCountdown() {
        countdownRemaining = 3;
        countdownTimer = new Timer();
        countdownTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (session == null || modeExecutor == null) {
                    stopCoundown(true);
                    return;
                }

                if (countdownRemaining == 0) {
                    stopCoundown(false);
                    engage();
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
                Dronelink.getInstance().announce(getString(R.string.Executable_start_cancelled));
            }
        }
    }

    private void engage() {
        final ModeExecutor modeExecutor = this.modeExecutor;
        final DroneSession session = this.session;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (modeExecutor == null || session == null) {
                    return;
                }

                try {
                    modeExecutor.engage(session, new Executor.EngageDisallowed() {
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
                            alertDialog.setTitle(getString(R.string.Executable_start_engage_droneSerialNumberUnavailable_title));
                            alertDialog.setMessage(getString(R.string.Executable_start_engage_droneSerialNumberUnavailable_message));
                            alertDialog.setPositiveButton(getString(R.string.Executable_dismiss), new DialogInterface.OnClickListener() {
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

    private void toggleModeExpanded() {
        toggleModeExpanded(!expanded);
    }

    private void toggleModeExpanded(final boolean expanded) {
        this.expanded = expanded;
        getView().getLayoutParams().height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, (float)(expanded ? 135 : 70), getResources().getDisplayMetrics());
        getView().requestLayout();
        getActivity().runOnUiThread(updateViews);
    }

    private Runnable updateViews = new Runnable() {
        public void run() {
            final View view = getView();
            if (view == null) {
                return;
            }

            final ModeExecutor modeExecutorLocal = modeExecutor;
            if (modeExecutorLocal == null) {
                view.setVisibility(View.GONE);
                return;
            }
            view.setVisibility(View.VISIBLE);

            titleTextView.setText(modeExecutorLocal.descriptors.toString());

            if (countdownTimer != null) {
                primaryButton.setVisibility(View.VISIBLE);
                subtitleTextView.setVisibility(View.VISIBLE);
                executionDurationTextView.setVisibility(View.INVISIBLE);
                dismissButton.setVisibility(View.VISIBLE);
                messagesTextView.setVisibility(View.INVISIBLE);

                primaryButton.setEnabled(true);
                primaryButton.setBackgroundTintList(primaryEngagedColor);
                primaryButton.setImageResource(R.drawable.baseline_close_white_48);
                subtitleTextView.setText(getString(R.string.Executable_start_countdown) + " " + (countdownRemaining + 1));
                return;
            }

            if (modeExecutorLocal.isEngaging()) {
                primaryButton.setVisibility(View.INVISIBLE);
                subtitleTextView.setVisibility(View.VISIBLE);
                executionDurationTextView.setVisibility(View.INVISIBLE);
                dismissButton.setVisibility(View.INVISIBLE);
                messagesTextView.setVisibility(View.INVISIBLE);

                subtitleTextView.setText(getString(R.string.Executable_start_engaging));
                return;
            }


            primaryButton.setVisibility(View.VISIBLE);
            subtitleTextView.setVisibility(View.VISIBLE);
            executionDurationTextView.setVisibility(modeExecutorLocal.isEngaged() ? View.VISIBLE : View.INVISIBLE);
            dismissButton.setVisibility(modeExecutorLocal.isEngaged() ? View.INVISIBLE : View.VISIBLE);
            messagesTextView.setVisibility(expanded ? View.VISIBLE : View.INVISIBLE);

            primaryButton.setEnabled(session != null);
            executionDurationTextView.setText(Dronelink.getInstance().format("timeElapsed", modeExecutorLocal.getExecutionDuration(), "00:00"));
            if (modeExecutorLocal.isEngaged()) {
                final Message summaryMessage = modeExecutorLocal.getSummaryMessage();
                subtitleTextView.setText(summaryMessage == null ? "" : summaryMessage.toString());
                if (expanded) {
                    final StringBuilder messages = new StringBuilder();
                    for (final MessageGroup messageGroup : modeExecutorLocal.getExecutingMessageGroups()) {
                        if (messages.length() > 0) {
                            messages.append("\n");
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
                subtitleTextView.setText(getString(session == null ? R.string.Mode_disconnected : R.string.Mode_ready));
                messagesTextView.setText("");
                primaryButton.setBackgroundTintList(session == null ? primaryDisconnectedColor : primaryDisengagedColor);
                primaryButton.setImageResource(R.drawable.baseline_play_arrow_white_48);
            }
        }
    };

    @Override
    public void onOpened(final DroneSession session) {
        this.session = session;
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onClosed(final DroneSession session) {
        this.session = null;
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onRegistered(final String error) {}

    @Override
    public void onDroneSessionManagerAdded(final DroneSessionManager manager) {}

    @Override
    public void onMissionLoaded(final MissionExecutor executor) {}

    @Override
    public void onMissionUnloaded(final MissionExecutor executor) {}

    @Override
    public void onFuncLoaded(final FuncExecutor executor) {}

    @Override
    public void onFuncUnloaded(final FuncExecutor executor) {}

    @Override
    public void onModeLoaded(final ModeExecutor executor) {
        modeExecutor = executor;
        executor.addListener(this);
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onModeUnloaded(final ModeExecutor executor) {
        modeExecutor = null;
        executor.removeListener(this);
        getActivity().runOnUiThread(updateViews);
    }


    @Override
    public void onModeEngaging(final ModeExecutor executor) {
        Dronelink.getInstance().announce(getString(R.string.Mode_engaging));
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onModeEngaged(final ModeExecutor executor, final Executor.Engagement engagement) {
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onModeExecuted(final ModeExecutor executor, final Executor.Engagement engagement) {
        if (System.currentTimeMillis() - lastUpdatedMillis > updateMillis) {
            lastUpdatedMillis = System.currentTimeMillis();
            getActivity().runOnUiThread(updateViews);
        }
    }

    @Override
    public void onModeDisengaged(final ModeExecutor executor, final Executor.Engagement engagement, final Message reason) {
        if (!getString(R.string.ModeDisengageReason_user_disengaged).equals(reason.title)) {
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

        Dronelink.getInstance().announce(getString(R.string.Mode_disengaged));
        getActivity().runOnUiThread(updateViews);
    }
}