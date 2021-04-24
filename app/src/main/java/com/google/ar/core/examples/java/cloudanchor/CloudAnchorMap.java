package com.google.ar.core.examples.java.cloudanchor;

import android.util.Log;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.NodeParent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class CloudAnchorMap {
    private LinkedHashMap<Long, CloudAnchor> map = new LinkedHashMap<Long, CloudAnchor>();

    public void add(Anchor anchor, Long anchorId, NodeParent nodeParent) {
        CloudAnchor cloudAnchor = new CloudAnchor(anchor, anchorId, nodeParent);
        map.put(cloudAnchor.getAnchorId(), cloudAnchor);
        Log.i("cloudAnchorMap", "Anchor Id " + anchorId + " inserted");
    }

    public void add(CloudAnchor cloudAnchor) {
        map.put(cloudAnchor.getAnchorId(), cloudAnchor);
        Log.i("cloudAnchorMap", "Anchor Id " + cloudAnchor.getAnchorId() + " inserted");
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

    public void clear() {
        for (Map.Entry<Long, CloudAnchor> entry : map.entrySet()) {
            entry.getValue().getAnchorNode().setRenderable(null);
            entry.getValue().getAnchorNode().setParent(null);
            Log.i("clear", String.valueOf(entry.getValue().getAnchorNode().getRenderable() == null));
        }
        map.clear();
    }

    public ArrayList<String> getAllNames() {
        ArrayList<String> names = new ArrayList<>();
        for (Map.Entry<Long, CloudAnchor> entry : map.entrySet()) {
            names.add(entry.getValue().getAnchorName());
        }
        return names;
    }
}
