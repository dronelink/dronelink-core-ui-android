//  MapboxMapController.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 6/15/22.
//  Copyright © 2022 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.os.Handler;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;

import com.dronelink.core.CameraFile;
import com.dronelink.core.Convert;
import com.dronelink.core.DroneOffsets;
import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.FuncExecutor;
import com.dronelink.core.MissionExecutor;
import com.dronelink.core.ModeExecutor;
import com.dronelink.core.adapters.DroneStateAdapter;
import com.dronelink.core.command.CommandError;
import com.dronelink.core.kernel.command.Command;
import com.dronelink.core.kernel.core.CameraFocusCalibration;
import com.dronelink.core.kernel.core.FuncInput;
import com.dronelink.core.kernel.core.FuncMapOverlay;
import com.dronelink.core.kernel.core.GeoCoordinate;
import com.dronelink.core.kernel.core.GeoSpatial;
import com.dronelink.core.kernel.core.Message;
import com.dronelink.core.kernel.core.PlanRestrictionZone;
import com.dronelink.core.kernel.core.UserInterfaceSettings;
import com.dronelink.core.kernel.core.enums.VariableValueType;
import com.mapbox.android.core.location.LocationEngine;
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
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
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

public class MapboxMapController implements Dronelink.Listener, DroneSessionManager.Listener, DroneSession.Listener, MissionExecutor.Listener, FuncExecutor.Listener, ModeExecutor.Listener {
    private enum Tracking {
        NONE,
        DRONE_NORTH_UP,
        DRONE_HEADING
    }

    private DroneSession session;
    private MissionExecutor missionExecutor;
    private FuncExecutor funcExecutor;
    private ModeExecutor modeExecutor;
    private com.mapbox.mapboxsdk.maps.MapView mapView;
    private MapboxMap map;
    private LocationComponent locationComponent;
    private Marker missionReferenceMarker;
    private Annotation missionRequiredTakeoffAreaAnnotation;
    private List<Annotation> missionRestrictionZoneAnnotations = new ArrayList<>();
    private Annotation missionEstimateBackgroundAnnotation;
    private Annotation missionEstimateForegroundAnnotation;
    private Annotation missionReengagementEstimateBackgroundAnnotation;
    private Annotation missionReengagementEstimateForegroundAnnotation;
    private Marker missionReengagementDroneAnnotation;
    private List<Marker> funcDroneAnnotations = new ArrayList<>();
    private List<Annotation> funcMapOverlayAnnotations = new ArrayList<>();
    private Timer updateTimer;
    private final long updateMillis = 100;
    private boolean missionCentered = false;
    private String currentMissionEstimateID = null;
    private Tracking tracking = Tracking.NONE;
    private boolean disposed = false;

    private DroneStateAdapter getDroneState() {
        final DroneSession session = this.session;
        if (session == null || session.getState() == null) {
            return null;
        }
        return session.getState().value;
    }

