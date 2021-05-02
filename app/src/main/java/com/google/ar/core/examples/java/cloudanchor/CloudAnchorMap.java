package com.google.ar.core.examples.java.cloudanchor;

import android.util.Log;
import android.util.Pair;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.NodeParent;
import com.google.ar.sceneform.math.Vector3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CloudAnchorMap {
    private LinkedHashMap<Long, CloudAnchor> map = new LinkedHashMap<>();
    private LinkedHashMap<String, Long> nameToId = new LinkedHashMap<>();
    private Set<Long> anchors = new HashSet<>();
    private LinkedHashMap<Long, List<Pair<Long, Float>>> adjacency = new LinkedHashMap<>();
    private List<List<float[]>> relativeTransformations = new ArrayList<>();

    public void add(Anchor anchor, Long anchorId, NodeParent nodeParent) {
        CloudAnchor cloudAnchor = new CloudAnchor(anchor, anchorId, nodeParent);
        map.put(cloudAnchor.getAnchorId(), cloudAnchor);
        anchors.add(cloudAnchor.getAnchorId());
        List<Pair<Long, Float>> temp = new ArrayList<Pair<Long, Float>>();
        adjacency.put(cloudAnchor.getAnchorId(), temp);
        Log.i("cloudAnchorMap", "Anchor Id " + anchorId + " inserted");
        Log.i("adjacency", "Anchor Id " + cloudAnchor.getAnchorId() + " inserted");
    }

    public void add(CloudAnchor cloudAnchor) {
        map.put(cloudAnchor.getAnchorId(), cloudAnchor);
        anchors.add(cloudAnchor.getAnchorId());
        nameToId.put(cloudAnchor.getAnchorName(), cloudAnchor.getAnchorId());
        List<Pair<Long, Float>> temp = new ArrayList<Pair<Long, Float>>();
        adjacency.put(cloudAnchor.getAnchorId(), temp);
        Log.i("cloudAnchorMap", "Anchor Id " + cloudAnchor.getAnchorId() + " inserted");
        Log.i("adjacency", "Anchor Id " + cloudAnchor.getAnchorId() + " inserted");
    }

    public Boolean hasPath(Long sourceAnchorId, Long destAnchorId) {
        if (findPath(sourceAnchorId, destAnchorId).isEmpty()) {
            return false;
        }
        return true;
    }

    public List<Long> findPath(Long sourceAnchorId, Long destAnchorId) {
        Set<Long> visited = new HashSet<>();
        visited.add(sourceAnchorId);
        Set<Long> notVisited = new HashSet<>();

        HashMap<Long, Long> parentTrack = new HashMap<>();
        parentTrack.put(sourceAnchorId, null);
        HashMap<Long, Double> shortestPath = new HashMap<>();

        List<Long> path = new ArrayList<>();

        for (Long anchorId : anchors) {
            if (anchorId == sourceAnchorId)
                shortestPath.put(anchorId, 0.0);
            else
                shortestPath.put(anchorId, Double.POSITIVE_INFINITY);
        }

        while (true) {
            Long currentAnchor = closestNeighborUnvist(shortestPath, visited);

            if (currentAnchor == null) {
                // Message saying there is no path from chosen source anchor to dest anchor
                List<Long> npath = new ArrayList<>();
                return npath;
            }

            if (currentAnchor == destAnchorId) {
                Long traceAnchor = destAnchorId;

                while (true) {
                    Long parent = parentTrack.get(traceAnchor);
                    if (parent == null) {
                        break;
                    }

                    path.add(parent);
                    traceAnchor = parent;
                }

                return path;
            }
            visited.add(currentAnchor);

            for (Pair<Long, Float> edge : adjacency.get(currentAnchor)) {
                if (visited.contains(edge.first))
                    continue;

                if (shortestPath.get(currentAnchor) + edge.second < shortestPath.get(edge.first)) {
                    shortestPath.put(edge.first, shortestPath.get(currentAnchor) + edge.second);
                    parentTrack.put(edge.first, currentAnchor);
                }
            }

        }
    }

    public Long closestNeighborUnvist(HashMap<Long, Double> shortestPath, Set<Long> visited) {
        double shortestDist = Double.POSITIVE_INFINITY;
        Long closestAnchor = null;
        for (Long anchor : anchors) {
            if (visited.contains(anchor))
                continue;

            double curDistance = shortestPath.get(anchor);
            if (curDistance == Double.POSITIVE_INFINITY)
                continue;

            if (curDistance < shortestDist) {
                shortestDist = curDistance;
                closestAnchor = anchor;
            }
        }

        return closestAnchor;
    }

    public int size() {
        return map.size();
    }

    public ArrayList<Long> getAnchorIds() {
        return new ArrayList<>(map.keySet());
    }

    public AnchorNode getAnchorNodeById(Long anchorId) {
        Log.i("getAnchorNodeById", String.valueOf(anchorId));
        Log.i("getAnchorNodeById", String.valueOf(map.get(anchorId).getAnchorId()));
        return map.get(anchorId).getAnchorNode();
    }

    public void createEdge(Long anchorId1, Long anchorId2, Float weight) {
        Pair<Long, Float> temp = new Pair<>(anchorId2, weight);
        Pair<Long, Float> temp2 = new Pair<>(anchorId1, weight);
        adjacency.get(anchorId1).add(temp);
        adjacency.get(anchorId2).add(temp2);
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

    public ArrayList<Long> getIdsFromNames(ArrayList<String> names) {
        ArrayList<Long> ids = new ArrayList<>();
        for(String name : names){
            ids.add(nameToId.get(name));
        }
        return ids;
    }

    public void calculateRelativeTransformations() {
        relativeTransformations.clear();
        for (Map.Entry<Long, CloudAnchor> entry1 : map.entrySet()) {
            ArrayList<float[]> rel = new ArrayList<>();
            for (Map.Entry<Long, CloudAnchor> entry2 : map.entrySet()) {
                Log.i("relativeTransformations", entry1.getKey() + ", " + entry2.getKey());
                if (entry1.getKey() == entry2.getKey()) {
                    rel.add(new float[]{0, 0, 0});
                } else {
                    float[] pos1 = entry1.getValue().getAnchor().getPose().getTranslation();
                    float[] pos2 = entry2.getValue().getAnchor().getPose().getTranslation();
                    rel.add(new float[]{pos1[0]-pos2[0], pos1[1]-pos2[1], pos1[2]-pos2[2]});
                }
            }
            relativeTransformations.add(rel);
        }
    }

    public List<List<float[]>> getRelativeTransformations() {
        return relativeTransformations;
    }

    public String serializeRelativeTransformations() throws IOException {
        for (int i = 0; i < relativeTransformations.size(); i++) {
            Log.i("relativeTransformations", String.valueOf(relativeTransformations.get(i).size()));
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(relativeTransformations);
        oos.close();
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    public void setRelativeTransformations(String s) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(s);
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        relativeTransformations = (List<List<float[]>>)o;
        Log.i("relativeTransformations", relativeTransformations.toString());
        Log.i("relativeTransformations", String.valueOf(relativeTransformations.get(1).get(1)[0]));
        for (int i = 0; i < relativeTransformations.size(); i++) {
            Log.i("relativeTransformations", String.valueOf(relativeTransformations.get(i).size()));
        }
    }
}
