package com.google.ar.core.examples.java.cloudanchor;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.ar.core.Anchor;
import com.google.ar.core.Anchor.CloudAnchorState;
import com.google.ar.core.Session;
import com.google.common.base.Preconditions;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A helper class to handle all the Cloud Anchors logic, and add a callback-like mechanism on top of
 * the existing ARCore API.
 */
class CloudAnchorManager {
    private static final String TAG =
            CloudAnchorActivity.class.getSimpleName() + "." + CloudAnchorManager.class.getSimpleName();
    private static final long DURATION_FOR_NO_RESOLVE_RESULT_MS = 10000;
    private long deadlineForMessageMillis;

    /**
     * Listener for the results of a host operation.
     */
    interface CloudAnchorHostListener {

        /**
         * This method is invoked when the results of a Cloud Anchor operation are available.
         */
        void onCloudTaskComplete(Anchor anchor);
    }

    /**
     * Listener for the results of a resolve operation.
     */
    interface CloudAnchorResolveListener {

        /**
         * This method is invoked when the results of a Cloud Anchor operation are available.
         */
        void onCloudTaskComplete(CloudAnchor cloudAnchor);

        /**
         * This method show the toast message.
         */
        void onShowResolveMessage();
    }

    @Nullable
    private Session session = null;
    private final HashMap<Anchor, CloudAnchorHostListener> pendingHostAnchors = new HashMap<>();
    private final HashMap<CloudAnchor, CloudAnchorResolveListener> pendingResolveAnchors = new HashMap<>();

    /**
     * This method is used to set the session, since it might not be available when this object is
     * created.
     */
    synchronized void setSession(Session session) {
        this.session = session;
    }

    /**
     * This method hosts an anchor. The {@code listener} will be invoked when the results are
     * available.
     */
    synchronized void hostCloudAnchor(Anchor anchor, CloudAnchorHostListener listener) {
        Preconditions.checkNotNull(session, "The session cannot be null.");
        Anchor newAnchor = session.hostCloudAnchor(anchor);
        if (pendingHostAnchors.isEmpty())
            pendingHostAnchors.put(newAnchor, listener);
    }

    /**
     * This method resolves an anchor. The {@code listener} will be invoked when the results are
     * available.
     */
    synchronized void resolveCloudAnchor(
            CloudAnchor cloudAnchor, CloudAnchorResolveListener listener, long startTimeMillis) {
        Log.w(TAG, "resolveCloudAnchor: " + cloudAnchor.getAnchorId(), null);
        Preconditions.checkNotNull(session, "The session cannot be null.");
        Anchor newAnchor = session.resolveCloudAnchor(cloudAnchor.getCloudAnchorId());
        cloudAnchor.setAnchor(newAnchor);
        Log.w(TAG, "resolveCloudAnchor: " + newAnchor.getPose().toString(), null);
        deadlineForMessageMillis = startTimeMillis + DURATION_FOR_NO_RESOLVE_RESULT_MS;
        pendingResolveAnchors.put(cloudAnchor, listener);
    }

    /**
     * Should be called after a {@link Session#update()} call.
     */

    synchronized void onUpdate() {
        Preconditions.checkNotNull(session, "The session cannot be null.");
        Iterator<Map.Entry<Anchor, CloudAnchorHostListener>> hostIter =
                pendingHostAnchors.entrySet().iterator();
        while (hostIter.hasNext()) {
            Map.Entry<Anchor, CloudAnchorHostListener> entry = hostIter.next();
            Anchor anchor = entry.getKey();
            if (isReturnableState(anchor.getCloudAnchorState())) {
                CloudAnchorHostListener listener = entry.getValue();
                listener.onCloudTaskComplete(anchor);
                Log.i("anchor", "anchor inserted: " + anchor.getCloudAnchorId());
                hostIter.remove();
            }
        }

        Iterator<Map.Entry<CloudAnchor, CloudAnchorResolveListener>> resolveIter =
                pendingResolveAnchors.entrySet().iterator();
        while (resolveIter.hasNext()) {
            Map.Entry<CloudAnchor, CloudAnchorResolveListener> entry = resolveIter.next();
            CloudAnchorResolveListener listener = entry.getValue();
            Log.i("anchor", "anchor resolving: " + entry.getKey().getCloudAnchorId());
            Log.i("anchor", "anchor resolving status: " + entry.getKey().getAnchor().getCloudAnchorState().toString());
            if (isReturnableState(entry.getKey().getAnchor().getCloudAnchorState())) {
                listener.onCloudTaskComplete(entry.getKey());
                resolveIter.remove();
            }
            if (deadlineForMessageMillis > 0 && SystemClock.uptimeMillis() > deadlineForMessageMillis) {
                listener.onShowResolveMessage();
                deadlineForMessageMillis = 0;
            }
        }
    }

    /**
     * Used to clear any currently registered listeners, so they wont be called again.
     */
    synchronized void clearListeners() {
        pendingHostAnchors.clear();
        deadlineForMessageMillis = 0;
    }

    private static boolean isReturnableState(CloudAnchorState cloudState) {
        switch (cloudState) {
            case NONE:
            case TASK_IN_PROGRESS:
                return false;
            default:
                return true;
        }
    }
}
