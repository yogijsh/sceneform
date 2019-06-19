/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.appoly.sceneform_example;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.mapbox.android.core.location.LocationEngine;
import com.mapbox.android.core.location.LocationEngineListener;
import com.mapbox.android.core.location.LocationEnginePriority;
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.api.directions.v5.DirectionsCriteria;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.CameraMode;
import com.mapbox.mapboxsdk.plugins.locationlayer.modes.RenderMode;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncherOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import uk.co.appoly.arcorelocation.LocationMarker;
import uk.co.appoly.arcorelocation.LocationScene;
import uk.co.appoly.arcorelocation.rendering.LocationNode;
import uk.co.appoly.arcorelocation.rendering.LocationNodeRender;
import uk.co.appoly.arcorelocation.utils.ARLocationPermissionHelper;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore and Sceneform APIs.
 */
public class LocationActivity extends AppCompatActivity implements OnMapReadyCallback, LocationEngineListener,
        PermissionsListener, MapboxMap.OnMapClickListener {
    private boolean installRequested;
    private boolean hasFinishedLoading = false;
    
    private Snackbar loadingMessageSnackbar = null;

    private ArSceneView arSceneView;

    // Renderables for this example
    private ModelRenderable andyRenderable;
    private ViewRenderable exampleLayoutRenderable;

    // Our ARCore-Location scene
    private LocationScene locationScene;

    List<Float> fLat = new ArrayList<Float>();
    List<Float> fLon = new ArrayList<Float>();

    private double finalLat = 0.0;
    private double finalLon = 0.0;

    private int setMarkerCounter = 0;

    private MapView mapView;
    private MapboxMap map;
    private PermissionsManager permissionsManager;
    private LocationEngine locationEngine;
    private LocationLayerPlugin locationLayerPlugin;
    private Location originLocation;
    private Point originPosition;
    private Point destinationPosition;
    private Marker destinationMarker;
    private NavigationMapRoute navigationMapRoute;
    private static final String TAG = "MainActivity";
    private DirectionsRoute currentRoute;

    private NavigationLauncherOptions options;


    private LatLng mypoint = new LatLng();

    public class mapCoordinates {
        public final double latitude;
        public final double longitude;

        public mapCoordinates(double lat, double lon) {
            latitude = lat;
            longitude = lon;
        }
    }

    private Button startButton;

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    // CompletableFuture requires api level 24
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Checks for write and read permissions to storage
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
        }

        //License from Mapbox to be able to load the map
        Mapbox.getInstance(this, "pk.eyJ1IjoieW9naWpzaCIsImEiOiJjandqN3Q5NDEwMnlnM3lvZTVwaG10bWh3In0.DH-WnRFP49wzW-KR5hxnaQ");
        setContentView(R.layout.activity_sceneform);


        //Creates the mapView and the arSceneView
        setContentView(R.layout.activity_sceneform);
        arSceneView = findViewById(R.id.ar_scene_view);

        mapView = (MapView) findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);

        //Startbutton for the turn-by-turn navigation
        startButton = findViewById(R.id.startButton);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Launch navigation UI
                NavigationLauncherOptions options = NavigationLauncherOptions.builder()
                        .directionsProfile(DirectionsCriteria.PROFILE_WALKING) //change here for car navigation
                        .directionsRoute(currentRoute)
                        .shouldSimulateRoute(false) //For simulation set to true /////////////////////////////////////////
                        .build();


                // Call this method with Context from within an Activity
                NavigationLauncher.startNavigation(LocationActivity.this, options);
            }
        });



        // Build a renderable from a 2D View.
        CompletableFuture<ViewRenderable> exampleLayout =
                ViewRenderable.builder()
                        .setView(this, R.layout.example_layout)
                        .build();

        // When you build a Renderable, Sceneform loads its resources in the background while returning
        // a CompletableFuture. Call thenAccept(), handle(), or check isDone() before calling get().
        CompletableFuture<ModelRenderable> andy = ModelRenderable.builder()
                .setSource(this, R.raw.andy)
                .build();


        CompletableFuture.allOf(
                exampleLayout,
                andy)
                .handle(
                        (notUsed, throwable) -> {
                            // When you build a Renderable, Sceneform loads its resources in the background while
                            // returning a CompletableFuture. Call handle(), thenAccept(), or check isDone()
                            // before calling get().

                            if (throwable != null) {
                                DemoUtils.displayError(this, "Unable to load renderables", throwable);
                                return null;
                            }

                            try {
                                exampleLayoutRenderable = exampleLayout.get();
                                andyRenderable = andy.get();
                                hasFinishedLoading = true;

                            } catch (InterruptedException | ExecutionException ex) {
                                DemoUtils.displayError(this, "Unable to load renderables", ex);
                            }

                            return null;
                        });

        // Set an update listener on the Scene that will hide the loading message once a Plane is
        // detected.
        arSceneView
                .getScene()
                .setOnUpdateListener(
                        frameTime -> {
                            if (!hasFinishedLoading) {
                                return;
                            }

                            if (locationScene == null) {

                                //------------------------------------------------------------------------------------------

                                // Setting up the locationscene where the marker will be shown
                                // We know that here, the AR components have been initiated.
                                locationScene = new LocationScene(this, this, arSceneView);

                                BufferedReader reader;
                                File sdcard = Environment.getExternalStorageDirectory();
                                // to this path add a new directory path
                                File dir = new File(sdcard.getAbsolutePath());
                                // create this directory if not already created
                                dir.mkdir();
                                // create the file in which we will write the contents
                                File file = new File(dir, "/arLocation/3/assets/coordinates.txt");
                                //final File file = new File("/sdcard/text.txt");
                                FileInputStream is = null;
                                try {
                                    is = new FileInputStream(file);
                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                }

                                if (file.exists()) {

                                    reader = new BufferedReader(new InputStreamReader(is));
                                    String line = null;
                                    try {
                                        line = reader.readLine();
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                    int arrayCounter = 0;
                                    String lat ="";
                                    String lon = "";
                                    while(line != null){
                                        Log.d("StackOverflow", line);


                                        if(line != null){
                                            String tmpLat = line.substring(0, line.indexOf(";"));
                                            String tmpLon = line.substring(line.indexOf(";") + 1, line.length());
                                            if(!lat.equals(tmpLat) && !lon.equals(tmpLon))
                                            {
                                                lat = line.substring(0, line.indexOf(";"));
                                                lon = line.substring(line.indexOf(";") + 1, line.length());

                                                //Add the coordinates to the List for the destination Marker
                                                fLat.add(Float.parseFloat(lat));
                                                fLon.add(Float.parseFloat(lon));
                                            }
                                        }

                                        try {
                                            line = reader.readLine();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }

                                    }
                                }

                                createMarker();




                                //----------------------------------------------------------------------------------------

                                // Adding a simple location marker of a 3D model
                                /*locationScene.mLocationMarkers.add(
                                        new LocationMarker(
                                                9.227179,
                                                48.464133,
                                                getAndy()));*/
                            }

                            Frame frame = arSceneView.getArFrame();
                            if (frame == null) {
                                return;
                            }

                            if (frame.getCamera().getTrackingState() != TrackingState.TRACKING) {
                                return;
                            }

                            if (locationScene != null) {
                                locationScene.processFrame(frame);
                            }

                            if (loadingMessageSnackbar != null) {
                                for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
                                    if (plane.getTrackingState() == TrackingState.TRACKING) {
                                        hideLoadingMessage();
                                    }
                                }
                            }
                        });


        // Lastly request CAMERA & fine location permission which is required by ARCore-Location.
        ARLocationPermissionHelper.requestPermission(this);
    }
    /**
     * Example node of a layout
     *Show the node for the marker with the distance of the destination
     * @return
     */
    private Node getExampleView() {
        Node base = new Node();
        base.setRenderable(exampleLayoutRenderable);
        Context c = this;
        // Add  listeners etc here
        View eView = exampleLayoutRenderable.getView();
        eView.setOnTouchListener((v, event) -> {
            Toast.makeText(
                    c, "Location marker touched.", Toast.LENGTH_LONG)
                    .show();
            return false;
        });

        return base;
    }

    //Creates the marker that show the distance for the final coordinate
    private void createMarker()
    {
        LocationMarker layoutLocationMarker = new LocationMarker(
                fLon.get(fLon.size()-1),
                fLat.get(fLat.size()-1),
                getExampleView()

        );

        finalLon = fLon.get(fLon.size()-1);
        finalLat = fLat.get(fLat.size()-1);

        View eView = exampleLayoutRenderable.getView();
        TextView finalDistance = findViewById(R.id.textView4);


        // An example "onRender" event, called every frame
        // Updates the layout with the markers distance
        layoutLocationMarker.setRenderEvent(new LocationNodeRender() {
            @Override
            public void render(LocationNode node) {
                View eView = exampleLayoutRenderable.getView();

                TextView distanceTextView = eView.findViewById(R.id.textView2);
                distanceTextView.setText(node.getDistance() + "m");
            }
        });
        // Adding the marker
        locationScene.mLocationMarkers.add(layoutLocationMarker);

    }

    //Creates an invisible second View
    private Node getExampleView2() {
        Node base = new Node();

        return base;
    }

    /***
     * Example Node of a 3D model was used for testing
     *
     * @return
     */
    private Node getAndy() {
        Node base = new Node();
        base.setRenderable(andyRenderable);
        Context c = this;
        base.setOnTapListener((v, event) -> {
            Toast.makeText(
                    c, "Andy touched.", Toast.LENGTH_LONG)
                    .show();
        });
        return base;
    }

    /**
     * Make sure we call locationScene.resume();
     */
    @Override
    protected void onResume() {
        super.onResume();


        if (locationScene != null) {
            locationScene.resume();
        }

        if (arSceneView.getSession() == null) {
            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                Session session = DemoUtils.createArSession(this, installRequested);
                if (session == null) {
                    installRequested = ARLocationPermissionHelper.hasPermission(this);
                    return;
                } else {
                    arSceneView.setupSession(session);
                }
            } catch (UnavailableException e) {
                DemoUtils.handleSessionException(this, e);
            }
        }

        try {
            arSceneView.resume();
        } catch (CameraNotAvailableException ex) {
            DemoUtils.displayError(this, "Unable to get camera", ex);
            finish();
            return;
        }

        if (arSceneView.getSession() != null) {
            showLoadingMessage();
        }

        mapView.onResume();
    }

    /**
     * Make sure we call locationScene.pause();
     */
    @Override
    public void onPause() {
        super.onPause();

        if (locationScene != null) {
            locationScene.pause();
        }

        arSceneView.pause();
        mapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        arSceneView.destroy();

        if(locationEngine != null){
            locationEngine.deactivate();
        }
        mapView.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        if (!ARLocationPermissionHelper.hasPermission(this)) {
            if (!ARLocationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                ARLocationPermissionHelper.launchPermissionSettings(this);
            } else {
                Toast.makeText(
                        this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                        .show();
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void showLoadingMessage() {
        if (loadingMessageSnackbar != null && loadingMessageSnackbar.isShownOrQueued()) {
            return;
        }

        loadingMessageSnackbar =
                Snackbar.make(
                        LocationActivity.this.findViewById(android.R.id.content),
                        R.string.plane_finding,
                        Snackbar.LENGTH_INDEFINITE);
        loadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
        loadingMessageSnackbar.show();
    }

    private void hideLoadingMessage() {
        if (loadingMessageSnackbar == null) {
            return;
        }

        loadingMessageSnackbar.dismiss();
        loadingMessageSnackbar = null;
    }

    //Opens a different application
    public static boolean openApp(Context context, String packageName) {
        PackageManager manager = context.getPackageManager();
        try {
            Intent i = manager.getLaunchIntentForPackage(packageName);
            if (i == null) {
                return false;
                //throw new ActivityNotFoundException();
            }
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            context.startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        }
    }

    //Once the Map was loaded it enables and searches for the Location of the device
    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        map = mapboxMap;
        map.addOnMapClickListener(this);
        enableLocation();
        //LatLng point = null;
        //setRoute(point);

    }

    //Checks if location permissions were granted and finds the current location
    private void enableLocation(){
        if(PermissionsManager.areLocationPermissionsGranted(this))
        {
            initializeLocationEngine();
            initializeLocationLayer();


        }else{
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    //Finds the location and calls the camera setter to point the map to the current location
    @SuppressWarnings("MissingPermission")
    private void initializeLocationEngine(){
        locationEngine = new LocationEngineProvider(this).obtainBestLocationEngineAvailable();
        locationEngine.setPriority(LocationEnginePriority.BALANCED_POWER_ACCURACY);
        locationEngine.activate();

        Location lastLocation = locationEngine.getLastLocation();
        if(lastLocation != null){
            originLocation = lastLocation;
            setCameraPosition(lastLocation);
        } else{
            locationEngine.addLocationEngineListener(this);
        }
    }

    //Locationlayer to track the current location
    @SuppressWarnings("MissingPermission")
    private void initializeLocationLayer(){
        locationLayerPlugin = new LocationLayerPlugin(mapView,map,locationEngine);
        locationLayerPlugin.setLocationLayerEnabled(true);
        locationLayerPlugin.setCameraMode(CameraMode.TRACKING);
        locationLayerPlugin.setRenderMode(RenderMode.COMPASS);

    }

    //Sets the camera position to the current location on the map
    private void setCameraPosition(Location location){
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(location.getLatitude(),
                location.getLongitude()),15.0));
    }

    @Override
    @SuppressWarnings("MissingPermission")
    public void onConnected() {
        locationEngine.requestLocationUpdates();
    }

    //Moves the camera every time the location changes
    @Override
    public void onLocationChanged(Location location) {
        if(location != null){
            originLocation = location;
            setCameraPosition(location);

            LatLng point = new LatLng();
            point.setLongitude(finalLon);
            point.setLatitude(finalLat);
            onMapClick(point);

            startButton.setVisibility(View.VISIBLE);


        }
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        //Present toast or dialog
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if(granted){
            enableLocation();
        }
    }

    //Starts the mapView the locationengine and the locationlayer
    @Override
    @SuppressWarnings("MissingPermission")
    public void onStart() {
        super.onStart();
        if(locationEngine != null){
            locationEngine.requestLocationUpdates();
        }
        if(locationLayerPlugin != null){
            locationLayerPlugin.onStart();
        }
        mapView.onStart();
    }

    //Stops the mapView the locationengine and the locationlayer
    @Override
    public void onStop() {
        super.onStop();
        if(locationEngine != null){
            locationEngine.removeLocationUpdates();
        }
        if(locationLayerPlugin != null){
            locationLayerPlugin.onStop();
        }

        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    //When the map is clicked it creates a route from the current location to the clicked destination
    @Override
    public void onMapClick(@NonNull LatLng point) {
        
        if (destinationMarker != null) {
            map.removeMarker(destinationMarker);
        }

        destinationMarker = map.addMarker(new MarkerOptions().position(point));

        destinationPosition = Point.fromLngLat(fLon.get(fLon.size()-1), fLat.get(fLon.size()-1));
        originPosition = Point.fromLngLat(originLocation.getLongitude(), originLocation.getLatitude());
        getRoute(originPosition, destinationPosition);

    }

    //Creates the current route from current location and destination
    private void getRoute(Point origin, Point destination){
        NavigationRoute.builder(this)
                .profile(DirectionsCriteria.PROFILE_WALKING) //Change Here for car navigation
                .accessToken(Mapbox.getAccessToken())
                .origin(origin)
                .destination(destination)
                .build()
                .getRoute(new Callback<DirectionsResponse>() {
                    @Override
                    public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
                        if(response.body() == null)
                        {
                            Log.e(TAG, "No routes found, check right user and access token");
                            return;
                        }else if (response.body().routes().size() == 0){
                            Log.e(TAG,"No routes found");
                            return;
                        }

                        currentRoute = response.body().routes().get(0);

                        if(navigationMapRoute != null){
                            navigationMapRoute.removeRoute();
                        }
                        else{
                            navigationMapRoute = new NavigationMapRoute(null,mapView,map);
                        }
                        navigationMapRoute.addRoute(currentRoute);
                    }

                    @Override
                    public void onFailure(Call<DirectionsResponse> call, Throwable t) {
                        Log.e(TAG, "Error:" + t.getMessage());
                    }
                });
    }


}
