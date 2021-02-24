//  MapboxMapFragment.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 11/8/19.
//  Copyright © 2019 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dronelink.core.CameraFile;
import com.dronelink.core.Convert;
import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.FuncExecutor;
import com.dronelink.core.MissionExecutor;
import com.dronelink.core.ModeExecutor;
import com.dronelink.core.adapters.DroneStateAdapter;
import com.dronelink.core.command.CommandError;
import com.dronelink.core.kernel.command.Command;
import com.dronelink.core.kernel.core.FuncInput;
import com.dronelink.core.kernel.core.FuncMapOverlay;
import com.dronelink.core.kernel.core.GeoCoordinate;
import com.dronelink.core.kernel.core.GeoSpatial;
import com.dronelink.core.kernel.core.Message;
import com.dronelink.core.kernel.core.PlanRestrictionZone;
import com.dronelink.core.kernel.core.enums.VariableValueType;
import com.mapbox.android.core.location.LocationEngineCallback;
import com.mapbox.android.core.location.LocationEngineResult;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.annotations.Annotation;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.PolygonOptions;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.PropertyFactory;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MapboxMapFragment extends Fragment implements Dronelink.Listener, DroneSessionManager.Listener, DroneSession.Listener, MissionExecutor.Listener, FuncExecutor.Listener, ModeExecutor.Listener, OnMapReadyCallback {
    private DroneSession session;
    private MissionExecutor missionExecutor;
    private FuncExecutor funcExecutor;
    private ModeExecutor modeExecutor;
    private com.mapbox.mapboxsdk.maps.MapView mapView;
    private MapboxMap map;
    private Annotation missionRequiredTakeoffAreaAnnotation;
    private List<Annotation> missionRestrictionZoneAnnotations = new ArrayList<>();
    private Annotation missionEstimateBackgroundAnnotation;
    private Annotation missionEstimateForegroundAnnotation;
    private Annotation missionReengagementEstimateBackgroundAnnotation;
    private Annotation missionReengagementEstimateForegroundAnnotation;
    private List<Marker> funcDroneAnnotations = new ArrayList<>();
    private List<Annotation> funcMapOverlayAnnotations = new ArrayList<>();
    private Timer updateTimer;
    private final long updateMillis = 100;
    private boolean missionCentered = false;

    private DroneStateAdapter getDroneState() {
        final DroneSession session = this.session;
        if (session == null || session.getState() == null) {
            return null;
        }
        return session.getState().value;
    }

    public MapboxMapFragment() {}

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_mapbox_map, container, false);
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return view;
    }

    @Override
    public void onViewCreated(final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mapView = getView().findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
        Dronelink.getInstance().getSessionManager().removeListener(this);
        Dronelink.getInstance().removeListener(this);

        final DroneSession sessionLocal = this.session;
        if (sessionLocal != null) {
            sessionLocal.removeListener(this);
        }

        final MissionExecutor missionExecutor = this.missionExecutor;
        if (missionExecutor != null) {
            missionExecutor.removeListener(this);
        }

        final FuncExecutor funcExecutor = this.funcExecutor;
        if (funcExecutor != null) {
            funcExecutor.removeListener(this);
        }

        final ModeExecutor modeExecutor = this.modeExecutor;
        if (modeExecutor != null) {
            modeExecutor.removeListener(this);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onDestroy() {
        if (updateTimer != null) {
            updateTimer.cancel();
        }

        super.onDestroy();

        mapView.onDestroy();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    public interface MoreMenuItem {
        String getTitle();
        void onClick();
    }

    public void onMore(final Context context, final View anchor, final MapboxMapFragment.MoreMenuItem[] actions) {
        final PopupMenu actionSheet = new PopupMenu(context, anchor);

        if (actions != null) {
            for (final MapboxMapFragment.MoreMenuItem action : actions) {
                actionSheet.getMenu().add(action.getTitle());
            }
        }

        actionSheet.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                if (actions != null) {
                    for (final MapboxMapFragment.MoreMenuItem action : actions) {
                        if (item.getTitle() == action.getTitle()) {
                            action.onClick();
                            return true;
                        }
                    }
                }

                return false;
            }
        });

        actionSheet.show();
    }

    private void updateTimer() {
        getActivity().runOnUiThread(update);
    }

    private Runnable update = new Runnable() {
        public void run() {
            final Style style = map.getStyle();
            final DroneStateAdapter state = getDroneState();
            if (style == null || state == null) {
                return;
            }

            final Location droneHomeLocation = state.getHomeLocation();
            if (droneHomeLocation != null) {
                final GeoJsonSource droneHomeSource = style.getSourceAs("drone-home");
                droneHomeSource.setGeoJson(Feature.fromGeometry(Point.fromLngLat(droneHomeLocation.getLongitude(), droneHomeLocation.getLatitude())));
            }

            final Location droneLocation = state.getLocation();
            if (droneLocation != null) {
                final GeoJsonSource droneSource = style.getSourceAs("drone");
                droneSource.setGeoJson(Feature.fromGeometry(Point.fromLngLat(droneLocation.getLongitude(), droneLocation.getLatitude())));
                style.getLayerAs("drone").setProperties(PropertyFactory.iconRotate((float)(Convert.RadiansToDegrees(state.getOrientation().getYaw()) - map.getCameraPosition().bearing)));
            }
        }
    };

    @SuppressWarnings({"MissingPermission"})
    private Runnable updateMissionRequiredTakeoffArea = new Runnable() {
        public void run() {
            if (map == null) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getActivity().runOnUiThread(updateMissionRequiredTakeoffArea);
                    }
                }, 100);
                return;
            }

            if (missionRequiredTakeoffAreaAnnotation != null) {
                map.removeAnnotation(missionRequiredTakeoffAreaAnnotation);
            }


            final MissionExecutor missionExecutorLocal = missionExecutor;
            if (missionExecutorLocal != null) {
                final MissionExecutor.TakeoffArea requiredTakeoffArea = missionExecutorLocal.requiredTakeoffArea;
                if (requiredTakeoffArea != null) {
                    final Location takeoffLocation = requiredTakeoffArea.coordinate.getLocation();
                    final List<LatLng> takeoffAreaPoints = new LinkedList<>();
                    for (int i = 0; i < 100; i++) {
                        final Location location = Convert.locationWithBearing(takeoffLocation, (double)i / 100.0 * Math.PI * 2.0, requiredTakeoffArea.distanceTolerance.horizontal);
                        takeoffAreaPoints.add(new LatLng(location.getLatitude(), location.getLongitude()));
                    }
                    missionRequiredTakeoffAreaAnnotation = map.addPolygon(new PolygonOptions().addAll(takeoffAreaPoints).alpha((float)0.25).fillColor(Color.parseColor("#ffa726")));
                }
            }
        }
    };

    @SuppressWarnings({"MissingPermission"})
    private Runnable updateMissionRestrictionZones = new Runnable() {
        public void run() {
            if (map == null) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getActivity().runOnUiThread(updateMissionRestrictionZones);
                    }
                }, 100);
                return;
            }

            for (final Annotation missionRestrictionZoneAnnotation : missionRestrictionZoneAnnotations) {
                map.removeAnnotation(missionRestrictionZoneAnnotation);
            }
            missionRestrictionZoneAnnotations.clear();

            final MissionExecutor missionExecutorLocal = missionExecutor;
            if (missionExecutorLocal != null) {
                final PlanRestrictionZone[] restrictionZones = missionExecutorLocal.getRestrictionZones();
                if (restrictionZones != null) {
                    for (int i = 0; i < restrictionZones.length; i++) {
                        final PlanRestrictionZone restrictionZone = restrictionZones[i];
                        final GeoCoordinate[] coordinates = missionExecutorLocal.getRestrictionZoneBoundaryCoordinates(i);
                        if (coordinates == null) {
                            continue;
                        }

                        final List<LatLng> points = new LinkedList<>();
                        switch (restrictionZone.zone.shape) {
                            case CIRCLE:
                                final Location center = coordinates[0].getLocation();
                                final double radius = center.distanceTo(coordinates[1].getLocation());
                                for (int p = 0; p < 100; p++) {
                                    final Location location = Convert.locationWithBearing(center, (double)p / 100.0 * Math.PI * 2.0, radius);
                                    points.add(new LatLng(location.getLatitude(), location.getLongitude()));
                                }
                                break;

                            case POLYGON:
                                for (int c = 0; c < coordinates.length; c++) {
                                    points.add(new LatLng(coordinates[c].latitude, coordinates[c].longitude));
                                }
                                break;
                        }

                        final int fillColor = DronelinkUI.parseHexColor(restrictionZone.zone.color, Color.argb(255,255, 23, 68));
                        final Annotation missionRestrictionZoneAnnotation = map.addPolygon(new PolygonOptions().addAll(points)
                                .alpha(restrictionZone.zone.color != null && restrictionZone.zone.color.length() == 9 ? ((float)Color.alpha(fillColor)) / 255f : (float)0.5)
                                .fillColor(fillColor)
                                .strokeColor(DronelinkUI.parseHexColor(restrictionZone.zone.color, Color.argb((int)(255 * 0.7), 255, 23, 68))));
                        missionRestrictionZoneAnnotations.add(missionRestrictionZoneAnnotation);
                    }
                }
            }
        }
    };

    @SuppressWarnings({"MissingPermission"})
    private Runnable updateMissionEstimate = new Runnable() {
        public void run() {
            if (map == null) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        getActivity().runOnUiThread(updateMissionEstimate);
                    }
                }, 100);
                return;
            }

            if (missionEstimateBackgroundAnnotation != null) {
                map.removeAnnotation(missionEstimateBackgroundAnnotation);
            }

            if (missionEstimateForegroundAnnotation != null) {
                map.removeAnnotation(missionEstimateForegroundAnnotation);
            }

            if (missionReengagementEstimateBackgroundAnnotation != null) {
                map.removeAnnotation(missionReengagementEstimateBackgroundAnnotation);
            }

            if (missionReengagementEstimateForegroundAnnotation != null) {
                map.removeAnnotation(missionReengagementEstimateForegroundAnnotation);
            }

            final MissionExecutor missionExecutorLocal = missionExecutor;
            if (missionExecutorLocal == null) {
                return;
            }

            final MissionExecutor.Estimate estimate = missionExecutorLocal.getEstimate();
            if (estimate == null) {
                return;
            }

            final List<LatLng> visibleCoordinates = new LinkedList<>();

            final GeoSpatial[] estimateSpatials = estimate.spatials;
            if (estimateSpatials != null && estimateSpatials.length > 0) {
                final List<LatLng> pathPoints = new LinkedList<>();
                for (final GeoSpatial spatial : estimateSpatials) {
                    pathPoints.add(new LatLng(spatial.coordinate.latitude, spatial.coordinate.longitude));
                }

                missionEstimateBackgroundAnnotation = map.addPolyline(new PolylineOptions().addAll(pathPoints).width(6).color(Color.parseColor("#0277bd")));
                missionEstimateForegroundAnnotation = map.addPolyline(new PolylineOptions().addAll(pathPoints).width((float) 2.5).color(Color.parseColor("#26c6da")));

                if (!missionCentered) {
                    visibleCoordinates.addAll(pathPoints);
                }

                final GeoSpatial[] reengagementEstimateSpatials = estimate.reengagementSpatials;
                if (reengagementEstimateSpatials != null && reengagementEstimateSpatials.length > 0) {
                    final List<LatLng> reengagementPoints = new LinkedList<>();
                    for (final GeoSpatial spatial : reengagementEstimateSpatials) {
                        reengagementPoints.add(new LatLng(spatial.coordinate.latitude, spatial.coordinate.longitude));
                    }

                    missionReengagementEstimateBackgroundAnnotation = map.addPolyline(new PolylineOptions().addAll(reengagementPoints).width(6).color(Color.parseColor("#6a1b9a")));
                    missionReengagementEstimateForegroundAnnotation = map.addPolyline(new PolylineOptions().addAll(reengagementPoints).width((float) 2.5).color(Color.parseColor("#e040fb")));

                    if (!missionCentered) {
                        visibleCoordinates.addAll(reengagementPoints);
                    }
                }
            }

            if (visibleCoordinates.size() > 0) {
                missionCentered = true;
                final LatLngBounds.Builder bounds = new LatLngBounds.Builder();
                bounds.includes(visibleCoordinates);
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 10));
            }
        }
    };

    private Runnable updateFuncElements = new Runnable() {
        public void run() {
            final FuncExecutor funcExecutorLocal = funcExecutor;
            int inputIndex = 0;
            List<GeoSpatial> spatials = new ArrayList<>();
            GeoSpatial mapCenterSpatial = null;
            while (true) {
                final FuncInput input = funcExecutorLocal == null ? null : funcExecutorLocal.getInput(inputIndex);
                if (input == null) {
                    break;
                }

                if (input.variable.valueType == VariableValueType.DRONE) {
                    Object value = funcExecutorLocal.readValue(inputIndex);
                    if (value != null) {
                        if (value instanceof GeoSpatial[]) {
                            spatials.addAll(Arrays.asList((GeoSpatial[]) value));
                            if (spatials.size() > 0) {
                                mapCenterSpatial = spatials.get(spatials.size() - 1);
                            }
                        }
                        else if (value instanceof GeoSpatial) {
                            spatials.add((GeoSpatial)value);
                            mapCenterSpatial = (GeoSpatial)value;
                        }
                    }
                }
                else {
                    mapCenterSpatial = null;
                }
                inputIndex++;
            }

            while (funcDroneAnnotations.size() > spatials.size()) {
                map.removeMarker(funcDroneAnnotations.remove(funcDroneAnnotations.size() - 1));
            }

            while (funcDroneAnnotations.size() < spatials.size()) {
                funcDroneAnnotations.add(map.addMarker(new MarkerOptions()
                        .setIcon(IconFactory.getInstance(getActivity()).fromResource(R.drawable.func_input_drone))
                        .setPosition(new LatLng(spatials.get(funcDroneAnnotations.size()).coordinate.latitude, spatials.get(funcDroneAnnotations.size()).coordinate.longitude))));
            }

            int index = 0;
            for (final Marker funcDroneAnnotation : funcDroneAnnotations) {
                funcDroneAnnotation.setPosition(new LatLng(spatials.get(index).coordinate.latitude, spatials.get(index).coordinate.longitude));
                index++;
            }

            if (mapCenterSpatial != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mapCenterSpatial.coordinate.latitude, mapCenterSpatial.coordinate.longitude), 19.5));
            }

            for (final Annotation funcMapOverlayAnnotation : funcMapOverlayAnnotations) {
                map.removeAnnotation(funcMapOverlayAnnotation);
            }
            funcMapOverlayAnnotations.clear();

            if (funcExecutorLocal != null) {
                final FuncMapOverlay[] mapOverlays = funcExecutorLocal.getMapOverlays(session, new FuncExecutor.FuncExecuteError() {
                    @Override
                    public void error(final String value) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(getContext(), value, Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });

                if (mapOverlays != null && mapOverlays.length > 0) {
                    for (final FuncMapOverlay mapOverlay : mapOverlays) {
                        final List<LatLng> points = new LinkedList<>();
                        for (int c = 0; c < mapOverlay.coordinates.length; c++) {
                            points.add(new LatLng(mapOverlay.coordinates[c].latitude, mapOverlay.coordinates[c].longitude));
                        }

                        final int fillColor = DronelinkUI.parseHexColor(mapOverlay.color, Color.argb(255,255, 23, 68));
                        final Annotation funcMapOverlayAnnotation = map.addPolygon(new PolygonOptions().addAll(points)
                                .alpha(mapOverlay.color != null && mapOverlay.color.length() == 9 ? ((float)Color.alpha(fillColor)) / 255f : (float)0.5)
                                .fillColor(fillColor)
                                .strokeColor(DronelinkUI.parseHexColor(mapOverlay.color, Color.argb((int)(255 * 0.7), 255, 23, 68))));
                        funcMapOverlayAnnotations.add(funcMapOverlayAnnotation);
                    }
                }
            }
        }
    };

    private Runnable updateModeElements = new Runnable() {
        public void run() {
            final ModeExecutor modeExecutorLocal = modeExecutor;
            if (modeExecutorLocal == null || !modeExecutorLocal.isEngaged()) {
                return;
            }

            final Style style = map.getStyle();
            if (style == null) {
                return;
            }

            final GeoSpatial modeTarget = modeExecutorLocal.getTarget();
            if (modeTarget != null) {
                final GeoJsonSource modeTargetSource = style.getSourceAs("mode-target");
                modeTargetSource.setGeoJson(Feature.fromGeometry(Point.fromLngLat(modeTarget.coordinate.longitude, modeTarget.coordinate.latitude)));
                style.getLayerAs("mode-target").setProperties(PropertyFactory.iconRotate((float)(Convert.RadiansToDegrees(modeTarget.orientation.getYaw()) - map.getCameraPosition().bearing)));
            }

            final List<LatLng> visibleCoordinates = new LinkedList<>();
            final GeoCoordinate[] modeVisibleCoordinates = modeExecutorLocal.getVisibleCoordinates();
            if (modeVisibleCoordinates != null) {
                for (final GeoCoordinate coordinate : modeVisibleCoordinates) {
                    visibleCoordinates.add(new LatLng(coordinate.latitude, coordinate.longitude));
                }
            }

            if (visibleCoordinates.size() > 0) {
                final LatLngBounds.Builder bounds = new LatLngBounds.Builder();
                bounds.includes(visibleCoordinates);
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), (int)(Math.max(mapView.getHeight(), mapView.getWidth()) * 0.2)));
            }
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
    public void onRegistered(final String error) {}

    @Override
    public void onInitialized(final DroneSession session) {}

    @Override
    public void onLocated(final DroneSession session) {
        final DroneStateAdapter state = getDroneState();
        if (state == null) {
            return;
        }

        final Location droneLocation = state.getLocation();
        if (droneLocation != null && missionExecutor == null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(droneLocation.getLatitude(), droneLocation.getLongitude()), 18.5));
                }
            });
        }
    }

    @Override
    public void onMotorsChanged(final DroneSession session, boolean value) {}

    @Override
    public void onCommandExecuted(final DroneSession session, final Command command) {}

    @Override
    public void onCommandFinished(final DroneSession session, final Command command, final CommandError error) {}

    @Override
    public void onCameraFileGenerated(final DroneSession session, final CameraFile file) {}

    @Override
    public void onMissionLoaded(final MissionExecutor executor) {
        missionExecutor = executor;
        missionCentered = false;
        executor.addListener(this);
        getActivity().runOnUiThread(updateMissionRequiredTakeoffArea);
        getActivity().runOnUiThread(updateMissionRestrictionZones);
        if (executor.isEstimated()) {
            getActivity().runOnUiThread(updateMissionEstimate);
        }
    }

    @Override
    public void onMissionUnloaded(final MissionExecutor executor) {
        missionExecutor = null;
        missionCentered = false;
        executor.removeListener(this);
        getActivity().runOnUiThread(updateMissionRequiredTakeoffArea);
        getActivity().runOnUiThread(updateMissionRestrictionZones);
        getActivity().runOnUiThread(updateMissionEstimate);
    }

    @Override
    public void onFuncLoaded(final FuncExecutor executor) {
        funcExecutor = executor;
        executor.addListener(this);
        getActivity().runOnUiThread(updateFuncElements);
    }

    @Override
    public void onFuncUnloaded(final FuncExecutor executor) {
        funcExecutor = null;
        executor.removeListener(this);
        getActivity().runOnUiThread(updateFuncElements);
    }

    @Override
    public void onModeLoaded(final ModeExecutor executor) {
        modeExecutor = executor;
        executor.addListener(this);
    }

    @Override
    public void onModeUnloaded(final ModeExecutor executor) {
        modeExecutor = null;
        executor.removeListener(this);
    }

    @Override
    public void onMissionEstimating(final MissionExecutor executor) {}

    @Override
    public void onMissionEstimated(final MissionExecutor executor, final MissionExecutor.Estimate estimate) {
        getActivity().runOnUiThread(updateMissionEstimate);
    }

    @Override
    public void onMissionEngaging(final MissionExecutor executor) {}

    @Override
    public void onMissionEngaged(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {}

    @Override
    public void onMissionExecuted(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {}

    @Override
    public void onMissionDisengaged(final MissionExecutor executor, final MissionExecutor.Engagement engagement, final Message reason) {}

    @Override
    public void onFuncInputsChanged(final FuncExecutor executor) {
        getActivity().runOnUiThread(updateFuncElements);
    }

    @Override
    public void onFuncExecuted(final FuncExecutor executor) {
    }

    @Override
    public void onModeEngaging(final ModeExecutor executor) {}

    @Override
    public void onModeEngaged(final ModeExecutor executor, final ModeExecutor.Engagement engagement) {
        getActivity().runOnUiThread(updateModeElements);
    }

    @Override
    public void onModeExecuted(final ModeExecutor executor, final ModeExecutor.Engagement engagement) {
        getActivity().runOnUiThread(updateModeElements);
    }

    @Override
    public void onModeDisengaged(final ModeExecutor executor, final ModeExecutor.Engagement engagement, final Message reason) {}

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        map = mapboxMap;
        final MapboxMapFragment self = this;
        mapboxMap.setStyle(Style.SATELLITE_STREETS, new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                enableLocationComponent(style);

                style.addImage("drone-home", BitmapFactory.decodeResource(MapboxMapFragment.this.getResources(), R.drawable.home));
                final GeoJsonSource droneHomeSource = new GeoJsonSource("drone-home", Feature.fromGeometry(Point.fromLngLat(0, 0)));
                style.addSource(droneHomeSource);

                final SymbolLayer droneHomeLayer;
                droneHomeLayer = new SymbolLayer("drone-home", "drone-home");
                droneHomeLayer.withProperties(PropertyFactory.iconImage("drone-home"), PropertyFactory.iconAllowOverlap(true));
                style.addLayer(droneHomeLayer);

                style.addImage("drone", BitmapFactory.decodeResource(MapboxMapFragment.this.getResources(), R.drawable.drone));
                final GeoJsonSource droneSource = new GeoJsonSource("drone", Feature.fromGeometry(Point.fromLngLat(0, 0)));
                style.addSource(droneSource);

                final SymbolLayer droneLayer;
                droneLayer = new SymbolLayer("drone", "drone");
                droneLayer.withProperties(PropertyFactory.iconImage("drone"), PropertyFactory.iconAllowOverlap(true));
                style.addLayer(droneLayer);

                style.addImage("mode-target", BitmapFactory.decodeResource(MapboxMapFragment.this.getResources(), R.drawable.drone));
                final GeoJsonSource modeTargetSource = new GeoJsonSource("mode-target", Feature.fromGeometry(Point.fromLngLat(0, 0)));
                style.addSource(modeTargetSource);

                final SymbolLayer modeTargetLayer;
                modeTargetLayer = new SymbolLayer("mode-target", "mode-target");
                modeTargetLayer.withProperties(PropertyFactory.iconImage("mode-target"), PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconOpacity((float) 0.5));
                style.addLayer(modeTargetLayer);

                updateTimer = new Timer();
                updateTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        updateTimer();
                    }
                }, 0, updateMillis);

                Dronelink.getInstance().getSessionManager().addListener(self);
                Dronelink.getInstance().addListener(self);
            }
        });
    }

    @SuppressWarnings({"MissingPermission"})
    private void enableLocationComponent(final Style style) {
        if (PermissionsManager.areLocationPermissionsGranted(getContext())) {
            final LocationComponent locationComponent = map.getLocationComponent();
            locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(getContext(), style).build());
            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setRenderMode(RenderMode.COMPASS);
            locationComponent.getLocationEngine().getLastLocation(new LocationEngineCallback<LocationEngineResult>() {
                @Override
                public void onSuccess(LocationEngineResult result) {
                    if (result.getLastLocation() != null && !missionCentered && session == null) {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(result.getLastLocation().getLatitude(), result.getLastLocation().getLongitude()), 17));
                    }
                }

                @Override
                public void onFailure(@NonNull Exception exception) {

                }
            });
        }
    }
}
