package com.example.galleryproject.ui.map;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;

import com.example.galleryproject.Database.AppDatabase;
import com.example.galleryproject.ImageCollectionViewModel;
import com.example.galleryproject.Model.Image;
import com.example.galleryproject.Model.ImageCollection;
import com.example.galleryproject.PhotoGroupActivity;
import com.example.galleryproject.R;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import com.google.maps.android.clustering.Cluster;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;
import com.google.maps.android.ui.IconGenerator;

import java.util.ArrayList;
import java.util.List;

public class MapFragment extends Fragment implements OnMapReadyCallback,
                                                     ClusterManager.OnClusterClickListener<MarkerItem>,
                                                     ClusterManager.OnClusterInfoWindowClickListener<MarkerItem>,
                                                     ClusterManager.OnClusterItemClickListener<MarkerItem>,
                                                     ClusterManager.OnClusterItemInfoWindowClickListener<MarkerItem> {
    private GoogleMap mMap;
    private ClusterManager<MarkerItem> mClusterManager;
    private ImageCollectionViewModel collectionViewModel;

    AppDatabase mDb;
    List<ImageCollection> collections;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_map, container, false);
        setUpMap();

        collectionViewModel = ViewModelProviders.of(getActivity()).get(ImageCollectionViewModel.class);
        mDb = AppDatabase.getInstance(getContext());
        collections = new ArrayList<>();
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private class MarkerItemRenderer extends DefaultClusterRenderer<MarkerItem> {
        private final IconGenerator mIconGenerator = new IconGenerator(getContext());
        private final IconGenerator mClusterIconGenerator = new IconGenerator(getContext());
        private final ImageView mImageView;
        private final ImageView mClusterImageView;
        private final int mDimension;
        MarkerItemRenderer() {
            super(getContext(), getMap(), mClusterManager);

            // Group marker layout
            View clusterMarker_rootView = getLayoutInflater().inflate(R.layout.map_marker, null);
            Drawable clusterDraw = getResources().getDrawable(R.drawable.map_bubble, null);
            mClusterIconGenerator.setBackground(clusterDraw);
            mClusterIconGenerator.setContentView(clusterMarker_rootView);

            mClusterImageView = clusterMarker_rootView.findViewById(R.id.mapMarker_imageView);

            // Single marker layout
            View singleMarker_rootView = getLayoutInflater().inflate(R.layout.map_marker, null);
            mIconGenerator.setBackground(clusterDraw);
            mIconGenerator.setContentView(singleMarker_rootView);

            mImageView = singleMarker_rootView.findViewById(R.id.mapMarker_imageView);
            mDimension = (int) getResources().getDimension(R.dimen.custom_profile_image);
        }

        @Override
        protected void onBeforeClusterItemRendered(MarkerItem marker, MarkerOptions markerOptions) {
            // Draw a single person.
            // Set the info window to show their name.

            Bitmap icon = mIconGenerator.makeIcon();
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(icon)).title(marker.getTitle());
        }

        @Override
        protected void onClusterItemRendered(final MarkerItem clusterItem, final Marker marker) {
            Glide.with(getContext())
                 .load(clusterItem.getRepImage().getFile())
                 .apply(new RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
                 .fitCenter()
                 .override(200)
                 .into(new SimpleTarget<Drawable>() {
                    @Override
                    public void onResourceReady(Drawable drawable, Transition<? super Drawable> transition) {
                        Marker marker = getMarker(clusterItem);
                        if (marker == null) {
                            return;
                        }

                        mImageView.setImageDrawable(drawable);
                        Bitmap icon = mIconGenerator.makeIcon();
                        marker.setIcon(BitmapDescriptorFactory.fromBitmap(icon));
                    }
                 });
        }

        @Override
        protected void onBeforeClusterRendered(Cluster<MarkerItem> cluster, MarkerOptions markerOptions) {
            // Draw multiple people.
            // Note: this method runs on the UI thread. Don't spend too much time in here (like in this example).

            Bitmap icon = mIconGenerator.makeIcon();
            markerOptions.icon(BitmapDescriptorFactory.fromBitmap(icon));
        }

        @Override
        protected void onClusterRendered(final Cluster<MarkerItem> cluster, final Marker marker) {
            final List<Drawable> markerPhotos = new ArrayList<>();
            final int width = mDimension;
            final int height = mDimension;
            for (MarkerItem item : cluster.getItems()) {
                // Draw 4 at most.
                if (markerPhotos.size() == 4) break;
                try {
                    Glide.with(getContext())
                         .load(item.getRepImage().getFile())
                         .diskCacheStrategy(DiskCacheStrategy.ALL)
                         .override(100)
                         .into(new SimpleTarget<Drawable>(){
                             @Override
                             public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                                 Marker marker = getMarker(cluster);
                                 if (marker == null) {
                                     return;
                                 }

                                 resource.setBounds(0, 0, width, height);
                                 markerPhotos.add(resource);

                                 MultiDrawable multiDrawable = new MultiDrawable(markerPhotos);
                                 multiDrawable.setBounds(0, 0, width, height);
                                 mClusterImageView.setImageDrawable(multiDrawable);
                                 Bitmap icon = mClusterIconGenerator.makeIcon(String.valueOf(cluster.getSize()));
                                 marker.setIcon(BitmapDescriptorFactory.fromBitmap(icon));
                             }
                         });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            Bitmap icon = mClusterIconGenerator.makeIcon(String.valueOf(cluster.getSize()));
            marker.setIcon(BitmapDescriptorFactory.fromBitmap(icon));

        }

        @Override
        protected boolean shouldRenderAsCluster(Cluster cluster) {
            // Always render clusters.
            return cluster.getSize() > 2;
        }
    }

    @Override
    public boolean onClusterClick(Cluster<MarkerItem> cluster) {
        // Zoom in the cluster. Need to create LatLngBounds and including all the cluster items
        // inside of bounds, then animate to center of the bounds.

        // Create the builder to collect all essential cluster items for the bounds.
        LatLngBounds.Builder builder = LatLngBounds.builder();
        for (ClusterItem item : cluster.getItems()) {
            builder.include(item.getPosition());
        }
        // Get the LatLngBounds
        final LatLngBounds bounds = builder.build();

        // Animate camera to the bounds
        try {
            getMap().animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return true;
    }

    @Override
    public void onClusterInfoWindowClick(Cluster<MarkerItem> cluster) {
        // Does nothing, but you could go to a list of the users.
    }

    @Override
    public boolean onClusterItemClick(MarkerItem item) {
        ImageCollection collection = item.getCollection();
        // Does nothing, but you could go into the user's profile page, for example.
        Intent intent = new Intent(getActivity(), PhotoGroupActivity.class);
        intent.putExtra("ImageCollection", collection);
        getActivity().startActivity(intent);

        return true;
    }

    @Override
    public void onClusterItemInfoWindowClick(MarkerItem item) {
        // Does nothing, but you could go into the user's profile page, for example.
    }

    protected void initClusterManager(boolean isRestore) {
        if (!isRestore) {
            getMap().moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(37.5111024,127.0249376), 12f));
        }

        Context context = getContext();
        mClusterManager = new ClusterManager<>(context, getMap());
        mClusterManager.setRenderer(new MarkerItemRenderer());

        getMap().setOnCameraIdleListener(mClusterManager);
        getMap().setOnMarkerClickListener(mClusterManager);
        getMap().setOnInfoWindowClickListener(mClusterManager);
        mClusterManager.setOnClusterClickListener(this);
        mClusterManager.setOnClusterInfoWindowClickListener(this);
        mClusterManager.setOnClusterItemClickListener(this);
        mClusterManager.setOnClusterItemInfoWindowClickListener(this);

    }

    public void setUpMap() {
        SupportMapFragment mapFragment =
                (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.mapFragment);
        mapFragment.getMapAsync(this);
    }

    public GoogleMap getMap() {
        return mMap;
    }

    @Override
    public void onMapReady(GoogleMap map) {
        if (mMap != null) {
            return;
        }
        mMap = map;
        initClusterManager(false);

        getMap().clear();
        mClusterManager.clearItems();

        List<ImageCollection> current = collectionViewModel.getImageCollections().getValue();
        collections.addAll(current);

        List<MarkerItem> items = addCollectionMarkers(collections);

        mClusterManager.addItems(items);
        Log.e("MARKER_CLUSTER", "DO CLUSTER!");
        Log.e("MARKER_CLUSTER", collections.size() + "");
        mClusterManager.cluster();
    }

    public List<MarkerItem> addCollectionMarkers(List<ImageCollection> collections) {
        List<MarkerItem> items = new ArrayList<>();

        for (ImageCollection c: collections) {
            List<Image> images = c.getRepImages();
            Image repImage = images.get(0);

            LatLng location = null;
            if (images.size() > 0) {
                double lat = repImage.getLatitude();
                double lng = repImage.getLongitude();

                if (lat == 0.0 || lng == 0.0) {
                    continue;
                } else {
                    location = new LatLng(lat, lng);
                }
            }
            if (location == null) {
                continue;
            }

            items.add(new MarkerItem(location, c));
        }

        return items;
    }
}