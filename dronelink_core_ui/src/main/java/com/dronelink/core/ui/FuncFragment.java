//  FuncFragment.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 2/25/20.
//  Copyright © 2020 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;

import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.dronelink.core.DatedValue;
import com.dronelink.core.DroneOffsets;
import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.FuncExecutor;
import com.dronelink.core.MissionExecutor;
import com.dronelink.core.ModeExecutor;
import com.dronelink.core.adapters.DroneStateAdapter;
import com.dronelink.core.adapters.RemoteControllerStateAdapter;
import com.dronelink.core.kernel.core.CameraFocusCalibration;
import com.dronelink.core.kernel.core.FuncInput;
import com.dronelink.core.kernel.core.GeoSpatial;
import com.dronelink.core.kernel.core.enums.VariableValueType;
import com.squareup.picasso.Picasso;
import com.stfalcon.imageviewer.StfalconImageViewer;
import com.stfalcon.imageviewer.loader.ImageLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class FuncFragment extends Fragment implements Dronelink.Listener, DroneSessionManager.Listener, FuncExecutor.Listener {
    private static FuncExecutor mostRecentExecuted;

    private DroneSession session;
    private FuncExecutor funcExecutor;
    private Button backButton;
    private Button nextButton;
    private Button primaryButton;
    private ImageView titleImageView;
    private TextView titleTextView;
    private TextView variableNameTextView;
    private TextView variableDescriptionTextView;
    private ImageView variableImageView;
    private Spinner variableSpinner;
    private RadioGroup variableRadioGroup;
    private RadioButton variableRadioButton1;
    private RadioButton variableRadioButton2;
    private EditText variableEditText;
    private Button variableDroneMarkButton;
    private ImageButton variableDroneClearButton;
    private TextView variableDroneTextView;
    private TextView variableSummaryTextView;
    private TextView progressTextView;
    private ImageButton dismissButton;

    private boolean intro = true;
    private int inputIndex = 0;
    private boolean isLast() { return funcExecutor != null && inputIndex == funcExecutor.getInputCount(); }
    private boolean hasInputs() { return funcExecutor != null && (funcExecutor.getInputCount() > 0 || funcExecutor.getDynamicInputs() != null); }
    private FuncInput getInput() { return funcExecutor == null ? null : funcExecutor.getInput(inputIndex); }
    private String getValueNumberMeasurementTypeDisplay(final int index) { return funcExecutor == null ? null : funcExecutor.readValueNumberMeasurementTypeDisplay(index); }
    private boolean executing = false;
    private Object value = null;
    private final long listenRCButtonsMillis = 100;
    private Timer listenRCButtonsTimer;
    private boolean c1PressedPrevious = false;
    private boolean c2PressedPrevious = false;

    public FuncFragment() {}

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_func, container, false);
    }

    private void onNext() {
        ((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(nextButton.getWindowToken(), 0);

        if (isLast()) {
            onPrimary();
            return;
        }

        if (!writeValue(true)) {
            return;
        }

        inputIndex += 1;
        addNextDynamicInput();
        readValue();
        getActivity().runOnUiThread(updateViews);
    }

    private void onPrimary() {
        if (funcExecutor == null) {
            return;
        }

        if (intro && hasInputs()) {
            if (mostRecentExecuted != null && mostRecentExecuted.id.equals(funcExecutor.id)) {
                final AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
                alertDialog.setTitle(R.string.Func_cachedInputs_title);
                alertDialog.setMessage(R.string.Func_cachedInputs_message);
                alertDialog.setPositiveButton(getString(R.string.Func_cachedInputs_action_new), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface d, int i) {
                        d.dismiss();
                        finishIntro(false);
                    }
                });
                alertDialog.setNegativeButton(getString(R.string.Func_cachedInputs_action_previous), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface d, int i) {
                        d.dismiss();
                        funcExecutor.addCachedInputs(mostRecentExecuted);
                        finishIntro(true);
                    }
                });
                alertDialog.show();
            }
            else {
                finishIntro(false);
            }
            return;
        }

        if (!executing) {
            executing = true;
            getActivity().runOnUiThread(updateViews);
            funcExecutor.execute(session, new FuncExecutor.FuncExecuteError() {
                @Override
                public void error(final String value) {
                    showToast(value);
                    executing = false;
                    getActivity().runOnUiThread(updateViews);
                }
            });
        }
    }

    private void finishIntro(final boolean previous) {
        intro = false;
        if (funcExecutor.getInputCount() == 0) {
            addNextDynamicInput();
        }
        else if (previous) {
            inputIndex = funcExecutor.getInputCount();
        }
        readValue();
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onViewCreated(final View view, @Nullable final Bundle savedInstanceState) {
        titleImageView = getView().findViewById(R.id.titleImageView);
        titleTextView = getView().findViewById(R.id.titleTextView);
        variableNameTextView = getView().findViewById(R.id.variableNameTextView);
        variableDescriptionTextView = getView().findViewById(R.id.variableDescriptionTextView);
        variableDescriptionTextView.setMovementMethod(new ScrollingMovementMethod());
        variableImageView = getView().findViewById(R.id.variableImageView);
        variableSpinner = getView().findViewById(R.id.variableSpinner);
        variableRadioGroup = getView().findViewById(R.id.variableRadioGroup);
        variableRadioButton1 = getView().findViewById(R.id.variableRadioButton1);
        variableRadioButton2 = getView().findViewById(R.id.variableRadioButton2);
        variableEditText = getView().findViewById(R.id.variableEditText);
        variableDroneMarkButton = getView().findViewById(R.id.variableDroneMarkButton);
        variableDroneClearButton = getView().findViewById(R.id.variableDroneClearButton);
        variableDroneTextView = getView().findViewById(R.id.variableDroneTextView);
        variableDroneTextView.setMovementMethod(new ScrollingMovementMethod());
        variableSummaryTextView = getView().findViewById(R.id.variableSummaryTextView);
        variableSummaryTextView.setMovementMethod(new ScrollingMovementMethod());
        progressTextView = getView().findViewById(R.id.progressTextView);
        dismissButton = getView().findViewById(R.id.dismissButton);
        backButton = getView().findViewById(R.id.backButton);
        nextButton = getView().findViewById(R.id.nextButton);
        primaryButton = getView().findViewById(R.id.primaryButton);

        primaryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                onPrimary();
            }
        });

        dismissButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View view) {
                Dronelink.getInstance().unloadFunc();
            }
        });

        variableImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new StfalconImageViewer.Builder<>(getContext(), Collections.singletonList(variableImageView.getDrawable()), new ImageLoader<Drawable>() {
                    @Override
                    public void loadImage(final ImageView imageView, final Drawable image) {
                        imageView.setImageDrawable(image);
                    }
                }).show();
            }
        });

        variableDroneMarkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                onDroneMark();
            }
        });

        variableDroneClearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                if (funcExecutor == null) {
                    return;
                }

                funcExecutor.clearValue(inputIndex);
                readValue();
            }
        });

        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                ((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0);

                inputIndex -= 1;
                if (funcExecutor != null) {
                    if (inputIndex < funcExecutor.getInputCount() - 1) {
                        funcExecutor.removeLastDynamicInput();
                    }
                }
                readValue();
                getActivity().runOnUiThread(updateViews);
            }
        });

        nextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(final View v) {
                onNext();
            }
        });
        getActivity().runOnUiThread(updateViews);

        listenRCButtonsTimer = new Timer();
        listenRCButtonsTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                listenRCButtonsTimer();
            }
        }, 0, listenRCButtonsMillis);
    }

    private void listenRCButtonsTimer() {
        getActivity().runOnUiThread(listenRCButtons);
    }

    private Runnable listenRCButtons = new Runnable() {
        public void run() {
            final DroneSession sessionLocal = session;
            if (getView() == null || getView().getVisibility() != View.VISIBLE || sessionLocal == null) {
                return;
            }

            final DatedValue<RemoteControllerStateAdapter> remoteControllerState = sessionLocal.getRemoteControllerState(0);
            if (remoteControllerState != null && remoteControllerState.value != null) {
                if (c1PressedPrevious && !remoteControllerState.value.getC1Button().pressed && variableDroneMarkButton.getVisibility() == View.VISIBLE) {
                    onDroneMark();
                }

                if (c2PressedPrevious && !remoteControllerState.value.getC2Button().pressed) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onNext();
                        }
                    });
                }

                c1PressedPrevious = remoteControllerState.value.getC1Button().pressed;
                c2PressedPrevious = remoteControllerState.value.getC2Button().pressed;
            }
            else {
                c1PressedPrevious = false;
                c2PressedPrevious = false;
            }
        }
    };

    private void addNextDynamicInput() {
        if (funcExecutor == null) {
            return;
        }

        funcExecutor.addNextDynamicInput(session, new FuncExecutor.FuncExecuteError() {
            @Override
            public void error(final String value) {
                showToast(value);
                inputIndex -= 1;
                if (inputIndex < 0) {
                    inputIndex = 0;
                    intro = true;
                }
                else {
                    readValue();
                }

                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        getActivity().runOnUiThread(updateViews);
                    }
                });
            }
        });
    }

    private boolean writeValue(final boolean next) {
        final FuncInput input = getInput();
        if (input == null) {
            return false;
        }

        Object value = null;
        switch (input.variable.valueType) {
            case BOOLEAN:
                if (variableRadioButton1.isChecked()) {
                    value = true;
                }
                else if (variableRadioButton2.isChecked()) {
                    value = false;
                }
                break;

            case NUMBER:
                final String valueDouble = variableEditText.getText().toString();
                if (!valueDouble.isEmpty()) {
                    value = Double.parseDouble(valueDouble);
                }
                break;

            case STRING:
                if (input.enumValues == null) {
                    final String valueString = variableEditText.getText().toString();
                    if (!valueString.isEmpty()) {
                        value = valueString;
                    }
                }
                else {
                    if (input.enumValues.length == 2) {
                        if (variableRadioButton1.isChecked()) {
                            value = input.enumValues[0];
                        }
                        else if (variableRadioButton2.isChecked()) {
                            value = input.enumValues[1];
                        }
                    }
                    else {
                        value = variableSpinner.getSelectedItem();
                        if (value == getString(R.string.Func_input_choose)) {
                            value = null;
                        }
                    }
                }
                break;

            case DRONE:
                if (next) {
                    if (funcExecutor.readValue(inputIndex) == null && !input.optional) {
                        showToast(getString(R.string.Func_input_required));
                        return false;
                    }
                    return true;
                }
                value = session;
                break;
        }

        if (!input.optional && value == null && input.variable.valueType != VariableValueType.NULL) {
            showToast(getString(R.string.Func_input_required));
            return false;
        }

        funcExecutor.writeValue(inputIndex, value);
        return true;
    }

    private void readValue() {
        variableEditText.setText("");
        variableDroneTextView.setText("");

        final FuncInput input = getInput();
        if (input == null) {
            value = null;
            updateViews.run();
            return;
        }

        final List<String> enumValues = new ArrayList<>();
        enumValues.add(getString(R.string.Func_input_choose));
        switch (input.variable.valueType) {
            case BOOLEAN:
                variableRadioButton1.setText(getString(R.string.yes));
                variableRadioButton2.setText(getString(R.string.no));
                break;

            case STRING:
                if (input.enumValues != null) {
                    for (final String enumValue : input.enumValues) {
                        enumValues.add(enumValue);
                    }
                }
                break;
        }

        if (enumValues.size() == 3) {
            variableRadioButton1.setText(enumValues.get(1));
            variableRadioButton2.setText(enumValues.get(2));
        }
        else {
            variableSpinner.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_dropdown_item, enumValues.toArray(new String[0])));
        }

        final Object value = funcExecutor.readValue(inputIndex);
        if (value == null) {
            this.value = null;
            updateViews.run();
            return;
        }

        this.value = value;
        updateViews.run();

        if (input.variable.valueType == VariableValueType.DRONE) {
            if (value instanceof GeoSpatial) {
                variableDroneTextView.setText((String)funcExecutor.readValue(inputIndex, null, true));
            }

            if (value instanceof GeoSpatial[]) {
                final StringBuilder valueString = new StringBuilder();
                final Object[] valueArray = ((Object[])value);
                for (int i = valueArray.length - 1; i >= 0; i--) {
                    valueString.append(i + 1).append(". ").append((String)funcExecutor.readValue(inputIndex, i, true));
                    if (i > 0) {
                        valueString.append("\n");
                    }
                }
                variableDroneTextView.setText(valueString.toString());
            }
            return;
        }

        if (value instanceof Boolean) {
            variableRadioGroup.clearCheck();
            if ((Boolean)value) {
                variableRadioButton1.setChecked(true);
            }
            else {
                variableRadioButton2.setChecked(true);
            }
        }

        if (value instanceof Integer) {
            variableEditText.setText(value.toString());
        }

        if (value instanceof Double) {
            variableEditText.setText(value.toString());
        }

        if (value instanceof String) {
            variableEditText.setText((String)value);
            if (input.enumValues != null) {
                if (enumValues.size() == 3) {
                    variableRadioButton1.setChecked(enumValues.get(1).equals(value));
                    variableRadioButton2.setChecked(enumValues.get(2).equals(value));
                }
                else {
                    variableSpinner.setSelection(enumValues.indexOf(value));
                }
            }
        }
    }

    private void updateHeight(float height) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float maxHeight = Math.max(165, (displayMetrics.heightPixels / displayMetrics.density) - 265.0f);
        getView().getLayoutParams().height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, Math.min(height, maxHeight), displayMetrics);
        getView().requestLayout();
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
        if (listenRCButtonsTimer != null) {
            listenRCButtonsTimer.cancel();
        }
        Dronelink.getInstance().removeListener(this);
        Dronelink.getInstance().getSessionManager().removeListener(this);
        final FuncExecutor funcExecutor = this.funcExecutor;
        if (funcExecutor != null) {
            funcExecutor.removeListener(this);
        }
    }

    private Runnable updateViews = new Runnable() {
        public void run() {
            if (!isAdded()) {
                return;
            }

            FuncExecutor funcExecutorLocal = funcExecutor;
            final int visibility = funcExecutorLocal == null ? View.INVISIBLE : View.VISIBLE;
            getView().setVisibility(visibility == View.INVISIBLE ? View.GONE : View.VISIBLE);

            if (funcExecutorLocal == null) {
                return;
            }

            titleImageView.setVisibility(View.INVISIBLE);
            titleTextView.setVisibility(View.INVISIBLE);
            variableNameTextView.setVisibility(View.INVISIBLE);
            variableDescriptionTextView.setVisibility(View.INVISIBLE);
            variableImageView.setVisibility(View.INVISIBLE);
            variableSpinner.setVisibility(View.INVISIBLE);
            variableRadioGroup.setVisibility(View.INVISIBLE);
            variableEditText.setVisibility(View.INVISIBLE);
            variableDroneMarkButton.setVisibility(View.INVISIBLE);
            variableDroneClearButton.setVisibility(View.INVISIBLE);
            variableDroneTextView.setVisibility(View.INVISIBLE);
            variableSummaryTextView.setVisibility(View.INVISIBLE);

            if (intro) {
                titleImageView.setVisibility(View.VISIBLE);
                titleTextView.setVisibility(View.VISIBLE);
                backButton.setVisibility(View.INVISIBLE);
                nextButton.setVisibility(View.INVISIBLE);
                progressTextView.setVisibility(View.INVISIBLE);
                primaryButton.setVisibility(View.VISIBLE);

                titleTextView.setText(funcExecutorLocal.getDescriptors().name);
                primaryButton.setText(getString(executing ? R.string.Func_primary_executing : (hasInputs() ? R.string.Func_primary_intro : R.string.Func_primary_execute)));

                if (funcExecutorLocal.getIntroImageUrl() != null && !funcExecutorLocal.getIntroImageUrl().isEmpty()) {
                    updateHeight(530);
                    Picasso.get().load(funcExecutorLocal.getIntroImageUrl()).into(variableImageView);
                    variableImageView.setVisibility(View.VISIBLE);
                    final ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) variableImageView.getLayoutParams();
                    lp.bottomToTop = R.id.nextButton;
                    variableImageView.setLayoutParams(lp);
                }
                else {
                    updateHeight(135);
                    variableDescriptionTextView.setText(funcExecutorLocal.getDescriptors().description);
                    variableDescriptionTextView.setVisibility(View.VISIBLE);
                }
                return;
            }

            primaryButton.setVisibility(View.INVISIBLE);
            backButton.setVisibility(inputIndex > 0 ? View.VISIBLE : View.INVISIBLE);
            nextButton.requestLayout();
            nextButton.setVisibility(View.VISIBLE);
            nextButton.setText(getString(isLast() ? R.string.Func_primary_execute : R.string.Func_next));
            progressTextView.setVisibility(isLast() ? View.INVISIBLE : View.VISIBLE);
            progressTextView.setText(inputIndex + 1 == funcExecutorLocal.getInputCount() ? ("" + (inputIndex + 1)) : ((inputIndex + 1) + " / " + (funcExecutorLocal == null ? 0 : funcExecutorLocal.getInputCount())));

            final FuncInput input = getInput();
            if (input != null) {
                if (input.imageUrl != null && !input.imageUrl.isEmpty()) {
                    updateHeight(530);
                }
                else if (input.descriptors.description != null && !input.descriptors.description.isEmpty()) {
                    updateHeight(165);
                }
                else {
                    updateHeight(135);
                }

                variableNameTextView.setVisibility(View.VISIBLE);
                String name = input.descriptors.name == null ? "" : input.descriptors.name;
                String valueNumberMeasurementTypeDisplay = getValueNumberMeasurementTypeDisplay(inputIndex);
                if (valueNumberMeasurementTypeDisplay != null && !valueNumberMeasurementTypeDisplay.isEmpty()) {
                    name = name + " (" + valueNumberMeasurementTypeDisplay + ")";
                }

                if (!input.optional && input.variable.valueType != VariableValueType.NULL) {
                    name = name + " *";
                }
                variableNameTextView.setText(name);

                if (input.imageUrl != null && !input.imageUrl.isEmpty()) {
                    Picasso.get().load(input.imageUrl).into(variableImageView);
                    variableImageView.setVisibility(View.VISIBLE);
                    final ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) variableImageView.getLayoutParams();
                    lp.bottomToTop = input.variable.valueType == VariableValueType.NULL ? R.id.nextButton : R.id.variableDroneMarkButton;
                    variableImageView.setLayoutParams(lp);
                }
                else if (!(input.descriptors.description == null || input.descriptors.description.isEmpty())) {
                    variableDescriptionTextView.setVisibility(View.VISIBLE);
                    variableDescriptionTextView.setText(input.descriptors.description);
                }

                switch (input.variable.valueType) {
                    case BOOLEAN:
                        variableRadioGroup.setVisibility(View.VISIBLE);
                        break;

                    case NUMBER:
                        variableEditText.setVisibility(View.VISIBLE);
                        variableEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
                        variableEditText.requestFocus();
                        break;

                    case STRING:
                        if (input.enumValues == null) {
                            variableEditText.setVisibility(View.VISIBLE);
                            variableEditText.setInputType(InputType.TYPE_CLASS_TEXT);
                            variableEditText.requestFocus();
                        }
                        else {
                            if (input.enumValues.length == 2) {
                                variableRadioGroup.setVisibility(View.VISIBLE);
                            }
                            else {
                                variableSpinner.setVisibility(View.VISIBLE);
                            }
                        }
                        break;

                    case DRONE:
                        variableDroneMarkButton.setVisibility(View.VISIBLE);
                        variableDroneTextView.setVisibility(View.VISIBLE);
                        variableDroneClearButton.setVisibility(value == null ? View.INVISIBLE : View.VISIBLE);
                        break;
                }

                return;
            }

            if (isLast()) {
                updateHeight(530);
                variableNameTextView.setVisibility(View.VISIBLE);
                variableNameTextView.setText(getString(R.string.Func_input_summary));
                variableSummaryTextView.setVisibility(View.VISIBLE);
                variableSummaryTextView.setText(getSummary());
            }
        }
    };

    private String getSummary() {
        final FuncExecutor funcExecutor = this.funcExecutor;
        if (funcExecutor == null) {
            return "";
        }

        final StringBuilder summary = new StringBuilder();
        final int inputCount = funcExecutor.getInputCount();
        for (int i = 0; i < inputCount; i++) {
            final FuncInput input = funcExecutor.getInput(i);
            String details = getString(R.string.Func_input_none);
            final Object value = funcExecutor.readValue(i, null, true);
            if (value instanceof String) {
                details = (String)value;
            }

            String name = input.descriptors.name;
            final String valueNumberMeasurementTypeDisplay = getValueNumberMeasurementTypeDisplay(i);
            if (valueNumberMeasurementTypeDisplay != null && !valueNumberMeasurementTypeDisplay.isEmpty()) {
                name = name + " (" + valueNumberMeasurementTypeDisplay + ")";
            }

            summary.append(i + 1).append(". ").append(name).append("\n").append(details);
            if (i < inputCount - 1) {
                summary.append("\n\n");
            }
        }
        return summary.toString();
    }

    private void onDroneMark() {
        final DroneSession session = this.session;

        if (session == null) {
            showToast(getString(R.string.Func_input_drone_unavailable));
            return;
        }

        final DroneStateAdapter state = session.getState().value;
        Location location = null;
        if (state != null) {
            location = state.getLocation();
        }

        if (location == null) {
            showToast(getString(R.string.Func_input_location_unavailable));
            return;
        }

        final FuncInput input = getInput();
        if (input != null && input.extensions != null && input.extensions.droneOffsetsCoordinateReference) {
            Dronelink.getInstance().droneOffsets.droneCoordinateReference = location;
        }

        writeValue(false);
        readValue();
    }

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
    public void onRegistered(final String error) {
    }

    @Override
    public void onMissionLoaded(final MissionExecutor executor) {
    }

    @Override
    public void onMissionUnloaded(final MissionExecutor executor) {
    }

    @Override
    public void onFuncLoaded(final FuncExecutor executor) {
        funcExecutor = executor;
        executor.addListener(this);
        inputIndex = 0;
        intro = true;
        getActivity().runOnUiThread(updateViews);

        final String[] urls = executor.getUrls();
        if (urls != null) {
            DronelinkUI.getInstance().cacheImages(urls);
        }
    }

    @Override
    public void onFuncUnloaded(final FuncExecutor executor) {
        funcExecutor = null;
        executor.removeListener(this);
        getActivity().runOnUiThread(updateViews);
    }

    @Override
    public void onModeLoaded(final ModeExecutor executor) {}

    @Override
    public void onModeUnloaded(final ModeExecutor executor) {}

    @Override
    public void onCameraFocusCalibrationRequested(final CameraFocusCalibration value) {}

    @Override
    public void onCameraFocusCalibrationUpdated(final CameraFocusCalibration value) {}


    @Override
    public void onFuncInputsChanged(final FuncExecutor executor) {}

    @Override
    public void onFuncExecuted(final FuncExecutor executor) {
        mostRecentExecuted = executor;
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