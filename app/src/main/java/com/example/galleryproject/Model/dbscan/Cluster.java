package com.example.galleryproject.Model.dbscan;


import java.util.ArrayList;
import java.util.List;

public class Cluster {
    public List<DataPoint> points;
    public DataPoint centroid;
    public int id;

    public Cluster(int id) {
        this.id = id;
        this.points = new ArrayList();
//        this.centroid = null;
    }

    public List<DataPoint> getPoints() {
        return points;
    }

    public void addPoint(DataPoint point) {
        points.add(point);
        point.setCluster(id);
    }

    public void setPoints(List points) {
        this.points = points;
    }

//    public DataPoint getCentroid() {
//        return centroid;
//    }
//
//    public void setCentroid(Point centroid) {
//        this.centroid = centroid;
//    }

    public int getId() {
        return id;
    }

    public void clear() {
        points.clear();
    }

    public void plotCluster() {
        System.out.println("[Cluster: " + id + "]");
//        System.out.println("[Centroid: " + centroid + "]");
        System.out.println("[Points: \n");
        for(DataPoint p : points) {
            System.out.println(p);
        }
        System.out.println("]");
    }

    @Override
    public String toString() {
        StringBuffer s = new StringBuffer();

        s.append("[Cluster: " + id + "]\n");
        s.append("[Points: \n");
        for(DataPoint p : points) {
            s.append(p + "\n");
        }
        s.append("]\n");

        return s.toString();
    }
}