    public MapboxMapController(final MapboxMap map, final MapView mapView) {
        this.map = map;
        this.mapView = mapView;

        final DroneSessionManager manager = Dronelink.getInstance().getTargetDroneSessionManager();
        if (manager != null) {
            manager.addListener(this);
        }
        Dronelink.getInstance().addListener(this);

        updateTimer = new Timer();
        updateTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateTimer();
            }
        }, 0, updateMillis);

        map.setStyle(Style.SATELLITE_STREETS, new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                //TODO causes a crash: IncompatibleClassChangeError: Found interface com.google.android.gms.location.FusedLocationProviderClient
                // enableLocationComponent(style);

                map.getUiSettings().setTiltGesturesEnabled(false);
                map.getUiSettings().setCompassGravity(Gravity.BOTTOM | Gravity.RIGHT);

                style.addImage("location-puck", BitmapFactory.decodeResource(mapView.getResources(), R.drawable.location_puck));
                final GeoJsonSource locationPuckSource = new GeoJsonSource("location-puck", Feature.fromGeometry(Point.fromLngLat(0, 0)));
                style.addSource(locationPuckSource);

                final SymbolLayer locationPuckLayer;
                locationPuckLayer = new SymbolLayer("location-puck", "location-puck");
                locationPuckLayer.withProperties(PropertyFactory.iconImage("location-puck"), PropertyFactory.iconAllowOverlap(true));
                style.addLayer(locationPuckLayer);

                style.addImage("drone-home", BitmapFactory.decodeResource(mapView.getResources(), R.drawable.home));
                final GeoJsonSource droneHomeSource = new GeoJsonSource("drone-home", Feature.fromGeometry(Point.fromLngLat(0, 0)));
                style.addSource(droneHomeSource);

                final SymbolLayer droneHomeLayer;
                droneHomeLayer = new SymbolLayer("drone-home", "drone-home");
                droneHomeLayer.withProperties(PropertyFactory.iconImage("drone-home"), PropertyFactory.iconAllowOverlap(true));
                style.addLayer(droneHomeLayer);

                style.addImage("drone", BitmapFactory.decodeResource(mapView.getResources(), R.drawable.drone));
                final GeoJsonSource droneSource = new GeoJsonSource("drone", Feature.fromGeometry(Point.fromLngLat(0, 0)));
                style.addSource(droneSource);

                final SymbolLayer droneLayer;
                droneLayer = new SymbolLayer("drone", "drone");
                droneLayer.withProperties(PropertyFactory.iconImage("drone"), PropertyFactory.iconAllowOverlap(true));
                style.addLayer(droneLayer);

                style.addImage("mode-target", BitmapFactory.decodeResource(mapView.getResources(), R.drawable.drone));
                final GeoJsonSource modeTargetSource = new GeoJsonSource("mode-target", Feature.fromGeometry(Point.fromLngLat(0, 0)));
                style.addSource(modeTargetSource);

                final SymbolLayer modeTargetLayer;
                modeTargetLayer = new SymbolLayer("mode-target", "mode-target");
                modeTargetLayer.withProperties(PropertyFactory.iconImage("mode-target"), PropertyFactory.iconAllowOverlap(true), PropertyFactory.iconOpacity((float) 0.5));
                style.addLayer(modeTargetLayer);
            }
        });
    }

    @SuppressWarnings({"MissingPermission"})
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;

        if (updateTimer != null) {
            updateTimer.cancel();
        }
