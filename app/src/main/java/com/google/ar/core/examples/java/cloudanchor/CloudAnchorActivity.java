/*
 * Copyright 2019 Google LLC
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

package com.google.ar.core.examples.java.cloudanchor;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.InputType;
import android.util.ArraySet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.GuardedBy;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.google.android.filament.gltfio.Animator;
import com.google.android.filament.gltfio.FilamentAsset;
import com.google.ar.core.Anchor;
import com.google.ar.core.Anchor.CloudAnchorState;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.Config;
import com.google.ar.core.Config.CloudAnchorMode;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.examples.java.cloudanchor.PrivacyNoticeDialogFragment.HostResolveListener;
import com.google.ar.core.examples.java.cloudanchor.PrivacyNoticeDialogFragment.NoticeDialogListener;
import com.google.ar.core.examples.java.common.helpers.CameraPermissionHelper;
import com.google.ar.core.examples.java.common.helpers.DisplayRotationHelper;
import com.google.ar.core.examples.java.common.helpers.FullScreenHelper;
import com.google.ar.core.examples.java.common.helpers.SnackbarHelper;
import com.google.ar.core.examples.java.common.helpers.TrackingStateHelper;
import com.google.ar.core.examples.java.common.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.common.rendering.ObjectRenderer.BlendMode;
import com.google.ar.core.examples.java.common.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.common.rendering.PointCloudRenderer;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.NodeParent;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.SceneView;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.Material;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.ViewRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;
import com.google.common.base.Preconditions;
import com.google.firebase.database.DatabaseError;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class CloudAnchorActivity extends AppCompatActivity
        implements NoticeDialogListener {
    private static final String TAG = CloudAnchorActivity.class.getSimpleName();

    private enum HostResolveMode {
        NONE,
        HOSTING,
        RESOLVING,
    }

    private boolean installRequested;

    // Locks needed for synchronization
    private final Object singleTapLock = new Object();
    private final Object anchorLock = new Object();
    private final Object anchorsLock = new Object();

    // Tap handling and UI.
    private GestureDetector gestureDetector;
    private final SnackbarHelper snackbarHelper = new SnackbarHelper();
    private DisplayRotationHelper displayRotationHelper;
    private final TrackingStateHelper trackingStateHelper = new TrackingStateHelper(this);
    private Button hostButton;
    private Button resolveButton;
    private TextView roomCodeText;
    private SharedPreferences sharedPreferences;
    private static final String PREFERENCE_FILE_KEY = "allow_sharing_images";
    private static final String ALLOW_SHARE_IMAGES_KEY = "ALLOW_SHARE_IMAGES";

    @GuardedBy("singleTapLock")
    private MotionEvent queuedSingleTap;
    @GuardedBy("singleTapLock")
    private Boolean wasTappedThisFrame = false;
    @GuardedBy("singleTapLock")
    private Long timeOfLastTouch = System.currentTimeMillis();

    private Renderable waypointRenderable;
    private Renderable tempAnchorNodeRenderable;

    private Session session;

    @GuardedBy("anchorLock")
    private Anchor anchor;
    @GuardedBy("anchorLock")
    private AnchorNode anchorNode;
    @GuardedBy("anchorsLock")
    private CloudAnchorMap cloudAnchorMap = new CloudAnchorMap();

    private final static int ANCHOR_DATA_CODE = 1;

    private ArFragment arFragment;

    private Boolean wasNullSession = true;

    // Cloud Anchor Components.
    private FirebaseManager firebaseManager;
    private final CloudAnchorManager cloudManager = new CloudAnchorManager();
    private HostResolveMode currentMode;
    private RoomCodeAndCloudAnchorIdListener hostListener;

    private final Set<AnimationInstance> animators = new ArraySet<>();
    private Node waypointNode;
    private Node tempAnchorNode;

    private String anchorName;
    private ArrayList<String> connectedAnchors;
    private Anchor newAnchor;

    private static class AnimationInstance {
        Animator animator;
        Long startTime;
        float duration;
        int index;

        AnimationInstance(Animator animator, int index, Long startTime) {
            this.animator = animator;
            this.startTime = startTime;
            this.duration = animator.getAnimationDuration(index);
            this.index = index;
        }
    }

    private final String[] dest_name = new String[1];

    private Spinner dest_dropdown;
    private final String DEST_DROPDOWN_PROMPT = "Select a Destination";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        displayRotationHelper = new DisplayRotationHelper(this);


        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        initializeScene(arFragment.getArSceneView().getScene());
        waypointNode = new Node();
        tempAnchorNode = new Node();

        WeakReference<CloudAnchorActivity> weakActivity = new WeakReference<>(this);

        //Directional Arrow
/*
        ModelRenderable.builder()
                .setSource(this, Uri.parse(
                        "https://storage.googleapis.com/ar-answers-in-search-models/static/Tiger/model.glb"))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(
                        modelRenderable -> {
                            Node directionalNode = new Node();
                            directionalNode.setParent(arFragment.getArSceneView().getScene());
                            directionalNode.setRenderable(modelRenderable);
                            directionalNode.setLocalPosition(new Vector3(0f, 0f, 0f));
                            directionalNode.setLocalScale(new Vector3(3f, 3f, 3f));

                        })
                .exceptionally(throwable -> {
                    Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
                    return null;
                });


 */
        ModelRenderable.builder()
                .setSource(
                        this,
                        Uri.parse(
                                "https://storage.googleapis.com/ar-answers-in-search-models/static/Tiger/model.glb"))
                .setIsFilamentGltf(true)
                .build()
                .thenAccept(
                        modelRenderable -> {
                            CloudAnchorActivity activity = weakActivity.get();
                            if (activity != null) {
                                activity.waypointRenderable = modelRenderable;
                                activity.tempAnchorNodeRenderable = modelRenderable;
                            }
                        })
                .exceptionally(
                        throwable -> {
                            Toast toast =
                                    Toast.makeText(this, "Unable to load Waypoint renderable", Toast.LENGTH_LONG);
                            toast.setGravity(Gravity.CENTER, 0, 0);
                            toast.show();
                            return null;
                        });


        // Initialize UI components.
        hostButton = findViewById(R.id.host_button);
        hostButton.setOnClickListener((view) -> onHostButtonPress());
        resolveButton = findViewById(R.id.resolve_button);
        resolveButton.setOnClickListener((view) -> onResolveButtonPress());
        roomCodeText = findViewById(R.id.room_code_text);

        // Initialize Cloud Anchor variables.
        firebaseManager = new FirebaseManager(this);
        currentMode = HostResolveMode.NONE;
        sharedPreferences = getSharedPreferences(PREFERENCE_FILE_KEY, Context.MODE_PRIVATE);

        dest_dropdown = findViewById(R.id.dest_spinner);
        dest_dropdown.setVisibility(View.GONE);

    }

    private void initializeScene(Scene scene) {
        scene.setOnTouchListener(this::onTap);
        scene.addOnUpdateListener(this::onFrame);

        if (arFragment.getArSceneView().getSession() == null) {
            System.out.println("It null");
            return;
        }
        Config config = new Config(arFragment.getArSceneView().getSession());
        config.setCloudAnchorMode(CloudAnchorMode.ENABLED);
        arFragment.getArSceneView().getSession().configure(config);
    }

    private boolean onTap(HitTestResult hitTestResult, MotionEvent motionEvent) {

        synchronized (singleTapLock) {
            synchronized (anchorLock) {
                long currentTime = System.currentTimeMillis();
                if (timeOfLastTouch + 500 < currentTime) {
                    timeOfLastTouch = System.currentTimeMillis();
                    // Only process taps when hosting.
                    if (currentMode != HostResolveMode.HOSTING) {
                        return false;
                    }

                    Frame frame = arFragment.getArSceneView().getArFrame();
                    TrackingState cameraTrackingState = frame.getCamera().getTrackingState();
                    // Only handle a tap if the anchor is currently null, the queued tap is non-null and the
                    // camera is currently tracking.
                    if (motionEvent != null && cameraTrackingState == TrackingState.TRACKING) {
                        Preconditions.checkState(
                                currentMode == HostResolveMode.HOSTING,
                                "We should only be creating an anchor in hosting mode.");
                        for (HitResult hit : frame.hitTest(motionEvent)) {

                            if (shouldCreateAnchorWithHit(hit)) {

                                newAnchor = hit.createAnchor();
                                promptForAnchorName();


                                return true; // Only handle the first valid hit.
                            }

                        }
                    }

                }
                return false;
            }
        }
    }


    private void createDestinationDropdown() {
        ArrayList<String> items = cloudAnchorMap.getAllNames();
        items.add(DEST_DROPDOWN_PROMPT);
        System.out.println("Destination items: " + items.toString());
        final int num_items = items.size() - 1;
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, items) {
            public View getView(int position, View convertView, ViewGroup parent) {

                View v = super.getView(position, convertView, parent);

                ((TextView) v).setTextSize(16);

                return v;

            }

            public View getDropDownView(int position, View convertView, ViewGroup parent) {

                View v = super.getDropDownView(position, convertView, parent);

                ((TextView) v).setGravity(Gravity.CENTER);

                return v;

            }

            @Override
            public int getCount() {
                return (num_items); // Truncate the list
            }
        };
        //set the spinners adapter to the previously created one.
        dest_dropdown.setAdapter(adapter);
        dest_dropdown.setPrompt(DEST_DROPDOWN_PROMPT);
        dest_dropdown.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onNothingSelected(AdapterView<?> adapterView) {
                        Toast.makeText(getApplicationContext(), "No destination selected", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        dest_name[0] = parent.getItemAtPosition(position).toString();
                        if (dest_name[0] != null && !dest_name[0].equals(DEST_DROPDOWN_PROMPT)) {
                            long source_id = findClosestAnchor();
                            renderLineFromCameraToAnchor( cloudAnchorMap.getAnchorNodeById(source_id));
                            System.out.println(dest_name[0]);
                            Toast.makeText(getApplicationContext(), "The option is:" + dest_name[0], Toast.LENGTH_SHORT).show();
                            long dest_id = cloudAnchorMap.getIdFromName(dest_name[0]);
                            renderPath(source_id, dest_id);
                        }


                    }
                });
        dest_dropdown.setVisibility(View.VISIBLE);
        dest_dropdown.setSelection(num_items);

    }

    private void onReceivedAnchorData(String newAnchorName, ArrayList<String> newConnectedAnchors) {
        anchorName = newAnchorName;
        connectedAnchors = newConnectedAnchors;
        Toast.makeText(getApplicationContext(), "Anchor Name is " + anchorName + ", Connected Anchors are " + connectedAnchors.toString(), Toast.LENGTH_SHORT).show();
        wasTappedThisFrame = false;

        cloudManager.hostCloudAnchor(newAnchor, hostListener);
    }

    private void promptForAnchorName() {
        synchronized (singleTapLock) {
            anchorName = null;
            Bundle bundle = new Bundle();

            bundle.putStringArrayList("anchorNames", cloudAnchorMap.getAllNames());

            PromptAnchorData promptAnchorData = new PromptAnchorData();
            promptAnchorData.setArguments(bundle);
            promptAnchorData.setOkListener(this::onReceivedAnchorData);
            promptAnchorData.show(getSupportFragmentManager(), "ResolveDialog");
        }

    }

    private void onFrame(FrameTime frameTime) {
        arFragment.onUpdate(frameTime);
        Frame frame = arFragment.getArSceneView().getArFrame();

        if (frame == null) {
            return;
        }

        Camera camera = frame.getCamera();
        TrackingState cameraTrackingState = camera.getTrackingState();

        if (wasNullSession && arFragment.getArSceneView().getSession() != null) {
            System.out.println("NOT NULL");
            Config config = arFragment.getArSceneView().getSession().getConfig(); //new Config(arFragment.getArSceneView().getSession());

            config.setCloudAnchorMode(CloudAnchorMode.ENABLED);

            arFragment.getArSceneView().getSession().configure(config);
            wasNullSession = false;
        }


        cloudManager.setSession(arFragment.getArSceneView().getSession());
        // Notify the cloudManager of all the updates.
        cloudManager.onUpdate();

        // If not tracking, don't draw 3d objects.
        if (cameraTrackingState == TrackingState.PAUSED) {
            return;
        }
        wasTappedThisFrame = false;
    }

    @Override
    protected void onDestroy() {
        // Clear all registered listeners.
        resetMode();

        if (session != null) {
            // Explicitly close ARCore Session to release native resources.
            // Review the API reference for important considerations before calling close() in apps with
            // more complicated lifecycle requirements:
            // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
            session.close();
            session = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Setting the session in the HostManager.
        cloudManager.setSession(arFragment.getArSceneView().getSession());

        if (currentMode == HostResolveMode.NONE) {
            snackbarHelper.showMessage(this, getString(R.string.snackbar_initial_message));
        }
    }


    private void createSession() {
        if (session == null) {
            Exception exception = null;
            int messageId = -1;
            try {
                switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }
                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(this)) {
                    CameraPermissionHelper.requestCameraPermission(this);
                    return;
                }
                session = new Session(this);
            } catch (UnavailableArcoreNotInstalledException e) {
                messageId = R.string.snackbar_arcore_unavailable;
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                messageId = R.string.snackbar_arcore_too_old;
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                messageId = R.string.snackbar_arcore_sdk_too_old;
                exception = e;
            } catch (Exception e) {
                messageId = R.string.snackbar_arcore_exception;
                exception = e;
            }

            if (exception != null) {
                snackbarHelper.showError(this, getString(messageId));
                Log.e(TAG, "Exception creating session", exception);
                return;
            }

            // Create default config and check if supported.
            Config config = new Config(session);
            config.setCloudAnchorMode(CloudAnchorMode.ENABLED);
            session.configure(config);

            // Setting the session in the HostManager.
            cloudManager.setSession(session);
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            snackbarHelper.showError(this, getString(R.string.snackbar_camera_unavailable));
            session = null;
            return;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            //surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus);
    }

    /**
     * Returns {@code true} if and only if the hit can be used to create an Anchor reliably.
     */
    private static boolean shouldCreateAnchorWithHit(HitResult hit) {
        Trackable trackable = hit.getTrackable();
        if (trackable instanceof Plane) {
            // Check if the hit was within the plane's polygon.
            return ((Plane) trackable).isPoseInPolygon(hit.getHitPose());
        } else if (trackable instanceof Point) {
            // Check if the hit was against an oriented point.
            return ((Point) trackable).getOrientationMode() == OrientationMode.ESTIMATED_SURFACE_NORMAL;
        }
        return false;
    }

    private void setNewAnchor(Anchor newAnchor) {
        synchronized (anchorLock) {
            if (newAnchor != null) {
                anchor = newAnchor;
                anchorNode = new AnchorNode(anchor);
                anchorNode.setParent(arFragment.getArSceneView().getScene());
                Log.i("anchor", "new anchor added");
                Log.i("anchor", String.valueOf(anchorNode == null));
            }
        }
    }

    /**
     * Sets the new value of the current anchor. Detaches the old anchor, if it was non-null.
     */
    private void setNewAnchor(boolean resolve, CloudAnchor cloudAnchor) {
        synchronized (anchorLock) {
            if (cloudAnchor.getAnchor() != null)
                newAddAnchorToList(resolve, cloudAnchor);
        }
    }

    private void setNewAnchor(Anchor newAnchor, boolean detach) {
        synchronized (anchorLock) {
            if (anchor != null && detach) {
                anchor.detach();
            }
            anchor = newAnchor;
        }
    }

    /**
     * Callback function invoked when the Host Button is pressed.
     */
    private void onHostButtonPress() {
        if (currentMode == HostResolveMode.HOSTING) {
            resetMode();
            return;
        }

        if (!sharedPreferences.getBoolean(ALLOW_SHARE_IMAGES_KEY, false)) {
            showNoticeDialog(this::onPrivacyAcceptedForHost);
        } else {
            onPrivacyAcceptedForHost();
        }
    }

    private void onPrivacyAcceptedForHost() {
        if (hostListener != null) {
            return;
        }
        resolveButton.setEnabled(false);
        hostButton.setText(R.string.cancel);
        snackbarHelper.showMessageWithDismiss(this, getString(R.string.snackbar_on_host));

        hostListener = new RoomCodeAndCloudAnchorIdListener();
        firebaseManager.getNewRoomCode(hostListener);
    }

    /**
     * Callback function invoked when the Resolve Button is pressed.
     */
    private void onResolveButtonPress() {
        if (currentMode == HostResolveMode.RESOLVING) {
            resetMode();
            return;
        }

        if (!sharedPreferences.getBoolean(ALLOW_SHARE_IMAGES_KEY, false)) {
            showNoticeDialog(this::onPrivacyAcceptedForResolve);
        } else {
            onPrivacyAcceptedForResolve();
        }

        createDestinationDropdown();
    }

    private void onPrivacyAcceptedForResolve() {
        ResolveDialogFragment dialogFragment = new ResolveDialogFragment();
        dialogFragment.setOkListener(this::onRoomCodeEntered);
        dialogFragment.show(getSupportFragmentManager(), "ResolveDialog");
    }

    /**
     * Resets the mode of the app to its initial state and removes the anchors.
     */
    private void resetMode() {
        hostButton.setText(R.string.host_button_text);
        hostButton.setEnabled(true);
        resolveButton.setText(R.string.resolve_button_text);
        resolveButton.setEnabled(true);
        roomCodeText.setText(R.string.initial_room_code);
        currentMode = HostResolveMode.NONE;
        firebaseManager.clearRoomListener();
        hostListener = null;
        setNewAnchor(null, true);
        snackbarHelper.hide(this);
        cloudManager.clearListeners();
        cloudAnchorMap.clear();
    }

    private void renderPath(Long sourceId, Long destId) {
        // Need to pass source and destination anchorIds
        if (cloudAnchorMap.hasPath(sourceId, destId) && cloudAnchorMap.size() >= 2) {
            System.out.println("It has a path");
//      for(long i = 1; i < cloudAnchorMap.size(); i++){
//        renderLineBetweenTwoAnchorNodes(cloudAnchorMap.getAnchorNodeById(i - 1), cloudAnchorMap.getAnchorNodeById(i));
//
//        if(i == cloudAnchorMap.size() - 1){
//          renderWaypoint(cloudAnchorMap.getAnchorNodeById(i));
//        }
//      }
            // TEMPORARY
            ArrayList<Long> anchorIds = cloudAnchorMap.getAnchorIds();
            Log.i("CloudAnchorMap", anchorIds.toString());
            for (int i = 0; i < anchorIds.size() - 1; i++) {
                renderLineBetweenTwoAnchorNodes(cloudAnchorMap.getAnchorNodeById(anchorIds.get(i)), cloudAnchorMap.getAnchorNodeById(anchorIds.get(i + 1)));
            }
            renderWaypoint(cloudAnchorMap.getAnchorNodeById(Collections.max(anchorIds)));
        }
        if (anchorNode != null) {
            Log.i("CloudAnchorMap", anchorNode.getName());
            renderTempAnchor(anchorNode);
        }
    }

    private void renderWaypoint(AnchorNode anchorNode) {
        // Create the transformable model and add it to the anchor.
        waypointNode.setParent(anchorNode);
        waypointNode.setRenderable(waypointRenderable);
        waypointNode.setLocalScale(new Vector3(.2f, .2f, .2f));


        FilamentAsset filamentAsset = waypointNode.getRenderableInstance().getFilamentAsset();
        if (filamentAsset.getAnimator().getAnimationCount() > 0) {
            animators.add(new AnimationInstance(filamentAsset.getAnimator(), 0, System.nanoTime()));
        }

        Color color = new Color(0, 0, 0, 1);
        for (int i = 0; i < waypointRenderable.getSubmeshCount(); ++i) {
            Material material = waypointRenderable.getMaterial(i);
            material.setFloat4("baseColorFactor", color);
        }
    }

    private void renderTempAnchor(AnchorNode anchorNode) {
        // Create the transformable model and add it to the anchor.
        tempAnchorNode.setParent(anchorNode);
        tempAnchorNode.setRenderable(tempAnchorNodeRenderable);
        tempAnchorNode.setLocalScale(new Vector3(.2f, .2f, .2f));


        FilamentAsset filamentAsset = tempAnchorNode.getRenderableInstance().getFilamentAsset();
        if (filamentAsset.getAnimator().getAnimationCount() > 0) {
            animators.add(new AnimationInstance(filamentAsset.getAnimator(), 0, System.nanoTime()));
        }

        Color color = new Color(0, 0, 0, 1);
        for (int i = 0; i < tempAnchorNodeRenderable.getSubmeshCount(); ++i) {
            Material material = tempAnchorNodeRenderable.getMaterial(i);
            material.setFloat4("baseColorFactor", color);
        }
    }

    void renderLineFromCameraToAnchor(AnchorNode anchorNode){
        Vector3 point1 = anchorNode.getWorldPosition();
        Pose cameraPose = arFragment.getArSceneView().getArFrame().getCamera().getPose();
        Vector3 point2 = new Vector3(cameraPose.tx(), cameraPose.ty(), 0);

        final Vector3 difference = Vector3.subtract(point1, point2);
        final Vector3 directionFromTopToBottom = difference.normalized();
        final Quaternion rotationFromAToB =
                Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());

         /* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
         to extend to the necessary length.  */
        MaterialFactory.makeOpaqueWithColor(getApplicationContext(), new Color(0, 255, 244))
                .thenAccept(
                        material -> {
                            /* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
                                   to extend to the necessary length.  */
                            ModelRenderable model = ShapeFactory.makeCube(
                                    new Vector3(.01f, .01f, difference.length()),
                                    Vector3.zero(), material);
                            /* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
                                   the midpoint between the given points . */
                            Node node = new Node();
                            node.setParent(anchorNode);
                            node.setRenderable(model);
                            node.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                            node.setWorldRotation(rotationFromAToB);
                        }
                );


    }

    private void renderLineBetweenTwoAnchorNodes(AnchorNode prev, AnchorNode curr) {
        Vector3 point1 = curr.getWorldPosition();
        Vector3 point2 = prev.getWorldPosition();
    /* First, find the vector extending between the two points and define a look rotation in terms of this
        Vector. */
        final Vector3 difference = Vector3.subtract(point1, point2);
        final Vector3 directionFromTopToBottom = difference.normalized();
        final Quaternion rotationFromAToB =
                Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());

         /* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
         to extend to the necessary length.  */
        MaterialFactory.makeOpaqueWithColor(getApplicationContext(), new Color(0, 255, 244))
                .thenAccept(
                        material -> {
                            /* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
                                   to extend to the necessary length.  */
                            ModelRenderable model = ShapeFactory.makeCube(
                                    new Vector3(.01f, .01f, difference.length()),
                                    Vector3.zero(), material);
                            /* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
                                   the midpoint between the given points . */
                            Node node = new Node();
                            node.setParent(curr);
                            node.setRenderable(model);
                            node.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                            node.setWorldRotation(rotationFromAToB);
                        }
                );

    }

    private void newAddAnchorToList(boolean resolve, CloudAnchor cloudAnchor) {
        synchronized (anchorsLock) {
            cloudAnchor.setAnchorNode(arFragment.getArSceneView().getScene());
            cloudAnchorMap.add(cloudAnchor, resolve);
            renderAnchorName(cloudAnchor);
            createDestinationDropdown();
        }
    }

    /**
     * Callback function invoked when the user presses the OK button in the Resolve Dialog.
     */
    private void onRoomCodeEntered(Long roomCode) {
        currentMode = HostResolveMode.RESOLVING;
        hostButton.setEnabled(false);
        resolveButton.setText(R.string.cancel);
        roomCodeText.setText(String.valueOf(roomCode));
        snackbarHelper.showMessageWithDismiss(this, getString(R.string.snackbar_on_resolve));
        Log.i("roomCode", String.valueOf(roomCode));
        firebaseManager.registerNewListenerForRoom(
                roomCode,
                (resolvingAnchors, serializedAdjacency) -> {
                    CloudAnchorResolveStateListener resolveListener =
                            new CloudAnchorResolveStateListener(this, roomCode);
                    Preconditions.checkNotNull(resolveListener, "The resolve listener cannot be null.");
                    for (int i = 0; i < resolvingAnchors.size(); i++) {
                        cloudManager.resolveCloudAnchor(
                                resolvingAnchors.get(i), resolveListener, SystemClock.uptimeMillis());
                    }
                    try {
                        cloudAnchorMap.setAdjacency(serializedAdjacency);
                    } catch (IOException exception) {
                        exception.printStackTrace();
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
        );
    }

    /**
     * Listens for both a new room code and an anchor ID, and shares the anchor ID in Firebase with
     * the room code when both are available.
     */
    private final class RoomCodeAndCloudAnchorIdListener
            implements CloudAnchorManager.CloudAnchorHostListener, FirebaseManager.RoomCodeListener {

        private Context context;
        private Long roomCode;
        private Long roomIdx;
        private String cloudAnchorId;
        private Pose cloudAnchorPose;

        @Override
        public void onNewRoomCode(Context currentContext, Long newRoomCode) {
            Preconditions.checkState(roomCode == null, "The room code cannot have been set before.");
            context = currentContext;
            roomCode = newRoomCode;
            roomIdx = 0L;
            roomCodeText.setText(String.valueOf(roomCode));
            snackbarHelper.showMessageWithDismiss(
                    CloudAnchorActivity.this, getString(R.string.snackbar_room_code_available));
            checkAndMaybeShare();
            synchronized (singleTapLock) {
                // Change currentMode to HOSTING after receiving the room code (not when the 'Host' button
                // is tapped), to prevent an anchor being placed before we know the room code and able to
                // share the anchor ID.
                currentMode = HostResolveMode.HOSTING;
            }
        }

        @Override
        public void onError(DatabaseError error) {
            Log.w(TAG, "A Firebase database error happened.", error.toException());
            snackbarHelper.showError(
                    CloudAnchorActivity.this, getString(R.string.snackbar_firebase_error));
        }

        @Override
        public void onCloudTaskComplete(Anchor anchor) {
            CloudAnchorState cloudState = anchor.getCloudAnchorState();
            if (cloudState.isError()) {
                Log.e(TAG, "Error hosting a cloud anchor, state " + cloudState);
                snackbarHelper.showMessageWithDismiss(
                        CloudAnchorActivity.this, getString(R.string.snackbar_host_error, cloudState));
                return;
            }
            Preconditions.checkState(
                    cloudAnchorId == null, "The cloud anchor ID cannot have been set before.");
            cloudAnchorId = anchor.getCloudAnchorId();
            cloudAnchorPose = anchor.getPose();
            setNewAnchor(anchor);
            checkAndMaybeShare();
        }

        private void checkAndMaybeShare() {
            if (roomCode == null || roomIdx == null || cloudAnchorId == null || cloudAnchorPose == null) {
                return;
            }

            CloudAnchor cloudAnchor = new CloudAnchor(anchor, anchorName, cloudAnchorId, roomIdx, arFragment.getArSceneView().getScene());

            setNewAnchor(false, cloudAnchor);
//            cloudAnchorMap.add(cloudAnchor, false);

            ArrayList<Long> connectedAnchorIds = cloudAnchorMap.getIdsFromNames(connectedAnchors);
            for (Long id : connectedAnchorIds) {
                //change weight to distance
                cloudAnchorMap.createEdge(roomIdx, id, 1.0f);
                AnchorNode anchorNode = cloudAnchorMap.getAnchorNodeById(id);
                renderLineBetweenTwoAnchorNodes(anchorNode, cloudAnchor.getAnchorNode());
            }
            String serializedAdjacency = "";
            try {
                serializedAdjacency = cloudAnchorMap.serializeAdjacency();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
            firebaseManager.storeAnchorIdInRoom(roomCode, roomIdx, cloudAnchorId, anchorName, cloudAnchorPose, serializedAdjacency);

            roomIdx++;
            snackbarHelper.showMessageWithDismiss(
                    CloudAnchorActivity.this, getString(R.string.snackbar_cloud_id_shared));
            reset();
        }

        private void reset() {
            setNewAnchor(null, false);
            cloudAnchorId = null;
            cloudAnchorPose = null;
        }
    }

    private final class CloudAnchorResolveStateListener
            implements CloudAnchorManager.CloudAnchorResolveListener {
        private final long roomCode;
        private final Context context;

        CloudAnchorResolveStateListener(Context currentContext, long roomCode) {
            this.roomCode = roomCode;
            this.context = currentContext;
        }

        @Override
        public void onCloudTaskComplete(CloudAnchor cloudAnchor) {
            // When the anchor has been resolved, or had a final error state.
            CloudAnchorState cloudState = cloudAnchor.getAnchor().getCloudAnchorState();
            if (cloudState.isError()) {
                Log.w(
                        TAG,
                        "The anchor in room "
                                + roomCode
                                + " could not be resolved. The error state was "
                                + cloudState);
                snackbarHelper.showMessageWithDismiss(
                        CloudAnchorActivity.this, getString(R.string.snackbar_resolve_error, cloudState));
                return;
            }
            setNewAnchor(true, cloudAnchor);
        }

        @Override
        public void onShowResolveMessage() {
            snackbarHelper.setMaxLines(4);
            snackbarHelper.showMessageWithDismiss(
                    CloudAnchorActivity.this, getString(R.string.snackbar_resolve_no_result_yet));
        }


    }

    public void showNoticeDialog(HostResolveListener listener) {
        DialogFragment dialog = PrivacyNoticeDialogFragment.createDialog(listener);
        dialog.show(getSupportFragmentManager(), PrivacyNoticeDialogFragment.class.getName());
    }

    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
        if (!sharedPreferences.edit().putBoolean(ALLOW_SHARE_IMAGES_KEY, true).commit()) {
            throw new AssertionError("Could not save the user preference to SharedPreferences!");
        }
        createSession();
    }

    public void renderAnchorName(CloudAnchor cloudAnchor) {

        ViewRenderable.builder().setView(arFragment.getContext(), R.layout.anchor_name_display)
                .build()
                .thenAccept(
                        viewRenderable -> {
                            viewRenderable.setShadowCaster(false);
                            viewRenderable.setShadowReceiver(false);
                            Node nameNode = new Node();
                            nameNode.setParent(cloudAnchor.getAnchorNode());
                            nameNode.setRenderable(viewRenderable);
                            nameNode.setLocalScale(new Vector3(1f, 1f, 1f));
                            TextView textView = viewRenderable.getView().findViewById(R.id.anchor_name);
                            textView.setText(cloudAnchor.getAnchorName());
                            nameNode.setLocalPosition(new Vector3(0f, 1f, 0f));

                        }).exceptionally(
                throwable -> {
                    Toast toast =
                            Toast.makeText(this, "Unable to make anchor name display", Toast.LENGTH_LONG);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();
                    return null;
                });
    }

    public Long findClosestAnchor() {
        Pose cameraPose = arFragment.getArSceneView().getArFrame().getCamera().getPose();
        Long minDistCloudAnchorId = null;
        float minDistance = Float.MAX_VALUE;
        for (Long cloudAnchorId : cloudAnchorMap.getAnchorIds()) {
            AnchorNode currAnchorNode = cloudAnchorMap.getAnchorNodeById(cloudAnchorId);
            Pose anchorPose = currAnchorNode.getAnchor().getPose();

            float dx = anchorPose.tx() - cameraPose.tx();
            float dy = anchorPose.ty() - cameraPose.ty();
            float dz = anchorPose.tz() - cameraPose.tz();

            ///Compute the straight-line distance.
            float distanceToAnchor = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distanceToAnchor < minDistance) {
                minDistance = distanceToAnchor;
                minDistCloudAnchorId = cloudAnchorId;
            }


        }

        CloudAnchor minDistCloudAnchor = cloudAnchorMap.getCloudAnchorById(minDistCloudAnchorId);

        Toast toast =
                Toast.makeText(this, "Closest Anchor is: " + minDistCloudAnchor.getAnchorName(), Toast.LENGTH_LONG);
        toast.setGravity(Gravity.CENTER, 0, 0);
        toast.show();

        return minDistCloudAnchorId;
    }


}
