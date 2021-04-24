package com.google.ar.core.examples.java.cloudanchor;

import android.util.Log;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.NodeParent;
import com.google.ar.sceneform.math.Vector3;

import java.util.ArrayList;

import javax.annotation.Nullable;

public class CloudAnchor {
    @Nullable
    private String anchorName;
    @Nullable
    private Anchor anchor;
    @Nullable
    private AnchorNode anchorNode;
    private Long anchorId;
    private String cloudAnchorId;
    private boolean isDisplay = true;

    private Vector3 mappedTranslation = null;


    public CloudAnchor(Long anchorId, String anchorName, String cloudAnchorId) {
        this.anchorId = anchorId;
        this.anchorName = anchorName;
        this.cloudAnchorId = cloudAnchorId;
        this.isDisplay = false;
    }

    public CloudAnchor(Long anchorId, String anchorName, String cloudAnchorId, ArrayList<Float> mappedTranslation) {
        this.anchorId = anchorId;
        this.anchorName = anchorName;
        this.cloudAnchorId = cloudAnchorId;
        this.isDisplay = false;
        this.mappedTranslation = new Vector3(mappedTranslation.get(0), mappedTranslation.get(1), mappedTranslation.get(2));
        Log.i("translation", this.mappedTranslation.toString());
    }


    public CloudAnchor(Anchor anchor, Long anchorId, NodeParent nodeParent) {
        this.anchorId = anchorId;
        this.cloudAnchorId = anchor.getCloudAnchorId();
        this.anchor = anchor;
        setAnchorNode(nodeParent);
    }

    public CloudAnchor(Anchor anchor, String anchorName, Long anchorId, NodeParent nodeParent) {
        this.anchorName = anchorName;
        this.anchorId = anchorId;
        this.cloudAnchorId = anchor.getCloudAnchorId();
        this.anchor = anchor;
        setAnchorNode(nodeParent);
    }

    public CloudAnchor(Anchor anchor, String anchorName, String cloudAnchorId, Long anchorId, NodeParent nodeParent) {
        this.anchorName = anchorName;
        this.anchorId = anchorId;
        this.cloudAnchorId = cloudAnchorId;
        this.anchor = anchor;
        setAnchorNode(nodeParent);
    }

    public void setDisplayName(@Nullable String anchorName) {
        this.anchorName = anchorName;
    }

    public void setAnchor(Anchor anchor) {
        this.anchor = anchor;
    }

    public void setAnchorNode(NodeParent nodeParent) {
        this.anchorNode = new AnchorNode(anchor);
        this.anchorNode.setParent(nodeParent);
    }

    public boolean getIsDisplay() {
        return this.isDisplay;
    }

    public Anchor getAnchor() {
        return this.anchor;
    }

    public Long getAnchorId() {
        return this.anchorId;
    }

    public String getCloudAnchorId() {
        return this.cloudAnchorId;
    }

    public AnchorNode getAnchorNode() {
        return this.anchorNode;
    }

    public String getAnchorName() {
        return this.anchorName;
    }
}
