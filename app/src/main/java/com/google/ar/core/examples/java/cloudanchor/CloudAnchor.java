package com.google.ar.core.examples.java.cloudanchor;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.NodeParent;

import javax.annotation.Nullable;

public class CloudAnchor {
    @Nullable
    private String displayName;
    @Nullable
    private Anchor anchor;
    @Nullable
    private AnchorNode anchorNode;
    private Long anchorId;

    public CloudAnchor(Anchor anchor, Long anchorId, NodeParent nodeParent) {
        this.anchor = anchor;
        this.anchorId = anchorId;
        this.anchorNode = new AnchorNode(anchor);
        this.anchorNode.setParent(nodeParent);
    }

    public CloudAnchor(Anchor anchor, String displayName, Long anchorId, NodeParent nodeParent) {
        this.anchor = anchor;
        this.displayName = displayName;
        this.anchorId = anchorId;
        this.anchorNode = new AnchorNode(anchor);
        this.anchorNode.setParent(nodeParent);
    }

    public void setDisplayName(@Nullable String displayName) {
        this.displayName = displayName;
    }

    public Anchor getAnchor() {
        return this.anchor;
    }

    public Long getAnchorId() {
        return this.anchorId;
    }

    public AnchorNode getAnchorNode() {
        return this.anchorNode;
    }
}
