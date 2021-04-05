package com.google.ar.core.examples.java.cloudanchor;

import android.util.Log;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.NodeParent;

import java.util.ArrayList;
import java.util.HashMap;

public class CloudAnchorMap {
    private HashMap<Long, CloudAnchor> map = new HashMap<Long, CloudAnchor>();

    public void add(Anchor anchor, Long anchorId, NodeParent nodeParent) {
        CloudAnchor cloudAnchor = new CloudAnchor(anchor, anchorId, nodeParent);
        map.put(cloudAnchor.getAnchorId(), cloudAnchor);
        Log.i("cloudAnchorMap", "Anchor Id " + anchorId + " inserted");
    }

    public boolean hasPath() {
        return true;
    }

    public int size() {
        return map.size();
    }

    public ArrayList<Long> getAnchorIds() {
        return new ArrayList<>(map.keySet());
    }

    public AnchorNode getAnchorNodeById(Long anchorId) {
        return map.get(anchorId).getAnchorNode();
    }

    public void createPath(int anchorId1, int anchorId2) {

    }
}
