//  MicrosoftMapFragment.java
//  DronelinkCoreUI
//
//  Created by Jim McAndrew on 5/1/20.
//  Copyright © 2020 Dronelink. All rights reserved.
//
package com.dronelink.core.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.dronelink.core.CameraFile;
import com.dronelink.core.Convert;
import com.dronelink.core.DatedValue;
import com.dronelink.core.DroneOffsets;
import com.dronelink.core.DroneSession;
import com.dronelink.core.DroneSessionManager;
import com.dronelink.core.Dronelink;
import com.dronelink.core.FuncExecutor;
import com.dronelink.core.MissionExecutor;
import com.dronelink.core.ModeExecutor;
import com.dronelink.core.adapters.DroneStateAdapter;
import com.dronelink.core.adapters.GimbalStateAdapter;
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
import com.microsoft.maps.AltitudeReferenceSystem;
import com.microsoft.maps.GeoboundingBox;
import com.microsoft.maps.Geocircle;
import com.microsoft.maps.Geopath;
import com.microsoft.maps.Geopoint;
import com.microsoft.maps.Geoposition;
import com.microsoft.maps.MapAnimationKind;
import com.microsoft.maps.MapCamera;
import com.microsoft.maps.MapCameraChangeReason;
import com.microsoft.maps.MapCameraChangedEventArgs;
import com.microsoft.maps.MapElementCollisionBehavior;
import com.microsoft.maps.MapElementLayer;
import com.microsoft.maps.MapFlyout;
import com.microsoft.maps.MapIcon;
import com.microsoft.maps.MapImage;
import com.microsoft.maps.MapPolygon;
import com.microsoft.maps.MapPolyline;
import com.microsoft.maps.MapProjection;
import com.microsoft.maps.MapScene;
import com.microsoft.maps.MapStyleSheets;
import com.microsoft.maps.OnMapCameraChangedListener;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MicrosoftMapFragment extends Fragment implements Dronelink.Listener, DroneSessionManager.Listener, DroneSession.Listener, MissionExecutor.Listener, FuncExecutor.Listener, ModeExecutor.Listener {
    private enum Tracking {
        NONE,
        THIRD_PERSON_NADIR, //follow
        THIRD_PERSON_OBLIQUE, //chase plane
        FIRST_PERSON //fpv
    }

    private enum Style { STREETS, SATELLITE }

    private static int SCENE_ELEMENT_USER = 1 << 0;
    private static int SCENE_ELEMENT_DRONE_CURRENT = 1 << 1;
    private static int SCENE_ELEMENT_DRONE_HOME = 1 << 2;
    private static int SCENE_ELEMENT_DRONE_TAKEOFF = 1 << 3;
    private static int SCENE_ELEMENT_MISSION_REENGAGEMENT = 1 << 4;
    private static int SCENE_ELEMENT_MISSION_MAIN = 1 << 5;
    private static int SCENE_ELEMENT_MISSION_TAKEOFF = 1 << 6;
    private static int SCENE_ELEMENTS_STANDARD = SCENE_ELEMENT_DRONE_CURRENT | SCENE_ELEMENT_DRONE_HOME | SCENE_ELEMENT_DRONE_TAKEOFF | SCENE_ELEMENT_MISSION_TAKEOFF | SCENE_ELEMENT_MISSION_REENGAGEMENT | SCENE_ELEMENT_MISSION_MAIN | SCENE_ELEMENT_MISSION_TAKEOFF;

    private DroneSession session;
    private boolean sessionLocationCentered = false;
    private MissionExecutor missionExecutor;
    private FuncExecutor funcExecutor;
    private ModeExecutor modeExecutor;
    private com.microsoft.maps.MapView mapView;
    private MapElementLayer droneLayer = new MapElementLayer();
    private MapIcon droneIcon = new MapIcon();
    private MapIcon droneHomeIcon = new MapIcon();
    private MapPolyline droneSessionPolyline;
    private List<Geoposition> droneSessionPositions = new ArrayList<>();
    private MapPolyline droneMissionExecutedPolyline;
    private List<Geoposition> droneMissionExecutedPositions = new ArrayList<>();
    private MapElementLayer missionLayer = new MapElementLayer();
    private MapElementLayer missionReengagementLayer = new MapElementLayer();
    private MapElementLayer funcLayer = new MapElementLayer();
    private List<MapIcon> funcInputDroneIcons = new ArrayList<>();
    private MapElementLayer funcMapOverlaysLayer = new MapElementLayer();
    private MapElementLayer modeLayer = new MapElementLayer();
    private MapIcon modeTargetIcon = new MapIcon();
    private final long updateDroneElementsMillis = 100;
    private Timer updateDroneElementsTimer;
    private double droneTakeoffAltitude = 0;
    private AltitudeReferenceSystem droneTakeoffAltitudeReferenceSystem = AltitudeReferenceSystem.SURFACE;
    private Style style = Style.STREETS;
    private Tracking tracking = Tracking.NONE;
    private Tracking trackingPrevious = Tracking.NONE;

    private DroneStateAdapter getDroneState() {
        if (session == null || session.getState() == null) {
            return null;
        }
        return session.getState().value;
    }

    public MicrosoftMapFragment() {}

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_microsoft_map, container, false);
        view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
        return view;
    }

    @Override
    public void onViewCreated(final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mapView = getView().findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.addOnMapCameraChangedListener(new OnMapCameraChangedListener() {
            @Override
            public boolean onMapCameraChanged(MapCameraChangedEventArgs mapCameraChangedEventArgs) {
                if (mapCameraChangedEventArgs.changeReason == MapCameraChangeReason.USER_INTERACTION) {
                    switch (tracking) {
                        case NONE:
                        case THIRD_PERSON_OBLIQUE:
                        case FIRST_PERSON:
                            break;

                        case THIRD_PERSON_NADIR:
                            tracking = Tracking.NONE;
                            trackingPrevious = Tracking.NONE;
                            break;
                    }
                }
                return true;
            }
        });

        mapView.setCredentialsKey(getActivity().getIntent().getStringExtra("mapCredentialsKey"));
        mapView.setMapStyleSheet(MapStyleSheets.roadDark());
        mapView.setMapProjection(MapProjection.GLOBE);
        mapView.setBusinessLandmarksVisible(false);
        mapView.setBuildingsVisible(true);
        mapView.setTransitFeaturesVisible(false);
        mapView.getUserInterfaceOptions().setCompassButtonVisible(false);
        mapView.getUserInterfaceOptions().setTiltButtonVisible(false);
        mapView.getUserInterfaceOptions().setZoomButtonsVisible(false);
        mapView.setBackgroundColor(Color.BLACK);

        updateScene(SCENE_ELEMENT_USER, MapAnimationKind.NONE);

        droneLayer.setZIndex(10);
        droneHomeIcon.setImage(new MapImage(BitmapFactory.decodeResource(MicrosoftMapFragment.this.getResources(), R.drawable.home)));
        droneHomeIcon.setFlat(true);
        droneHomeIcon.setDesiredCollisionBehavior(MapElementCollisionBehavior.REMAIN_VISIBLE);
        droneLayer.getElements().add(droneHomeIcon);
        droneIcon.setImage(new MapImage(BitmapFactory.decodeResource(MicrosoftMapFragment.this.getResources(), R.drawable.drone)));
        droneIcon.setFlat(true);
        droneIcon.setDesiredCollisionBehavior(MapElementCollisionBehavior.REMAIN_VISIBLE);
        droneLayer.getElements().add(droneIcon);
        mapView.getLayers().add(droneLayer);

        missionLayer.setZIndex(1);
        mapView.getLayers().add(missionLayer);

        missionReengagementLayer.setZIndex(1);
        mapView.getLayers().add(missionReengagementLayer);

        funcMapOverlaysLayer.setZIndex(1);
        mapView.getLayers().add(funcMapOverlaysLayer);

        funcLayer.setZIndex(1);
        mapView.getLayers().add(funcLayer);

        modeLayer.setZIndex(1);
        modeTargetIcon.setImage(new MapImage(BitmapFactory.decodeResource(MicrosoftMapFragment.this.getResources(), R.drawable.drone)));
        modeTargetIcon.setFlat(true);
        modeTargetIcon.setOpacity(0.5f);
        modeTargetIcon.setDesiredCollisionBehavior(MapElementCollisionBehavior.REMAIN_VISIBLE);
        modeLayer.getElements().add(modeTargetIcon);
        mapView.getLayers().add(modeLayer);
    }

    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();

        final DroneSessionManager manager = Dronelink.getInstance().getTargetDroneSessionManager();
        if (manager != null) {
            manager.addListener(this);
        }
        Dronelink.getInstance().addListener(this);

        updateDroneElementsTimer = new Timer();
        updateDroneElementsTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateDroneElements();
            }
        }, 0, updateDroneElementsMillis);
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

        if (updateDroneElementsTimer != null) {
            updateDroneElementsTimer.cancel();
        }

        final DroneSessionManager manager = Dronelink.getInstance().getTargetDroneSessionManager();
        if (manager != null) {
            manager.removeListener(this);
        }
        Dronelink.getInstance().removeListener(this);
        if (session != null) {
            session.removeListener(this);
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

    public void onMore(final Context context, final View anchor, final MoreMenuItem[] actions) {
        final PopupMenu actionSheet = new PopupMenu(context, anchor);

        actionSheet.getMenu().add(R.string.MicrosoftMap_reset);
        actionSheet.getMenu().add(R.string.MicrosoftMap_follow);
        actionSheet.getMenu().add(R.string.MicrosoftMap_chase_plane);
        actionSheet.getMenu().add(R.string.MicrosoftMap_fpv);
        if (tracking == Tracking.NONE) {
            if (style == Style.STREETS) {
                actionSheet.getMenu().add(R.string.MicrosoftMap_satellite);
            } else {
                actionSheet.getMenu().add(R.string.MicrosoftMap_streets);
            }
        }

        if (actions != null) {
            for (final MoreMenuItem action : actions) {
                actionSheet.getMenu().add(action.getTitle());
            }
        }

        actionSheet.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(final MenuItem item) {
                if (item.getTitle() == getString(R.string.MicrosoftMap_reset)) {
                    tracking = Tracking.NONE;
                    trackingPrevious = Tracking.NONE;
                    updateScene();
                    return true;
                }

                if (item.getTitle() == getString(R.string.MicrosoftMap_follow)) {
                    tracking = Tracking.THIRD_PERSON_NADIR;
                    return true;
                }

                if (item.getTitle() == getString(R.string.MicrosoftMap_chase_plane)) {
                    tracking = Tracking.THIRD_PERSON_OBLIQUE;
                    return true;
                }

                if (item.getTitle() == getString(R.string.MicrosoftMap_fpv)) {
                    tracking = Tracking.FIRST_PERSON;
                    return true;
                }

                if (item.getTitle() == getString(R.string.MicrosoftMap_satellite)) {
                    updateStyle(Style.SATELLITE);
                    return true;
                }

                if (item.getTitle() == getString(R.string.MicrosoftMap_streets)) {
                    updateStyle(Style.STREETS);
                    return true;
                }

                if (actions != null) {
                    for (final MoreMenuItem action : actions) {
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

    private void updateStyle(final Style style) {
        this.style = style;
        switch (style) {
            case STREETS:
                mapView.setMapStyleSheet(MapStyleSheets.roadDark());
                break;

            case SATELLITE:
                mapView.setMapStyleSheet(MapStyleSheets.aerialWithOverlay());
                break;
        }
    }

    private void updateDroneElements() {
        if (!isAdded()) {
            return;
        }

        final DroneSession session = this.session;
        final DroneStateAdapter state = getDroneState();
        if (session == null || state == null) {
            return;
        }

        final Location droneHomeLocation = state.getHomeLocation();
        if (session.isLocated() && droneHomeLocation != null) {
            int rotation = (int)(-mapView.getMapCamera().getHeading()) % 360;
            if (rotation < 0) {
                rotation += 360;
            }
            droneHomeIcon.setRotation(rotation);
            droneHomeIcon.setLocation(new Geopoint(droneHomeLocation.getLatitude(), droneHomeLocation.getLongitude()));
        }

        Location droneLocation = state.getLocation();
        if (session.isLocated() && droneLocation != null) {
            final DroneOffsets offsets = Dronelink.getInstance().droneOffsets;
            droneLocation = Convert.locationWithBearing(droneLocation, offsets.droneCoordinate.direction + Math.PI, offsets.droneCoordinate.magnitude);

            int rotation = (int)(-Convert.RadiansToDegrees(state.getOrientation().getYaw())) % 360;
            if (rotation < 0) {
                rotation += 360;
            }
            droneIcon.setRotation(rotation);
            droneIcon.setLocation(new Geopoint(positionAboveDroneTakeoffLocation(droneLocation, state.getAltitude()), droneTakeoffAltitudeReferenceSystem));
            addPositionAboveDroneTakeoffLocation(droneSessionPositions, droneLocation, state.getAltitude());
        }

        if ((missionExecutor != null && missionExecutor.isEngaged()) || droneSessionPositions.size() == 0) {
            if (droneSessionPolyline != null) {
                droneLayer.getElements().remove(droneSessionPolyline);
            }
            droneSessionPolyline = null;
        }
        else {
            if (droneSessionPolyline == null) {
                droneSessionPolyline = new MapPolyline();
                droneSessionPolyline.setStrokeColor(Color.argb((int)(255 * 0.95), 255, 255, 255));
                droneSessionPolyline.setStrokeWidth(1);
                droneLayer.getElements().add(droneSessionPolyline);
            }

            if (droneSessionPolyline.getPath() == null || droneSessionPolyline.getPath().size() != droneSessionPositions.size()) {
                droneSessionPolyline.setPath(new Geopath(droneSessionPositions, droneTakeoffAltitudeReferenceSystem));
            }
        }

        if (droneMissionExecutedPositions.size() == 0) {
            if (droneMissionExecutedPolyline != null) {
                droneLayer.getElements().remove(droneMissionExecutedPolyline);
            }
            droneMissionExecutedPolyline = null;
        }
        else {
            if (droneMissionExecutedPolyline == null) {
                droneMissionExecutedPolyline = new MapPolyline();
                droneMissionExecutedPolyline.setStrokeColor(Color.argb((int)(255 * 1.0), 245, 0, 87));
                droneMissionExecutedPolyline.setStrokeWidth(2);
                droneLayer.getElements().add(droneMissionExecutedPolyline);
            }

            if (droneMissionExecutedPolyline.getPath() == null || droneMissionExecutedPolyline.getPath().size() != droneMissionExecutedPositions.size()) {
                droneMissionExecutedPolyline.setPath(new Geopath(droneMissionExecutedPositions, droneTakeoffAltitudeReferenceSystem));
            }
        }

        if (session.isLocated() && droneLocation != null) {
            MapScene trackingScene = null;
            final Tracking trackingResolved = !sessionLocationCentered && tracking == Tracking.NONE && missionExecutor == null ? Tracking.THIRD_PERSON_OBLIQUE : tracking;
            switch (trackingResolved) {
                case NONE:
                    break;

                case THIRD_PERSON_NADIR:
                    trackingScene = MapScene.createFromLocationAndRadius(
                        new Geopoint(droneLocation.getLatitude(), droneLocation.getLongitude()),
                        Math.max(20, state.getAltitude() / 2.0));
                    break;

                case THIRD_PERSON_OBLIQUE:
                    trackingScene = MapScene.createFromCamera(
                            new MapCamera(
                                new Geopoint(positionAboveDroneTakeoffLocation(Convert.locationWithBearing(droneLocation, state.getOrientation().getYaw() + Math.PI, 15), state.getAltitude() + 14),
                                        droneTakeoffAltitudeReferenceSystem),
                                    Convert.RadiansToDegrees(state.getOrientation().getYaw()), 45));
                    break;

                case FIRST_PERSON:
                    final DatedValue<GimbalStateAdapter> gimbalState = session.getGimbalState(0);
                    trackingScene = MapScene.createFromCamera(
                            new MapCamera(
                                    new Geopoint(positionAboveDroneTakeoffLocation(droneLocation, state.getAltitude()), droneTakeoffAltitudeReferenceSystem),
                                    Convert.RadiansToDegrees(state.getOrientation().getYaw()), Math.min((gimbalState == null ? 0 : Convert.RadiansToDegrees(gimbalState.value.getOrientation().getPitch())) + 90, 90)));
                    break;
            }

            if (trackingScene != null) {
                //linear causes a memory leak right now:
                //https://stackoverflow.com/questions/10122570/replace-a-fragment-programmatically
                //mapView.setScene(trackingScene, trackingPrevious == tracking ? MapAnimationKind.LINEAR : MapAnimationKind.NONE);
                mapView.setScene(trackingScene, MapAnimationKind.NONE);
                trackingPrevious = tracking;
            }

            sessionLocationCentered = true;
        }
    }

    private void updateMissionElements() {
        missionLayer.getElements().clear();

        final MissionExecutor missionExecutor = this.missionExecutor;
        if (missionExecutor == null) {
            return;
        }

        final MissionExecutor.TakeoffArea requiredTakeoffArea = missionExecutor.requiredTakeoffArea;
        if (requiredTakeoffArea != null) {
            final MapPolygon polygon = new MapPolygon();
            polygon.setStrokeColor(Color.argb((int)(255 * 0.5), 255, 171, 64));
            polygon.setStrokeWidth(1);
            polygon.setFillColor(Color.argb((int)(255 * 0.25), 255, 145, 0));
            final List<Geocircle> shapes = new ArrayList<>();
            shapes.add(new Geocircle(position(requiredTakeoffArea.coordinate.getLocation()), requiredTakeoffArea.distanceTolerance.horizontal));
            polygon.setShapes(shapes);
            missionLayer.getElements().add(polygon);
        }

        if (missionExecutor.restrictionZones != null && missionExecutor.restrictionZoneBoundaryCoordinates != null) {
            for (int i = 0; i < missionExecutor.restrictionZones.length; i++) {
                final PlanRestrictionZone restrictionZone = missionExecutor.restrictionZones[i];
                final GeoCoordinate[] coordinates = i < missionExecutor.restrictionZoneBoundaryCoordinates.length ? missionExecutor.restrictionZoneBoundaryCoordinates[i] : null;
                if (coordinates == null) {
                    continue;
                }

                final MapPolygon polygon = new MapPolygon();
                polygon.setStrokeColor(DronelinkUI.parseHexColor(restrictionZone.zone.color, Color.argb((int)(255 * 0.7), 255, 23, 68), "B4"));
                polygon.setStrokeWidth(1);
                polygon.setFillColor(DronelinkUI.parseHexColor(restrictionZone.zone.color, Color.argb((int)(255 * 0.5), 255, 23, 68), "7F"));

                switch (restrictionZone.zone.shape) {
                    case CIRCLE:
                        final GeoCoordinate center = coordinates[0];
                        final double radius = center.getLocation().distanceTo(coordinates[1].getLocation());
                        final List<Geocircle> shapes = new ArrayList<>();
                        shapes.add(new Geocircle(positionAboveDroneTakeoffLocation(center.getLocation(), restrictionZone.zone.minAltitude.value), radius));
                        polygon.setShapes(shapes);
                        break;

                    case POLYGON:
                        final List<Geopath> paths = new ArrayList<>();
                        final List<Geoposition> positions = new ArrayList<>();
                        for (int c = 0; c < coordinates.length; c++) {
                            positions.add(positionAboveDroneTakeoffLocation(coordinates[c].getLocation(), restrictionZone.zone.minAltitude.value));
                        }
                        paths.add(new Geopath(positions, droneTakeoffAltitudeReferenceSystem));
                        polygon.setPaths(paths);
                        break;
                }
                missionLayer.getElements().add(polygon);
            }
        }

        final MissionExecutor.Estimate estimate = missionExecutor.getEstimate();
        if (estimate == null) {
            return;
        }

        List<Geoposition> positions = new ArrayList<>();
        for (final GeoSpatial spatial : estimate.spatials) {
            addPositionAboveDroneTakeoffLocation(positions, spatial.coordinate.getLocation(), spatial.altitude.value, 0.1);
        }

        if (positions.size() > 0) {
            final MapPolyline polyline = new MapPolyline();
            polyline.setStrokeColor(Color.argb((int)(255 * 0.73), 0, 229, 255));
            polyline.setStrokeWidth(1);
            polyline.setPath(new Geopath(positions, droneTakeoffAltitudeReferenceSystem));
            missionLayer.getElements().add(polyline);
        }

        updateMissionReengagementElements();
    }

    private void updateMissionReengagementElements() {
        missionReengagementLayer.getElements().clear();

        final MissionExecutor missionExecutor = this.missionExecutor;
        if (missionExecutor == null) {
            return;
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

        if (reengaging && reengagementEstimateSpatials != null && reengagementEstimateSpatials.length > 0) {
            List<Geoposition> positions = new ArrayList<>();
            for (final GeoSpatial spatial : reengagementEstimateSpatials) {
                addPositionAboveDroneTakeoffLocation(positions, spatial.coordinate.getLocation(), spatial.altitude.value, 0.1);
            }

            if (positions.size() > 0) {
                final MapPolyline polyline = new MapPolyline();
                polyline.setStrokeColor(Color.argb((int) (255 * 1.0), 224, 64, 251));
                polyline.setStrokeWidth(1);
                polyline.setPath(new Geopath(positions, droneTakeoffAltitudeReferenceSystem));
                missionReengagementLayer.getElements().add(polyline);
            }
        }

        GeoSpatial reengagementSpatial = null;
        if (reengaging) {
            if (reengagementEstimateSpatials != null && reengagementEstimateSpatials.length > 0) {
                reengagementSpatial = reengagementEstimateSpatials[reengagementEstimateSpatials.length - 1];
            }
        }
        else {
            reengagementSpatial = missionExecutor.getReengagementSpatial();
        }

        if (reengagementSpatial != null && (!engaged || reengaging)) {
            final MapIcon reengagementIcon = new MapIcon();
            reengagementIcon.setImage(new MapImage(BitmapFactory.decodeResource(MicrosoftMapFragment.this.getResources(), R.drawable.drone_reengagement)));
            reengagementIcon.setFlat(true);
            reengagementIcon.setDesiredCollisionBehavior(MapElementCollisionBehavior.REMAIN_VISIBLE);
            reengagementIcon.setLocation(new Geopoint(positionAboveDroneTakeoffLocation(reengagementSpatial.coordinate.getLocation(), reengagementSpatial.altitude.value), droneTakeoffAltitudeReferenceSystem));
            missionReengagementLayer.getElements().add(reengagementIcon);
        }
    }

    private void updateFuncElements() {
        int iconIndex = 0;
        int inputIndex = 0;
        GeoSpatial mapCenterSpatial = null;
        FuncExecutor funcExecutor = this.funcExecutor;
        if (funcExecutor != null) {
            while (true) {
                final FuncInput input = funcExecutor.getInput(inputIndex);
                if (input == null) {
                    break;
                }

                if (input.variable.valueType == VariableValueType.DRONE) {
                    Object value = funcExecutor.readValue(inputIndex);
                    if (value != null) {
                        GeoSpatial[] spatials = new GeoSpatial[]{};
                        if (value instanceof GeoSpatial[]) {
                            spatials = (GeoSpatial[])value;
                            if (spatials.length > 0) {
                                mapCenterSpatial = spatials[spatials.length - 1];
                            }
                        }
                        else if (value instanceof GeoSpatial) {
                            spatials = new GeoSpatial[] { (GeoSpatial)value };
                            mapCenterSpatial = (GeoSpatial)value;
                        }

                        for (int variableValueIndex = 0; variableValueIndex < spatials.length; variableValueIndex++) {
                            MapIcon inputIcon;
                            if (funcInputDroneIcons.size() < iconIndex) {
                                inputIcon = funcInputDroneIcons.get(iconIndex);
                            }
                            else {
                                inputIcon = new MapIcon();
                                inputIcon.setImage(new MapImage(BitmapFactory.decodeResource(MicrosoftMapFragment.this.getResources(), R.drawable.func_input_drone)));
                                inputIcon.setFlat(true);
                                inputIcon.setDesiredCollisionBehavior(MapElementCollisionBehavior.REMAIN_VISIBLE);
                                inputIcon.setFlyout(new MapFlyout());
                                funcInputDroneIcons.add(inputIcon);
                            }

                            final GeoSpatial spatial = spatials[variableValueIndex];
                            inputIcon.setLocation(new Geopoint(positionAboveDroneTakeoffLocation(spatial.coordinate.getLocation(), spatial.altitude.value), droneTakeoffAltitudeReferenceSystem));
                            inputIcon.getFlyout().setTitle((inputIndex + 1) + ". " + (input.descriptors.name == null ? " " : input.descriptors.name));
                            final Object valueFormatted = funcExecutor.readValue(inputIndex, variableValueIndex, true);
                            if (valueFormatted instanceof String) {
                                inputIcon.getFlyout().setDescription(valueFormatted + (value instanceof GeoSpatial[] ? (((GeoSpatial[])value).length > 1 ? " (" + (variableValueIndex + 1) + ")" : "") : ""));
                            }
                            else {
                                inputIcon.getFlyout().setDescription("");
                            }

                            iconIndex++;
                        }
                    }
                }
                inputIndex++;
            }
        }

        while (iconIndex < funcInputDroneIcons.size()) {
            funcInputDroneIcons.remove(funcInputDroneIcons.size() - 1);
        }

        while (funcLayer.getElements().size() > funcInputDroneIcons.size()) {
            funcLayer.getElements().remove(funcLayer.getElements().size() - 1);
        }

        while (funcLayer.getElements().size() < funcInputDroneIcons.size()) {
            funcLayer.getElements().add(funcInputDroneIcons.get(funcLayer.getElements().size()));
        }

        if (mapCenterSpatial != null && tracking == Tracking.NONE) {
            mapView.setScene(MapScene.createFromLocationAndRadius(
                    new Geopoint(mapCenterSpatial.coordinate.latitude, mapCenterSpatial.coordinate.longitude),
                    18), MapAnimationKind.NONE);
        }

        funcMapOverlaysLayer.getElements().clear();

        if (funcExecutor != null) {
            final FuncMapOverlay[] mapOverlays = funcExecutor.getMapOverlays(session, new FuncExecutor.FuncExecuteError() {
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
                    final MapPolygon polygon = new MapPolygon();
                    polygon.setStrokeColor(DronelinkUI.parseHexColor(mapOverlay.color, Color.argb((int)(255 * 0.7), 255, 23, 68), "B4"));
                    polygon.setStrokeWidth(1);
                    polygon.setFillColor(DronelinkUI.parseHexColor(mapOverlay.color, Color.argb((int)(255 * 0.5), 255, 23, 68), "7F"));
                    final List<Geopath> paths = new ArrayList<>();
                    final List<Geoposition> positions = new ArrayList<>();
                    for (int c = 0; c < mapOverlay.coordinates.length; c++) {
                        positions.add(positionAboveDroneTakeoffLocation(mapOverlay.coordinates[c].getLocation(), 0));
                    }
                    paths.add(new Geopath(positions, droneTakeoffAltitudeReferenceSystem));
                    polygon.setPaths(paths);
                    funcMapOverlaysLayer.getElements().add(polygon);
                }
            }
        }
    }

    private void updateModeElements() {
        final ModeExecutor modeExecutorLocal = modeExecutor;
        if (modeExecutorLocal == null || !modeExecutorLocal.isEngaged()) {
            return;
        }

        final GeoSpatial modeTarget = modeExecutorLocal.getTarget();
        if (modeTarget != null) {
            int rotation = (int)(-Convert.RadiansToDegrees(modeTarget.orientation.getYaw())) % 360;
            if (rotation < 0) {
                rotation += 360;
            }
            modeTargetIcon.setRotation(rotation);
            modeTargetIcon.setLocation(new Geopoint(positionAboveDroneTakeoffLocation(modeTarget.coordinate.getLocation(), modeTarget.altitude.value), droneTakeoffAltitudeReferenceSystem));
            modeTargetIcon.setVisible(true);
        }
        else {
            modeTargetIcon.setVisible(false);
        }

        final List<Geoposition> visibleCoordinates = new LinkedList<>();
        final GeoCoordinate[] modeVisibleCoordinates = modeExecutorLocal.getVisibleCoordinates();
        if (modeVisibleCoordinates != null) {
            for (final GeoCoordinate coordinate : modeVisibleCoordinates) {
                visibleCoordinates.add(new Geoposition(coordinate.latitude, coordinate.longitude));
            }
        }

        if (visibleCoordinates.size() > 0) {
            tracking = Tracking.NONE;
            final GeoboundingBox boundingBox = new GeoboundingBox(visibleCoordinates);
            final Location northwest = location(boundingBox.getNorthwestCorner());
            final Location southeast = location(boundingBox.getSoutheastCorner());
            double radius = northwest.distanceTo(southeast) * 0.5;
            final Location center = Convert.locationWithBearing(northwest, Convert.DegreesToRadians(northwest.bearingTo(southeast)), radius);
            //using the bounding box directly isn't great
            mapView.setScene(MapScene.createFromLocationAndRadius(new Geopoint(position(center)), radius * 2.0, 0.0, 0.0), MapAnimationKind.NONE);
        }
    }

    private void updateDroneTakeoffAltitude() {
        Geopoint point = null;
        final DroneStateAdapter state = getDroneState();
        if (state != null) {
            final Location takeoffLocation = state.getTakeoffLocation();
            if (takeoffLocation != null) {
                point = new Geopoint(takeoffLocation.getLatitude(), takeoffLocation.getLongitude());
            }
        }

        if (point == null && missionExecutor != null) {
            point = new Geopoint(missionExecutor.takeoffCoordinate.latitude, missionExecutor.takeoffCoordinate.longitude);
        }

        if (point == null) {
            droneTakeoffAltitude = 0;
            droneTakeoffAltitudeReferenceSystem = AltitudeReferenceSystem.SURFACE;
            return;
        }

        droneTakeoffAltitude = -point.toAltitudeReferenceSystem(AltitudeReferenceSystem.GEOID, mapView).getPosition().getAltitude();
        droneTakeoffAltitudeReferenceSystem = AltitudeReferenceSystem.GEOID;
    }

    private Geoposition positionAboveDroneTakeoffLocation(final Location coordinate, final double altitude) {
        return new Geoposition(coordinate.getLatitude(), coordinate.getLongitude(), droneTakeoffAltitude + altitude);
    }

    private Geoposition addPositionAboveDroneTakeoffLocation(final List<Geoposition> positions, final Location coordinate, final double altitude) {
        return addPositionAboveDroneTakeoffLocation(positions, coordinate, altitude, 0.5);
    }

    private Geoposition addPositionAboveDroneTakeoffLocation(final List<Geoposition> positions, final Location coordinate, final double altitude, final double tolerance) {
        Geoposition position = positionAboveDroneTakeoffLocation(coordinate, altitude);
        if (positions.size() > 0) {
            Geoposition lastPosition = positions.get(positions.size() - 1);
            final double distance = location(lastPosition).distanceTo(location(position));
            if (Math.abs(lastPosition.getAltitude() - position.getAltitude()) < tolerance && distance < tolerance) {
                return lastPosition;
            }

            //kluge: microsoft maps has a bug / optimization that refuses to render coordinates that are very close, even if the altitude is different, so trick it
            if (distance <= 0.01) {
                position = positionAboveDroneTakeoffLocation(Convert.locationWithBearing(coordinate, 0, 0.011), altitude);
            }
        }

        positions.add(position);
        return position;
    }

    private void updateScene() {
        updateScene(SCENE_ELEMENTS_STANDARD, MapAnimationKind.LINEAR);
    }

    private void updateScene(final int elements, final MapAnimationKind animation) {
        if (tracking != Tracking.NONE) {
            return;
        }

        final List<Geoposition> positions = new ArrayList<>();

        if ((elements & SCENE_ELEMENT_USER) == SCENE_ELEMENT_USER) {
            final Location location = Dronelink.getInstance().getLocation();
            if (location != null) {
                positions.add(position(location));
            }
        }

        final DroneSession session = this.session;
        final DroneStateAdapter state = getDroneState();
        if (session != null && state != null) {
            if ((elements & SCENE_ELEMENT_DRONE_CURRENT) == SCENE_ELEMENT_DRONE_CURRENT) {
                final Location location = state.getLocation();
                if (session.isLocated() && location != null) {
                    positions.add(position(location));
                }
            }

            if ((elements & SCENE_ELEMENT_DRONE_HOME) == SCENE_ELEMENT_DRONE_HOME) {
                final Location location = state.getHomeLocation();
                if (session.isLocated() && location != null) {
                    positions.add(position(location));
                }
            }

            if ((elements & SCENE_ELEMENT_DRONE_TAKEOFF) == SCENE_ELEMENT_DRONE_TAKEOFF) {
                final Location location = state.getTakeoffLocation();
                if (session.isLocated() && location != null) {
                    positions.add(position(location));
                }
            }
        }

        final MissionExecutor missionExecutor = this.missionExecutor;
        if (missionExecutor != null) {
            if ((elements & SCENE_ELEMENT_MISSION_TAKEOFF) == SCENE_ELEMENT_MISSION_TAKEOFF) {
                positions.add(position(missionExecutor.takeoffCoordinate.getLocation()));
            }

            final MissionExecutor.Estimate estimate = missionExecutor.getEstimate();
            if (estimate != null) {
                if ((elements & SCENE_ELEMENT_MISSION_MAIN) == SCENE_ELEMENT_MISSION_MAIN) {
                    for (final GeoSpatial spatial : estimate.spatials) {
                        positions.add(position(spatial.coordinate.getLocation()));
                    }
                }

                if ((elements & SCENE_ELEMENT_MISSION_REENGAGEMENT) == SCENE_ELEMENT_MISSION_REENGAGEMENT) {
                    for (final GeoSpatial spatial : estimate.reengagementSpatials) {
                        positions.add(position(spatial.coordinate.getLocation()));
                    }
                }
            }
        }

        if (positions.size() > 0) {
            final GeoboundingBox boundingBox = new GeoboundingBox(positions);
            final Location northwest = location(boundingBox.getNorthwestCorner());
            final Location southeast = location(boundingBox.getSoutheastCorner());
            double radius = northwest.distanceTo(southeast) * 0.5;
            final Location center = Convert.locationWithBearing(northwest, Convert.DegreesToRadians(northwest.bearingTo(southeast)), radius);
            if (positions.size() < 10 && radius < 100) {
                radius = 100;
            }

            //using the bounding box directly isn't great
            final Location bottom = Convert.locationWithBearing(center, 0, radius * 3.15);
            mapView.setScene(MapScene.createFromLocationAndRadius(new Geopoint(position(bottom)), radius * 2.0, 0.0, 65.0), MapAnimationKind.NONE);
            //ignoring animation because of mem leak
            //mapView.setScene(MapScene.createFromLocationAndRadius(new Geopoint(position(center)), radius), animation);
        }
    }

    private Location location(final Geoposition position) {
        final Location location = new Location("");
        location.setLatitude(position.getLatitude());
        location.setLongitude(position.getLongitude());
        return location;
    }

    private Geoposition position(final Location location) {
        return new Geoposition(location.getLatitude(), location.getLongitude());
    }

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
                tracking = Tracking.THIRD_PERSON_NADIR;
                break;

            case DRONE_HEADING:
                tracking = Tracking.THIRD_PERSON_NADIR;
                break;
        }
    }

    @Override
    public void onRegistered(final String error) {}

    @Override
    public void onMissionLoaded(final MissionExecutor executor) {
        missionExecutor = executor;
        executor.addListener(this);

        applyUserInterfaceSettings(executor.userInterfaceSettings);

        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateScene();
                    updateDroneTakeoffAltitude();
                    if (executor.isEstimated()) {
                        updateMissionElements();
                    }
                }
            });
        }
    }

    @Override
    public void onMissionUnloaded(final MissionExecutor executor) {
        droneMissionExecutedPositions.clear();
        missionExecutor = null;
        executor.removeListener(this);

        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateMissionElements();
                }
            });
        }
    }

    @Override
    public void onFuncLoaded(final FuncExecutor executor) {
        funcExecutor = executor;
        executor.addListener(this);

        applyUserInterfaceSettings(executor.getUserInterfaceSettings());

        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateFuncElements();
                }
            });
        }
    }

    @Override
    public void onFuncUnloaded(final FuncExecutor executor) {
        funcExecutor = null;
        executor.removeListener(this);

        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateFuncElements();
                }
            });
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
    public void onOpened(final DroneSession session) {
        this.session = session;
        sessionLocationCentered = false;
        session.addListener(this);
    }

    @Override
    public void onClosed(final DroneSession session) {
        droneSessionPositions.clear();
        this.session = null;
        session.removeListener(this);
    }

    @Override
    public void onInitialized(final DroneSession session) {}

    @Override
    public void onLocated(final DroneSession session) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateScene();
                    //wait 2 seconds to give the map time to load the elevation data
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            updateMissionElements();
                        }
                    }, 2000);
                }
            });
        }
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
    public void onMissionEstimating(final MissionExecutor executor) {}

    @Override
    public void onMissionEstimated(final MissionExecutor executor, final MissionExecutor.Estimate estimate) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateScene();
                    updateMissionElements();
                }
            });
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
        final DroneStateAdapter state = getDroneState();
        if (state != null) {
            final Location location = state.getLocation();
            if (location != null) {
                addPositionAboveDroneTakeoffLocation(droneMissionExecutedPositions, location, state.getAltitude());
            }

            if ((missionReengagementLayer.getElements().size() == 0 && executor.isReengaging()) || (missionReengagementLayer.getElements().size() > 0 && !executor.isReengaging())) {
                final Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            updateMissionReengagementElements();
                        }
                    });
                }
            }
        }
    }

    @Override
    public void onMissionDisengaged(final MissionExecutor executor, final MissionExecutor.Engagement engagement, final Message reason) {
        droneMissionExecutedPositions.clear();
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateMissionElements();
                }
            });
        }
    }

    @Override
    public void onMissionUpdatedDisconnected(final MissionExecutor executor, final MissionExecutor.Engagement engagement) {
        droneMissionExecutedPositions.clear();
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateMissionElements();
                }
            });
        }
    }

    @Override
    public void onFuncInputsChanged(final FuncExecutor executor) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateFuncElements();
                }
            });
        }
    }

    @Override
    public void onFuncExecuted(final FuncExecutor executor) {}

    @Override
    public Message[] modeEngageDisallowedReasons(final ModeExecutor executor) {
        return null;
    }

    @Override
    public void onModeEngaging(final ModeExecutor executor) {}

    @Override
    public void onModeEngaged(final ModeExecutor executor, final ModeExecutor.Engagement engagement) {}

    @Override
    public void onModeExecuted(final ModeExecutor executor, final ModeExecutor.Engagement engagement) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateModeElements();
                }
            });
        }
    }

    @Override
    public void onModeDisengaged(final ModeExecutor executor, final ModeExecutor.Engagement engagement, final Message reason) {
        final Activity activity = getActivity();
        if (activity != null) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateModeElements();
                }
            });
        }
    }
}