//TODO re-enable if we turn the location component back on
//        if (locationComponent != null) {
//            locationComponent.onStop();
//            locationComponent.setLocationComponentEnabled(false);
//        }

        final DroneSessionManager manager = Dronelink.getInstance().getTargetDroneSessionManager();
        if (manager != null) {
            manager.removeListener(this);
        }
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

        map = null;
        mapView.onStop();
        mapView = null;
    }

    public interface MoreMenuItem {
        String getTitle();
        void onClick();
    }

    public void onMore(final Context context, final View anchor, final MapboxMapController.MoreMenuItem[] actions) {
        final PopupMenu actionSheet = new PopupMenu(new ContextThemeWrapper(context, R.style.PopupMenuOverlapAnchor), anchor);

        actionSheet.getMenu().add(R.string.MapboxMap_reset);
        actionSheet.getMenu().add(R.string.MapboxMap_drone_heading);
        actionSheet.getMenu().add(R.string.MapboxMap_drone_north_up);

        if (actions != null) {
            for (final MapboxMapController.MoreMenuItem action : actions) {
                actionSheet.getMenu().add(action.getTitle());
            }
        }

        actionSheet.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                if (item.getTitle() == context.getString(R.string.MapboxMap_reset)) {
                    tracking = Tracking.NONE;
                    return true;
                }

                if (item.getTitle() == context.getString(R.string.MapboxMap_drone_heading)) {
                    tracking = Tracking.DRONE_HEADING;
                    return true;
                }

                if (item.getTitle() == context.getString(R.string.MapboxMap_drone_north_up)) {
                    tracking = Tracking.DRONE_NORTH_UP;
                    return true;
                }

                if (actions != null) {
                    for (final MapboxMapController.MoreMenuItem action : actions) {
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

    private void runOnUiThread(final Runnable runnable) {
        if (disposed) {
            return;
        }

        mapView.post(runnable);
    }

    private void updateTimer() {
        runOnUiThread(update);
    }

    private Runnable update = new Runnable() {
        public void run() {
            if (map == null) {
                return;
            }

            final Style style = map.getStyle();
            final DroneStateAdapter state = getDroneState();
            if (style == null || state == null) {
                return;
            }

            final Location pilotLocation = Dronelink.getInstance().getLocation();
            if (pilotLocation != null) {
                final GeoJsonSource locationPuckSource = style.getSourceAs("location-puck");
                locationPuckSource.setGeoJson(Feature.fromGeometry(Point.fromLngLat(pilotLocation.getLongitude(), pilotLocation.getLatitude())));
            }

            final Location droneHomeLocation = state.getHomeLocation();
            if (droneHomeLocation != null) {
                final GeoJsonSource droneHomeSource = style.getSourceAs("drone-home");
                droneHomeSource.setGeoJson(Feature.fromGeometry(Point.fromLngLat(droneHomeLocation.getLongitude(), droneHomeLocation.getLatitude())));
            }

            Location droneLocation = state.getLocation();
            if (droneLocation != null) {
                final GeoJsonSource droneSource = style.getSourceAs("drone");
                final DroneOffsets offsets = Dronelink.getInstance().droneOffsets;
                droneLocation = Convert.locationWithBearing(droneLocation, offsets.droneCoordinate.direction + Math.PI, offsets.droneCoordinate.magnitude);
                droneSource.setGeoJson(Feature.fromGeometry(Point.fromLngLat(droneLocation.getLongitude(), droneLocation.getLatitude())));
                style.getLayerAs("drone").setProperties(PropertyFactory.iconRotate((float)(Convert.RadiansToDegrees(state.getOrientation().getYaw()) - map.getCameraPosition().bearing)));
            }

            if (tracking != Tracking.NONE) {
                final Location location = state.getLocation();
                if (location != null) {
                    final double distance = Math.max(20, state.getAltitude() / 2.0);

                    final List<LatLng> visibleCoordinates = new ArrayList<>();
                    visibleCoordinates.add(getLatLng(Convert.locationWithBearing(location, 0, distance)));
                    visibleCoordinates.add(getLatLng(Convert.locationWithBearing(location, Math.PI / 2, distance)));
                    visibleCoordinates.add(getLatLng(Convert.locationWithBearing(location, Math.PI, distance)));
                    visibleCoordinates.add(getLatLng(Convert.locationWithBearing(location, 3 * Math.PI / 2, distance)));
                    setVisibleCoordinates(visibleCoordinates, tracking == Tracking.DRONE_NORTH_UP ? 0 : Convert.RadiansToDegrees(state.getOrientation().getYaw()));
                }
            }
        }
    };

    private void setVisibleCoordinates(final List<LatLng> visibleCoordinates) {
        setVisibleCoordinates(visibleCoordinates, null);
    }

    private void setVisibleCoordinates(final List<LatLng> visibleCoordinates, final Double direction) {
        if (visibleCoordinates.size() == 0) {
            return;
        }

        final LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        bounds.includes(visibleCoordinates);
        if (direction == null) {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), (int) (Math.max(mapView.getHeight(), mapView.getWidth()) * 0.05)));
        }
        else {
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), direction < 0 ? direction + 360 : direction, 0, (int) (Math.max(mapView.getHeight(), mapView.getWidth()) * 0.05)));
        }
    }

    private Runnable addMissionReferenceMarker = new Runnable() {
        public void run() {
            if (map == null) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        runOnUiThread(addMissionReferenceMarker);
                    }
                }, 100);
                return;
            }

            final MissionExecutor missionExecutorLocal = missionExecutor;
            if (missionExecutorLocal != null) {
                missionReferenceMarker = map.addMarker(new MarkerOptions()
                        .setIcon(IconFactory.getInstance(mapView.getContext()).fromResource(R.drawable.mission_reference))
                        .setPosition(getLatLng(missionExecutorLocal.referenceCoordinate.getLocation())));
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
                        runOnUiThread(updateMissionRequiredTakeoffArea);
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
                        takeoffAreaPoints.add(getLatLng(location));
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
                        runOnUiThread(updateMissionRestrictionZones);
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
                if (missionExecutorLocal.restrictionZones != null && missionExecutor.restrictionZoneBoundaryCoordinates != null) {
                    for (int i = 0; i < missionExecutorLocal.restrictionZones.length; i++) {
                        final PlanRestrictionZone restrictionZone = missionExecutorLocal.restrictionZones[i];
                        final GeoCoordinate[] coordinates = i < missionExecutorLocal.restrictionZoneBoundaryCoordinates.length ? missionExecutorLocal.restrictionZoneBoundaryCoordinates[i] : null;
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
                                    points.add(getLatLng(location));
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
                        runOnUiThread(updateMissionEstimate);
                    }
                }, 100);
                return;
            }

            final MissionExecutor missionExecutorLocal = missionExecutor;
            final MissionExecutor.Estimate estimate = missionExecutorLocal != null ? missionExecutor.getEstimate() : null;
            final String id = estimate != null ? estimate.id : null;
            if (currentMissionEstimateID == id) {
                return;
            }
            currentMissionEstimateID = id;

            if (missionEstimateBackgroundAnnotation != null) {
                map.removeAnnotation(missionEstimateBackgroundAnnotation);
            }

            if (missionEstimateForegroundAnnotation != null) {
                map.removeAnnotation(missionEstimateForegroundAnnotation);
            }

            if (missionExecutorLocal == null || estimate == null) {
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
            }

            final List<LatLng> reengagementPoints = missionReengagementEstimate();
            if (reengagementPoints != null) {
                if (!missionCentered) {
                    visibleCoordinates.addAll(reengagementPoints);
                }
            }

            if (visibleCoordinates.size() > 1) {
                missionCentered = true;
                setVisibleCoordinates(visibleCoordinates);
            }
        }
    };

    @SuppressWarnings({"MissingPermission"})
    private Runnable updateMissionReengagementEstimate = new Runnable() {
        public void run() {
            missionReengagementEstimate();
        }
    };

    private List<LatLng> missionReengagementEstimate() {
        if (missionReengagementEstimateBackgroundAnnotation != null) {
            map.removeAnnotation(missionReengagementEstimateBackgroundAnnotation);
        }
        missionReengagementEstimateBackgroundAnnotation = null;

        if (missionReengagementEstimateForegroundAnnotation != null) {
            map.removeAnnotation(missionReengagementEstimateForegroundAnnotation);
        }
        missionReengagementEstimateForegroundAnnotation = null;

        if (missionReengagementDroneAnnotation != null) {
            map.removeAnnotation(missionReengagementDroneAnnotation);
        }
        missionReengagementDroneAnnotation = null;

        final MissionExecutor missionExecutor = this.missionExecutor;
        if (missionExecutor == null) {
            return null;
        }

        final boolean engaged = missionExecutor.isEngaged();
        final boolean reengaging = missionExecutor.isReengaging();
        GeoSpatial[] reengagementEstimateSpatials = reengaging ? missionExecutor.getReengagementSpatials() : null;
        if (reengagementEstimateSpatials == null) {
            final MissionExecutor.Estimate estimate = missionExecutor.getEstimate();
            if (estimate != null) {
                reengagementEstimateSpatials = estimate.reengagementSpatials;
            }
        }

        List<LatLng> reengagementPoints = null;
        if (reengagementEstimateSpatials != null && reengagementEstimateSpatials.length > 0) {
            reengagementPoints = new LinkedList<>();
            for (final GeoSpatial spatial : reengagementEstimateSpatials) {
                reengagementPoints.add(new LatLng(spatial.coordinate.latitude, spatial.coordinate.longitude));
            }
        }

        if (reengaging && reengagementPoints != null) {
            missionReengagementEstimateBackgroundAnnotation = map.addPolyline(new PolylineOptions().addAll(reengagementPoints).width(6).color(Color.parseColor("#6a1b9a")));
            missionReengagementEstimateForegroundAnnotation = map.addPolyline(new PolylineOptions().addAll(reengagementPoints).width((float) 2.5).color(Color.parseColor("#e040fb")));
        }

        LatLng reengagementCoordinate = null;
        if (reengaging) {
            if (reengagementPoints != null && reengagementPoints.size() > 0) {
                reengagementCoordinate = reengagementPoints.get(reengagementPoints.size() - 1);
            }
        }
        else {
            final GeoSpatial reengagementSpatial = missionExecutor.getReengagementSpatial();
            if (reengagementSpatial != null) {
                reengagementCoordinate = new LatLng(reengagementSpatial.coordinate.latitude, reengagementSpatial.coordinate.longitude);
            }
        }

        if (reengagementCoordinate != null && (!engaged || reengaging)) {
            missionReengagementDroneAnnotation = map.addMarker(new MarkerOptions()
                    .setIcon(IconFactory.getInstance(mapView.getContext()).fromResource(R.drawable.drone_reengagement))
                    .setPosition(reengagementCoordinate));
        }

        return reengagementPoints;
    }

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
                        .setIcon(IconFactory.getInstance(mapView.getContext()).fromResource(R.drawable.func_input_drone))
                        .setPosition(new LatLng(spatials.get(funcDroneAnnotations.size()).coordinate.latitude, spatials.get(funcDroneAnnotations.size()).coordinate.longitude))));
            }

            int index = 0;
            for (final Marker funcDroneAnnotation : funcDroneAnnotations) {
                funcDroneAnnotation.setPosition(new LatLng(spatials.get(index).coordinate.latitude, spatials.get(index).coordinate.longitude));
                index++;
            }

            if (mapCenterSpatial != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mapCenterSpatial.coordinate.latitude, mapCenterSpatial.coordinate.longitude), 19.25));
            }

            for (final Annotation funcMapOverlayAnnotation : funcMapOverlayAnnotations) {
                map.removeAnnotation(funcMapOverlayAnnotation);
            }
            funcMapOverlayAnnotations.clear();

            if (funcExecutorLocal != null) {
                final FuncMapOverlay[] mapOverlays = funcExecutorLocal.getMapOverlays(session, new FuncExecutor.FuncExecuteError() {
                    @Override
                    public void error(final String value) {
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
            if (map == null) {
                return;
            }

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

            if (tracking == Tracking.NONE) {
                final List<LatLng> visibleCoordinates = new LinkedList<>();
                final GeoCoordinate[] modeVisibleCoordinates = modeExecutorLocal.getVisibleCoordinates();
                if (modeVisibleCoordinates != null) {
                    for (final GeoCoordinate coordinate : modeVisibleCoordinates) {
                        visibleCoordinates.add(new LatLng(coordinate.latitude, coordinate.longitude));
                    }
                }

                if (visibleCoordinates.size() > 0) {
                    setVisibleCoordinates(visibleCoordinates);
                }
            }
        }
    };

    public void applyUserInterfaceSettings(final UserInterfaceSettings userInterfaceSettings) {
        if (userInterfaceSettings == null) {
            return;
        }

        switch (userInterfaceSettings.mapTracking) {
            case NO_CHANGE:
                break;

            case NONE:
                tracking = Tracking.NONE;
                break;

            case DRONE_NORTH_UP:
                tracking = Tracking.DRONE_NORTH_UP;
                break;

            case DRONE_HEADING:
                tracking = Tracking.DRONE_HEADING;
                break;
        }
    }

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
    public void onDroneSessionManagerAdded(final DroneSessionManager droneSessionManager) {}

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
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (map != null) {
                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(droneLocation.getLatitude(), droneLocation.getLongitude()), 18.5));
                    }
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
    public void onVideoFeedSourceUpdated(final DroneSession session, final Integer channel) {}

    @Override
    public void onMissionLoaded(final MissionExecutor executor) {
        currentMissionEstimateID = null;
        missionExecutor = executor;
        missionCentered = false;
        executor.addListener(this);
        applyUserInterfaceSettings(executor.userInterfaceSettings);
        if (executor.userInterfaceSettings != null && executor.userInterfaceSettings.droneOffsetsVisible != null && executor.userInterfaceSettings.droneOffsetsVisible) {
            runOnUiThread(addMissionReferenceMarker);
        }
        runOnUiThread(updateMissionRequiredTakeoffArea);
        runOnUiThread(updateMissionRestrictionZones);
        if (executor.isEstimated()) {
            runOnUiThread(updateMissionEstimate);
        }
    }

    @Override
    public void onMissionUnloaded(final MissionExecutor executor) {
        missionExecutor = null;
        missionCentered = false;
        executor.removeListener(this);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (missionReferenceMarker != null) {
                    map.removeMarker(missionReferenceMarker);
                }
                missionReferenceMarker = null;
            }
        });
        runOnUiThread(updateMissionRequiredTakeoffArea);
        runOnUiThread(updateMissionRestrictionZones);
        runOnUiThread(updateMissionEstimate);
    }

    @Override
    public void onFuncLoaded(final FuncExecutor executor) {
        funcExecutor = executor;
        executor.addListener(this);
        applyUserInterfaceSettings(executor.getUserInterfaceSettings());
        runOnUiThread(updateFuncElements);
    }

    @Override
    public void onFuncUnloaded(final FuncExecutor executor) {
        funcExecutor = null;
        executor.removeListener(this);
        runOnUiThread(updateFuncElements);
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
    public void onCameraFocusCalibrationRequested(final CameraFocusCalibration value) {}

    @Override
    public void onCameraFocusCalibrationUpdated(final CameraFocusCalibration value) {}

    @Override
    public void onMissionEstimating(final MissionExecutor executor) {}

    @Override
    public void onMissionEstimated(final MissionExecutor executor, final MissionExecutor.Estimate estimate) {
        runOnUiThread(updateMissionEstimate);
    }

    @Override
    public Message[] missionEngageDisallowedReasons(final MissionExecutor executor) {
        return null;
    }

    @Override
    public void onMissionEngaging(final MissionExecutor executor) {}

    @Override
    public void onMissionEngaged(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {}

    @Override
    public void onMissionExecuted(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {
        if ((missionReengagementEstimateBackgroundAnnotation == null && executor.isReengaging()) || (missionReengagementEstimateBackgroundAnnotation != null && !executor.isReengaging())) {
            runOnUiThread(updateMissionReengagementEstimate);
        }
    }

    @Override
    public void onMissionDisengaged(final MissionExecutor executor, final MissionExecutor.Engagement engagement, final Message reason) {
        runOnUiThread(updateMissionReengagementEstimate);
    }

    @Override
    public void onMissionUpdatedDisconnected(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {
        runOnUiThread(updateMissionReengagementEstimate);
    }

    @Override
    public void onFuncInputsChanged(final FuncExecutor executor) {
        runOnUiThread(updateFuncElements);
    }

    @Override
    public void onFuncExecuted(final FuncExecutor executor) {
    }

    @Override
    public Message[] modeEngageDisallowedReasons(final ModeExecutor executor) {
        return null;
    }

    @Override
    public void onModeEngaging(final ModeExecutor executor) {}

    @Override
    public void onModeEngaged(final ModeExecutor executor, final ModeExecutor.Engagement engagement) {
        runOnUiThread(updateModeElements);
    }

    @Override
    public void onModeExecuted(final ModeExecutor executor, final ModeExecutor.Engagement engagement) {
        runOnUiThread(updateModeElements);
    }

    @Override
    public void onModeDisengaged(final ModeExecutor executor, final ModeExecutor.Engagement engagement, final Message reason) {}

    @SuppressWarnings({"MissingPermission"})
    private void enableLocationComponent(final Style style) {
        if (PermissionsManager.areLocationPermissionsGranted(mapView.getContext())) {
            locationComponent = map.getLocationComponent();
            locationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(mapView.getContext(), style).build());
            locationComponent.setLocationComponentEnabled(true);
            locationComponent.setMaxAnimationFps(30);
            locationComponent.setRenderMode(RenderMode.COMPASS);
            final LocationEngine locationEngine = locationComponent.getLocationEngine();
            if (locationEngine != null) {
                locationEngine.getLastLocation(new LocationEngineCallback<LocationEngineResult>() {
                    @Override
                    public void onSuccess(LocationEngineResult result) {
                        if (map != null && result.getLastLocation() != null && !missionCentered && session == null) {
                            map.moveCamera(CameraUpdateFactory.newLatLngZoom(getLatLng(result.getLastLocation()), 17));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Exception exception) {

                    }
                });
            }
        }
    }

    private LatLng getLatLng(final Location location) {
        return new LatLng(location.getLatitude(), location.getLongitude());
    }
}
