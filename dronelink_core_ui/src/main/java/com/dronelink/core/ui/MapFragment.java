//  MapFragment.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 11/8/19.
//  Copyright © 2019 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.dronelink.core.Convert;
import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.MissionExecutor;
import com.dronelink.core.adapters.DroneStateAdapter;
import com.dronelink.core.mission.core.GeoCoordinate;
import com.dronelink.core.mission.core.Message;
import com.mapbox.android.core.location.LocationEngineCallback;
import com.mapbox.android.core.location.LocationEngineResult;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.annotations.Annotation;
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

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MapFragment extends Fragment implements Dronelink.Listener, DroneSessionManager.Listener, MissionExecutor.Listener, OnMapReadyCallback {
    private DroneSession session;
    private MissionExecutor missionExecutor;
    private com.mapbox.mapboxsdk.maps.MapView mapView;
    private MapboxMap map;
    private Annotation missionRequiredTakeoffAreaAnnotation;
    private Annotation missionPathBackgroundAnnotation;
    private Annotation missionPathForegroundAnnotation;
    private final long updateMillis = 100;
    private Timer updateTimer;

    private DroneStateAdapter getDroneState() {
        if (session == null || session.getState() == null) {
            return null;
        }
        return session.getState().value;
    }

    public MapFragment() {}

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
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
        Dronelink.getInstance().removeListener(this);
        Dronelink.getInstance().getSessionManager().removeListener(this);
        if (missionExecutor != null) {
            missionExecutor.removeListener(this);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (updateTimer != null) {
            updateTimer.cancel();
        }

        mapView.onDestroy();
    }

    @Override
    public void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
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
                style.getLayerAs("drone").setProperties(PropertyFactory.iconRotate((float)(Convert.RadiansToDegrees(state.getMissionOrientation().getYaw()) - map.getCameraPosition().bearing)));
            }
        }
    };

    @SuppressWarnings({"MissingPermission"})
    private Runnable updateMissionEstimate = new Runnable() {
        public void run() {
            if (map == null) {
                return;
            }

            if (missionRequiredTakeoffAreaAnnotation != null) {
                map.removeAnnotation(missionRequiredTakeoffAreaAnnotation);
            }

            if (missionPathBackgroundAnnotation != null) {
                map.removeAnnotation(missionPathBackgroundAnnotation);
            }

            if (missionPathForegroundAnnotation != null) {
                map.removeAnnotation(missionPathForegroundAnnotation);
            }

            if (missionExecutor != null) {
                final MissionExecutor.TakeoffArea requiredTakeoffArea = missionExecutor.getRequiredTakeoffArea();
                if (requiredTakeoffArea != null) {
                    final Location takeoffLocation = requiredTakeoffArea.coordinate.getLocation();
                    final List<LatLng> takeoffAreaPoints = new LinkedList<>();
                    for (int i = 0; i < 100; i++) {
                        final Location location = Convert.locationWithBearing(takeoffLocation, (double)i / 100.0 * Math.PI * 2.0, requiredTakeoffArea.distanceTolerance.horizontal);
                        takeoffAreaPoints.add(new LatLng(location.getLatitude(), location.getLongitude()));
                    }
                    missionRequiredTakeoffAreaAnnotation = map.addPolygon(new PolygonOptions().addAll(takeoffAreaPoints).alpha((float)0.75).fillColor(Color.parseColor("#ffa726")));
                }


                final GeoCoordinate[][] pathCoordinates = missionExecutor.getEstimateSegmentCoordinates(null);
                final List<LatLng> pathPoints = new LinkedList<>();
                for (final GeoCoordinate[] segment : pathCoordinates) {
                    for (final GeoCoordinate coordinate : segment) {
                        pathPoints.add(new LatLng(coordinate.latitude, coordinate.longitude));
                    }
                }

                if (pathPoints.size() > 0) {
                    missionPathBackgroundAnnotation = map.addPolyline(new PolylineOptions().addAll(pathPoints).width(6).color(Color.parseColor("#0277bd")));
                    missionPathForegroundAnnotation = map.addPolyline(new PolylineOptions().addAll(pathPoints).width((float)2.5).color(Color.parseColor("#26c6da")));

                    map.getLocationComponent().getLocationEngine().getLastLocation(new LocationEngineCallback<LocationEngineResult>() {
                        @Override
                        public void onSuccess(LocationEngineResult result) {
                            updateBounds(pathPoints, result.getLastLocation());
                        }

                        @Override
                        public void onFailure(@NonNull Exception exception) {
                            updateBounds(pathPoints, null);
                        }
                    });
                }
            }
        }
    };

    private void updateBounds(final List<LatLng> points, final Location userLocation) {
        final LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        bounds.includes(points);
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 10));
    }


    @Override
    public void onOpened(final DroneSession session) {
        this.session = session;
    }

    @Override
    public void onClosed(final DroneSession session) {
        this.session = null;
    }

    @Override
    public void onRegistered(final String error) {
    }

    @Override
    public void onMissionLoaded(final MissionExecutor executor) {
        executor.addListener(this);
        missionExecutor = executor;
        getActivity().runOnUiThread(updateMissionEstimate);
    }

    @Override
    public void onMissionUnloaded(final MissionExecutor executor) {
        executor.removeListener(this);
        missionExecutor = null;
        getActivity().runOnUiThread(updateMissionEstimate);
    }

    @Override
    public void onMissionEstimated(final MissionExecutor executor, final long durationMillis) {
        getActivity().runOnUiThread(updateMissionEstimate);
    }

    @Override
    public void onMissionEngaged(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {}

    @Override
    public void onMissionExecuted(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {}

    @Override
    public void onMissionDisengaged(final MissionExecutor executor, final MissionExecutor.Engagement engagement, final Message reason) {}

    @Override
    public void onMapReady(@NonNull final MapboxMap mapboxMap) {
        map = mapboxMap;
        final MapFragment self = this;
        mapboxMap.setStyle(Style.SATELLITE_STREETS, new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                enableLocationComponent(style);

                style.addImage("drone-home", BitmapFactory.decodeResource(MapFragment.this.getResources(), R.drawable.home));
                final GeoJsonSource droneHomeSource = new GeoJsonSource("drone-home", Feature.fromGeometry(Point.fromLngLat(0, 0)));
                style.addSource(droneHomeSource);

                final SymbolLayer droneHomeLayer;
                droneHomeLayer = new SymbolLayer("drone-home", "drone-home");
                droneHomeLayer.withProperties(PropertyFactory.iconImage("drone-home"));
                style.addLayer(droneHomeLayer);

                style.addImage("drone", BitmapFactory.decodeResource(MapFragment.this.getResources(), R.drawable.drone));
                final GeoJsonSource droneSource = new GeoJsonSource("drone", Feature.fromGeometry(Point.fromLngLat(0, 0)));
                style.addSource(droneSource);

                final SymbolLayer droneLayer;
                droneLayer = new SymbolLayer("drone", "drone");
                droneLayer.withProperties(PropertyFactory.iconImage("drone"));
                style.addLayer(droneLayer);

                updateTimer = new Timer();
                updateTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        updateTimer();
                    }
                }, 0, updateMillis);

                Dronelink.getInstance().addListener(self);
                Dronelink.getInstance().getSessionManager().addListener(self);
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
                    if (result.getLastLocation() != null) {
                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(result.getLastLocation().getLatitude(), result.getLastLocation().getLongitude()), 15));
                    }
                }

                @Override
                public void onFailure(@NonNull Exception exception) {

                }
            });
        }
    }
}
