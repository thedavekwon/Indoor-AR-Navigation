package com.google.ar.core.examples.java.cloudanchor;

import java.io.Serializable;

public class Edge implements Serializable {
    long id;
    float weight;

    public Edge(long id, float weight) {
        this.id = id;
        this.weight = weight;
    }

    @Override
    public String toString() {
        return "Edge{" +
                "id=" + id +
                ", weight=" + weight +
                '}';
    }
}