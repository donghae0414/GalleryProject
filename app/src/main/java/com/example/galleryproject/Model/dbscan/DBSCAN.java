package com.example.galleryproject.Model.dbscan;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class DBSCAN implements Algorithm{

    public List<DataPoint> points;
    private List<Cluster> clusters;

    public double max_distance;
    public int min_points;

    public boolean[] visited;

    public DBSCAN(double max_distance, int min_points) {
        this.points = new ArrayList();
        this.clusters = new ArrayList();
        this.max_distance = max_distance;
        this.min_points = min_points;
    }

    public void cluster() {
        for (int n = 0; n < points.size(); n++) {

            if (!visited[n]) {
                DataPoint d = points.get(n);
                visited[n] = true;
                List<Integer> neighbors = getNeighbors(d);

                if (neighbors.size() >= min_points) {
                    Cluster c = new Cluster(clusters.size());
                    buildCluster(d, c, neighbors);
                    clusters.add(c);
                }
            }
        }
    }

    private void buildCluster(DataPoint d, Cluster c, List<Integer> neighbors) {
        c.addPoint(d);

        for (int i = 0; i < neighbors.size(); i++) {
            int point = neighbors.get(i);

            DataPoint p = points.get(point);
            if(!visited[point]) {
                visited[point] = true;
                List newNeighbors = getNeighbors(p);
                if(newNeighbors.size() >= min_points) {
                    merge(neighbors, newNeighbors);
//                    neighbors.addAll(newNeighbors);
                }
            }
            if(p.getCluster() == -1) {
                c.addPoint(p);
            }
        }
    }

    private List getNeighbors(DataPoint d) {
        List<Integer> neighbors = new ArrayList();
        int i = 0;
        for (DataPoint point : points) {
            double distance = d.distance(point);

            if(distance <= max_distance) {
                neighbors.add(i);
            }
            i++;
        }

        return neighbors;
    }

    public List<Integer> merge(final List<Integer> one, final List<Integer> two) {
        final Set oneSet = new HashSet(one);
        for (Integer item: two) {

            if (!oneSet.contains(item)) {
                one.add(item);
            }
        }

        return one;
    }

    public void setPoints(List points) {
        this.points = points;
        this.visited = new boolean[points.size()];
    }

    public List<Cluster> getClusters() {
        return this.clusters;
    }
}