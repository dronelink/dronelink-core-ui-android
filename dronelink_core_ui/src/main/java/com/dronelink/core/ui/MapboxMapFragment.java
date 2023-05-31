//  MapboxMapFragment.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 11/8/19.
//  Copyright © 2019 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import static com.mapbox.maps.plugin.annotation.generated.PointAnnotationManagerKt.createPointAnnotationManager;
import static com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManagerKt.createPolygonAnnotationManager;
import static com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManagerKt.createPolylineAnnotationManager;

import android.app.Activity;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

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
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Point;
import com.mapbox.maps.CameraOptions;
import com.mapbox.maps.EdgeInsets;
import com.mapbox.maps.GeoJSONSourceData;
import com.mapbox.maps.MapView;
import com.mapbox.maps.MapboxMap;
import com.mapbox.maps.Style;
import com.mapbox.maps.extension.style.layers.generated.SymbolLayer;
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource;
import com.mapbox.maps.plugin.Plugin;
import com.mapbox.maps.plugin.PuckBearingSource;
import com.mapbox.maps.plugin.animation.CameraAnimationsUtils;
import com.mapbox.maps.plugin.animation.MapAnimationOptions;
import com.mapbox.maps.plugin.annotation.AnnotationConfig;
import com.mapbox.maps.plugin.annotation.AnnotationPlugin;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotation;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions;
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotation;
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PolygonAnnotationOptions;
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation;
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager;
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions;
import com.mapbox.maps.plugin.compass.CompassViewPlugin;
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin;
import com.mapbox.maps.plugin.locationcomponent.LocationComponentPlugin2;
import com.mapbox.maps.plugin.locationcomponent.LocationComponentUtils;
import com.mapbox.maps.plugin.scalebar.ScaleBarUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MapboxMapFragment extends Fragment implements Dronelink.Listener, DroneSessionManager.Listener, DroneSession.Listener, MissionExecutor.Listener, FuncExecutor.Listener, ModeExecutor.Listener {
    private static final int CAMERA_ANIMATION_DURATION = 500;

    private enum Tracking {
        NONE,
        DRONE_NORTH_UP,
        DRONE_HEADING
    }

    private DroneSession session;
    private MissionExecutor missionExecutor;
    private FuncExecutor funcExecutor;
    private ModeExecutor modeExecutor;
    private MapView mapView;
    private MapboxMap map;
    private LocationComponentPlugin locationComponent;
    private PolygonAnnotationManager polygonAnnotationManager;
    private PointAnnotationManager pointAnnotationManager;
    private PolylineAnnotationManager polylineAnnotationManager;
    private PointAnnotation missionReferenceAnnotation;
    private PointAnnotation missionReengagementDroneAnnotation;
    private PolygonAnnotation missionRequiredTakeoffAreaAnnotation;
    private PolylineAnnotation missionEstimateBackgroundAnnotation;
    private PolylineAnnotation missionEstimateForegroundAnnotation;
    private PolylineAnnotation missionReengagementEstimateBackgroundAnnotation;
    private PolylineAnnotation missionReengagementEstimateForegroundAnnotation;
    private final List<PolygonAnnotation> missionRestrictionZoneAnnotations = new ArrayList<>();
    private final List<PolygonAnnotation> funcMapOverlayAnnotations = new ArrayList<>();
    private final List<PointAnnotation> funcDroneAnnotations = new ArrayList<>();
    private SymbolLayer droneLayer;
    private SymbolLayer modeTargetLayer;
    private Timer updateTimer;
    private final long updateMillis = 100;
    private boolean missionCentered = false;
    private boolean cameraOptionsInitialized = false;
    private String currentMissionEstimateID = null;
    private Tracking tracking = Tracking.NONE;

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
        //Set map invisible here to avoid flicker of wrong style/location when map is loaded.
        //It is set back to visible once style is loaded and correct location is set.
        mapView.setVisibility(View.INVISIBLE);
        this.map = mapView.getMapboxMap();

        final AnnotationPlugin annotationPlugin = mapView.getPlugin(Plugin.MAPBOX_ANNOTATION_PLUGIN_ID);
        if (annotationPlugin == null) {
            return;
        }

        polygonAnnotationManager = createPolygonAnnotationManager(annotationPlugin, new AnnotationConfig("drone-home", "polygon-annotation-layer"));
        polylineAnnotationManager = createPolylineAnnotationManager(annotationPlugin, new AnnotationConfig("drone-home", "polyline-annotation-layer"));
        pointAnnotationManager = createPointAnnotationManager(annotationPlugin, new AnnotationConfig("drone-home", "point-annotation-layer"));

        map.loadStyleUri(Style.SATELLITE_STREETS, style -> {
            enableLocationComponent();
            ScaleBarUtils.getScaleBar(mapView).setEnabled(false);
            final CompassViewPlugin compassViewPlugin = mapView.getPlugin(Plugin.MAPBOX_COMPASS_PLUGIN_ID);
            if (compassViewPlugin != null) {
                compassViewPlugin.setPosition(Gravity.BOTTOM | Gravity.RIGHT);
            }

            style.addImage("drone-home", BitmapFactory.decodeResource(mapView.getResources(), R.drawable.home));
            final GeoJsonSource droneHomeSource = new GeoJsonSource.Builder("drone-home").build();
            droneHomeSource.bindTo(style);

            final SymbolLayer droneHomeLayer = new SymbolLayer("drone-home", "drone-home");
            droneHomeLayer.iconImage("drone-home");
            droneHomeLayer.iconAllowOverlap(true);
            droneHomeLayer.bindTo(style);

            style.addImage("drone", BitmapFactory.decodeResource(mapView.getResources(), R.drawable.drone));
            final GeoJsonSource droneSource = new GeoJsonSource.Builder("drone").build();
            droneSource.bindTo(style);

            droneLayer = new SymbolLayer("drone", "drone");
            droneLayer.iconImage("drone");
            droneLayer.iconAllowOverlap(true);
            droneLayer.bindTo(style);

            style.addImage("mode-target", BitmapFactory.decodeResource(mapView.getResources(), R.drawable.drone));
            final GeoJsonSource modeTargetSource = new GeoJsonSource.Builder("mode-target").build();
            modeTargetSource.bindTo(style);

            modeTargetLayer = new SymbolLayer("mode-target", "mode-target");
            modeTargetLayer.iconImage("mode-target");
            modeTargetLayer.iconAllowOverlap(true);
            modeTargetLayer.iconOpacity(0.5);
            modeTargetLayer.bindTo(style);

            if (!cameraOptionsInitialized) {
                setCameraOptions(new CameraOptions.Builder().center(map.getCameraState().getCenter()).zoom(map.getCameraState().getZoom()).build(), false);
            }
            mapView.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onStart() {
        super.onStart();

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
    }

    @Override
    public void onStop() {
        super.onStop();

        if (updateTimer != null) {
            updateTimer.cancel();
        }

        if (locationComponent != null) {
            locationComponent.onStop();
            locationComponent.setEnabled(false);
            locationComponent = null;
        }

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
    }

    public interface MoreMenuItem {
        String getTitle();
        void onClick();
    }

    public void onMore(final Context context, final View anchor, final MapboxMapFragment.MoreMenuItem[] actions) {
        final PopupMenu actionSheet = new PopupMenu(context, anchor);

        actionSheet.getMenu().add(R.string.MapboxMap_reset);
        actionSheet.getMenu().add(R.string.MapboxMap_drone_heading);
        actionSheet.getMenu().add(R.string.MapboxMap_drone_north_up);

        if (actions != null) {
            for (final MapboxMapFragment.MoreMenuItem action : actions) {
                actionSheet.getMenu().add(action.getTitle());
            }
        }

        actionSheet.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                if (item.getTitle() == getString(R.string.MapboxMap_reset)) {
                    tracking = Tracking.NONE;
                    return true;
                }

                if (item.getTitle() == getString(R.string.MapboxMap_drone_heading)) {
                    tracking = Tracking.DRONE_HEADING;
                    return true;
                }

                if (item.getTitle() == getString(R.string.MapboxMap_drone_north_up)) {
                    tracking = Tracking.DRONE_NORTH_UP;
                    return true;
                }

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
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(update);
        }
    }

    private Runnable update = new Runnable() {
        public void run() {
            if (!isAdded() || map == null) {
                return;
            }

            final Style style = map.getStyle();
            final DroneStateAdapter state = getDroneState();
            if (style == null || state == null) {
                return;
            }

            final Location droneHomeLocation = state.getHomeLocation();
            if (droneHomeLocation != null) {
                style.setStyleGeoJSONSourceData("drone-home", GeoJSONSourceData.valueOf(Feature.fromGeometry(Point.fromLngLat(droneHomeLocation.getLongitude(), droneHomeLocation.getLatitude()))));
            }

            Location droneLocation = state.getLocation();
            if (droneLocation != null) {
                final DroneOffsets offsets = Dronelink.getInstance().droneOffsets;
                droneLocation = Convert.locationWithBearing(droneLocation, offsets.droneCoordinate.direction + Math.PI, offsets.droneCoordinate.magnitude);
                style.setStyleGeoJSONSourceData("drone", GeoJSONSourceData.valueOf(Feature.fromGeometry(Point.fromLngLat(droneLocation.getLongitude(), droneLocation.getLatitude()))));
                droneLayer.iconRotate((float)(Convert.RadiansToDegrees(state.getOrientation().getYaw()) - map.getCameraState().getBearing()));
            }

            if (tracking != Tracking.NONE) {
                final Location location = state.getLocation();
                if (location != null) {
                    final double distance = Math.max(20, state.getAltitude() / 2.0);

                    final List<Point> visibleCoordinates = new ArrayList<>();
                    visibleCoordinates.add(getPoint(Convert.locationWithBearing(location, 0, distance)));
                    visibleCoordinates.add(getPoint(Convert.locationWithBearing(location, Math.PI / 2, distance)));
                    visibleCoordinates.add(getPoint(Convert.locationWithBearing(location, Math.PI, distance)));
                    visibleCoordinates.add(getPoint(Convert.locationWithBearing(location, 3 * Math.PI / 2, distance)));
                    setVisibleCoordinates(visibleCoordinates, tracking == Tracking.DRONE_NORTH_UP ? 0 : Convert.RadiansToDegrees(state.getOrientation().getYaw()));
                }
            }
        }
    };

    private void setVisibleCoordinates(final List<Point> visibleCoordinates) {
        setVisibleCoordinates(visibleCoordinates, null);
    }

    private void setVisibleCoordinates(final List<Point> visibleCoordinates, final Double direction) {
        if (visibleCoordinates.size() == 0) {
            return;
        }

        final double inset = 0.2;
        final float density = mapView.getResources().getDisplayMetrics().density;
        final float heightDp = mapView.getHeight() / density;
        final float widthDp = mapView.getWidth() / density;

        final EdgeInsets edgePadding = new EdgeInsets(
                heightDp * (heightDp > 300 ? 0.25 : inset),
                widthDp * inset,
                heightDp * (heightDp > 300 ? 0.38 : inset),
                widthDp * inset);

        final Double updatedDirection = direction == null ? null : direction < 0 ? direction + 360 : direction;
        setCameraOptions(map.cameraForCoordinates(visibleCoordinates, edgePadding, updatedDirection, null), true);
    }

    private Runnable addMissionReferenceMarker = new Runnable() {
        public void run() {
            if (map == null) {
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        final Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(addMissionReferenceMarker);
                        }
                    }
                }, 100);
                return;
            }

            final MissionExecutor missionExecutorLocal = missionExecutor;
            if (missionExecutorLocal != null) {
                PointAnnotationOptions pointAnnotationOptions = new PointAnnotationOptions()
                        .withPoint(getPoint(missionExecutorLocal.referenceCoordinate.getLocation()))
                        .withIconImage(BitmapFactory.decodeResource(mapView.getResources(), R.drawable.mission_reference));
                missionReferenceAnnotation = pointAnnotationManager.create(pointAnnotationOptions);
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
                        final Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(updateMissionRequiredTakeoffArea);
                        }
                    }
                }, 100);
                return;
            }

            if (missionRequiredTakeoffAreaAnnotation != null) {
                polygonAnnotationManager.delete(missionRequiredTakeoffAreaAnnotation);
            }


            final MissionExecutor missionExecutorLocal = missionExecutor;
            if (missionExecutorLocal != null) {
                final MissionExecutor.TakeoffArea requiredTakeoffArea = missionExecutorLocal.requiredTakeoffArea;
                if (requiredTakeoffArea != null) {
                    final Location takeoffLocation = requiredTakeoffArea.coordinate.getLocation();
                    final List<Point> takeoffAreaPoints = new LinkedList<>();
                    for (int i = 0; i < 100; i++) {
                        final Location location = Convert.locationWithBearing(takeoffLocation, (double)i / 100.0 * Math.PI * 2.0, requiredTakeoffArea.distanceTolerance.horizontal);
                        takeoffAreaPoints.add(getPoint(location));
                    }
                    PolygonAnnotationOptions polygonAnnotationOptions = new PolygonAnnotationOptions()
                            .withPoints(Collections.singletonList(takeoffAreaPoints))
                            .withFillOpacity(0.25)
                            .withFillColor(Color.parseColor("#ffa726"));
                    missionRequiredTakeoffAreaAnnotation = polygonAnnotationManager.create(polygonAnnotationOptions);
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
                        final Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(updateMissionRestrictionZones);
                        }
                    }
                }, 100);
                return;
            }

            for (final PolygonAnnotation missionRestrictionZoneAnnotation : missionRestrictionZoneAnnotations) {
                polygonAnnotationManager.delete(missionRestrictionZoneAnnotation);
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

                        final List<Point> points = new LinkedList<>();
                        switch (restrictionZone.zone.shape) {
                            case CIRCLE:
                                final Location center = coordinates[0].getLocation();
                                final double radius = center.distanceTo(coordinates[1].getLocation());
                                for (int p = 0; p < 100; p++) {
                                    final Location location = Convert.locationWithBearing(center, (double)p / 100.0 * Math.PI * 2.0, radius);
                                    points.add(getPoint(location));
                                }
                                break;

                            case POLYGON:
                                for (int c = 0; c < coordinates.length; c++) {
                                    points.add(Point.fromLngLat(coordinates[c].longitude, coordinates[c].latitude));
                                }
                                break;
                        }

                        final int fillColor = DronelinkUI.parseHexColor(restrictionZone.zone.color, Color.argb(255,255, 23, 68));
                        PolygonAnnotationOptions polygonAnnotationOptions = new PolygonAnnotationOptions()
                                .withPoints(Collections.singletonList(points))
                                .withFillOpacity(restrictionZone.zone.color != null && restrictionZone.zone.color.length() == 9 ? (Color.alpha(fillColor)) / 255f : 0.5)
                                .withFillColor(fillColor)
                                .withFillOutlineColor(DronelinkUI.parseHexColor(restrictionZone.zone.color, Color.argb((int)(255 * 0.7), 255, 23, 68)));
                        missionRestrictionZoneAnnotations.add(polygonAnnotationManager.create(polygonAnnotationOptions));
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
                        final Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(updateMissionEstimate);
                        }
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
                polylineAnnotationManager.delete(missionEstimateBackgroundAnnotation);
            }

            if (missionEstimateForegroundAnnotation != null) {
                polylineAnnotationManager.delete(missionEstimateForegroundAnnotation);
            }

            if (missionExecutorLocal == null || estimate == null) {
                return;
            }

            final List<Point> visibleCoordinates = new LinkedList<>();

            final GeoSpatial[] estimateSpatials = estimate.spatials;
            if (estimateSpatials != null && estimateSpatials.length > 0) {
                final List<Point> pathPoints = new LinkedList<>();
                for (final GeoSpatial spatial : estimateSpatials) {
                    pathPoints.add(Point.fromLngLat(spatial.coordinate.longitude, spatial.coordinate.latitude));
                }
                final PolylineAnnotationOptions polylineAnnotationOptions = new PolylineAnnotationOptions()
                        .withPoints(pathPoints).withLineWidth(6.0).withLineColor(Color.parseColor("#0277bd"));
                missionEstimateBackgroundAnnotation = polylineAnnotationManager.create(polylineAnnotationOptions);

                polylineAnnotationOptions.withLineWidth(2.5).withLineColor(Color.parseColor("#26c6da"));
                missionEstimateForegroundAnnotation = polylineAnnotationManager.create(polylineAnnotationOptions);

                if (!missionCentered) {
                    visibleCoordinates.addAll(pathPoints);
                }
            }

            final List<Point> reengagementPoints = missionReengagementEstimate();
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

    private List<Point> missionReengagementEstimate() {
        if (missionReengagementEstimateBackgroundAnnotation != null) {
            polylineAnnotationManager.delete(missionReengagementEstimateBackgroundAnnotation);
        }
        missionReengagementEstimateBackgroundAnnotation = null;

        if (missionReengagementEstimateForegroundAnnotation != null) {
            polylineAnnotationManager.delete(missionReengagementEstimateForegroundAnnotation);
        }
        missionReengagementEstimateForegroundAnnotation = null;

        if (missionReengagementDroneAnnotation != null) {
            pointAnnotationManager.delete(missionReengagementDroneAnnotation);
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

        List<Point> reengagementPoints = null;
        if (reengagementEstimateSpatials != null && reengagementEstimateSpatials.length > 0) {
            reengagementPoints = new LinkedList<>();
            for (final GeoSpatial spatial : reengagementEstimateSpatials) {
                reengagementPoints.add(Point.fromLngLat(spatial.coordinate.longitude, spatial.coordinate.latitude));
            }
        }

        if (reengaging && reengagementPoints != null) {
            final PolylineAnnotationOptions polylineAnnotationOptions = new PolylineAnnotationOptions()
                    .withPoints(reengagementPoints).withLineWidth(6.0).withLineColor(Color.parseColor("#6a1b9a"));
            missionReengagementEstimateBackgroundAnnotation = polylineAnnotationManager.create(polylineAnnotationOptions);
            polylineAnnotationOptions.withLineWidth(2.5).withLineColor(Color.parseColor("#e040fb"));
            missionReengagementEstimateForegroundAnnotation = polylineAnnotationManager.create(polylineAnnotationOptions);
        }

        Point reengagementCoordinate = null;
        if (reengaging) {
            if (reengagementPoints != null && reengagementPoints.size() > 0) {
                reengagementCoordinate = reengagementPoints.get(reengagementPoints.size() - 1);
            }
        }
        else {
            final GeoSpatial reengagementSpatial = missionExecutor.getReengagementSpatial();
            if (reengagementSpatial != null) {
                reengagementCoordinate = Point.fromLngLat(reengagementSpatial.coordinate.longitude, reengagementSpatial.coordinate.latitude);
            }
        }

        if (reengagementCoordinate != null && (!engaged || reengaging)) {
            PointAnnotationOptions pointAnnotationOptions = new PointAnnotationOptions()
                    .withPoint(reengagementCoordinate).withIconImage(BitmapFactory.decodeResource(mapView.getResources(), R.drawable.drone_reengagement));
            missionReengagementDroneAnnotation = pointAnnotationManager.create(pointAnnotationOptions);
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
                pointAnnotationManager.delete(funcDroneAnnotations.remove(funcDroneAnnotations.size() - 1));
            }

            while (funcDroneAnnotations.size() < spatials.size()) {
                PointAnnotationOptions pointAnnotationOptions = new PointAnnotationOptions()
                        .withPoint(Point.fromLngLat(spatials.get(funcDroneAnnotations.size()).coordinate.longitude, spatials.get(funcDroneAnnotations.size()).coordinate.latitude))
                        .withIconImage(BitmapFactory.decodeResource(mapView.getResources(), R.drawable.func_input_drone));
                funcDroneAnnotations.add(pointAnnotationManager.create(pointAnnotationOptions));
            }

            int index = 0;
            for (final PointAnnotation funcDroneAnnotation : funcDroneAnnotations) {
                funcDroneAnnotation.setPoint(Point.fromLngLat(spatials.get(index).coordinate.longitude, spatials.get(index).coordinate.latitude));
                index++;
            }

            if (mapCenterSpatial != null) {
                setCameraOptions(new CameraOptions.Builder()
                        .center(Point.fromLngLat(mapCenterSpatial.coordinate.longitude, mapCenterSpatial.coordinate.latitude)).zoom(19.25).build(), true);
            }

            for (final PolygonAnnotation funcMapOverlayAnnotation : funcMapOverlayAnnotations) {
                polygonAnnotationManager.delete(funcMapOverlayAnnotation);
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
                        final List<Point> points = new LinkedList<>();
                        for (int c = 0; c < mapOverlay.coordinates.length; c++) {
                            points.add(Point.fromLngLat(mapOverlay.coordinates[c].longitude, mapOverlay.coordinates[c].latitude));
                        }

                        final int fillColor = DronelinkUI.parseHexColor(mapOverlay.color, Color.argb(255,255, 23, 68));
                        PolygonAnnotationOptions polygonAnnotationOptions = new PolygonAnnotationOptions()
                                .withPoints(Collections.singletonList(points))
                                .withFillOpacity(mapOverlay.color != null && mapOverlay.color.length() == 9 ? ((float)Color.alpha(fillColor)) / 255f : 0.5)
                                .withFillColor(fillColor)
                                .withFillOutlineColor(DronelinkUI.parseHexColor(mapOverlay.color, Color.argb((int)(255 * 0.7), 255, 23, 68)));
                        funcMapOverlayAnnotations.add(polygonAnnotationManager.create(polygonAnnotationOptions));
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
                style.setStyleGeoJSONSourceData("mode-target", GeoJSONSourceData.valueOf(Feature.fromGeometry(Point.fromLngLat(modeTarget.coordinate.longitude, modeTarget.coordinate.latitude))));
                modeTargetLayer.iconRotate((float)(Convert.RadiansToDegrees(modeTarget.orientation.getYaw()) - map.getCameraState().getBearing()));
            }

            if (tracking == Tracking.NONE) {
                final List<Point> visibleCoordinates = new LinkedList<>();
                final GeoCoordinate[] modeVisibleCoordinates = modeExecutorLocal.getVisibleCoordinates();
                if (modeVisibleCoordinates != null) {
                    for (final GeoCoordinate coordinate : modeVisibleCoordinates) {
                        visibleCoordinates.add(Point.fromLngLat(coordinate.longitude, coordinate.latitude));
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
            final Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (map != null) {
                            setCameraOptions(new CameraOptions.Builder().center(getPoint(droneLocation)).zoom(18.5).build(), true);
                        }
                    }
                });
            }
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
        missionExecutor = executor;
        missionCentered = false;
        executor.addListener(this);
        applyUserInterfaceSettings(executor.userInterfaceSettings);
        final Activity activity = getActivity();
        if (activity != null) {
            if (executor.userInterfaceSettings != null && executor.userInterfaceSettings.droneOffsetsVisible != null && executor.userInterfaceSettings.droneOffsetsVisible) {
                activity.runOnUiThread(addMissionReferenceMarker);
            }
            activity.runOnUiThread(updateMissionRequiredTakeoffArea);
            activity.runOnUiThread(updateMissionRestrictionZones);
            if (executor.isEstimated()) {
                activity.runOnUiThread(updateMissionEstimate);
            }
        }
    }

    @Override
    public void onMissionUnloaded(final MissionExecutor executor) {
        missionExecutor = null;
        missionCentered = false;
        executor.removeListener(this);
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (pointAnnotationManager != null && missionReferenceAnnotation != null) {
                        pointAnnotationManager.delete(missionReferenceAnnotation);
                    }
                    missionReferenceAnnotation = null;
                }
            });
            activity.runOnUiThread(updateMissionRequiredTakeoffArea);
            activity.runOnUiThread(updateMissionRestrictionZones);
            activity.runOnUiThread(updateMissionEstimate);
        }
    }

    @Override
    public void onFuncLoaded(final FuncExecutor executor) {
        funcExecutor = executor;
        executor.addListener(this);
        applyUserInterfaceSettings(executor.getUserInterfaceSettings());
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateFuncElements);
        }
    }

    @Override
    public void onFuncUnloaded(final FuncExecutor executor) {
        funcExecutor = null;
        executor.removeListener(this);
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateFuncElements);
        }
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
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateMissionEstimate);
        }
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
            final Activity activity = getActivity();
            if (activity != null) {
                activity.runOnUiThread(updateMissionReengagementEstimate);
            }
        }
    }

    @Override
    public void onMissionDisengaged(final MissionExecutor executor, final MissionExecutor.Engagement engagement, final Message reason) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateMissionReengagementEstimate);
        }
    }

    @Override
    public void onMissionUpdatedDisconnected(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateMissionReengagementEstimate);
        }
    }

    @Override
    public void onFuncInputsChanged(final FuncExecutor executor) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateFuncElements);
        }
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
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateModeElements);
        }
    }

    @Override
    public void onModeExecuted(final ModeExecutor executor, final ModeExecutor.Engagement engagement) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(updateModeElements);
        }
    }

    @Override
    public void onModeDisengaged(final ModeExecutor executor, final ModeExecutor.Engagement engagement, final Message reason) {}

    private void enableLocationComponent() {
        if (PermissionsManager.areLocationPermissionsGranted(mapView.getContext())) {
            locationComponent = LocationComponentUtils.getLocationComponent(mapView);
            locationComponent.setEnabled(true);
            locationComponent.setLocationPuck(LocationComponentUtils.createDefault2DPuck(locationComponent, mapView.getContext(), true));
            //Per Mapbox docs https://docs.mapbox.com/android/maps/guides/user-location/ Interface LocationComponentPlugin2 extends the original LocationComponentPlugin interface, it is introduced inorder to avoid breaking API changes.
            final LocationComponentPlugin2 locationComponent2 = LocationComponentUtils.getLocationComponent2(mapView);
            locationComponent2.setShowAccuracyRing(true);
            locationComponent2.setPuckBearingSource(PuckBearingSource.HEADING);
            if (!missionCentered) {
                final Location userLocation = Dronelink.getInstance().getLocation();
                if (userLocation != null) {
                    final Point userLocationPoint = Point.fromLngLat(userLocation.getLongitude(), userLocation.getLatitude());
                    setCameraOptions(new CameraOptions.Builder().center(userLocationPoint).zoom(17.0).build(),false);
                }
            }
        }
    }

    private void setCameraOptions(final CameraOptions cameraOptions, final boolean animated) {
        if (map == null || cameraOptions == null) {
            return;
        }

        if (animated && mapView.getVisibility() == View.VISIBLE) {
            CameraAnimationsUtils.getCamera(mapView).easeTo(cameraOptions, new MapAnimationOptions.Builder().duration(CAMERA_ANIMATION_DURATION).build());
        } else {
            map.setCamera(cameraOptions);
        }
        cameraOptionsInitialized = true;
    }

    private Point getPoint(final Location location) {
        return Point.fromLngLat(location.getLongitude(), location.getLatitude());
    }
}
