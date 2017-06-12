package me.emotioncity.chat;

import android.content.Context;
import android.location.Location;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import me.emotioncity.EcApplication;
import me.emotioncity.R;

/**
 * Created by stream on 05.12.16.
 */

public class EcOnMapReadyCallback  implements OnMapReadyCallback {
    private final LatLng placeLocation;
    private final Context context;
    private final String placeName;

    public EcOnMapReadyCallback(final Context context, final String placeName, final LatLng placeLocation) {
        this.placeName = placeName;
        this.placeLocation = placeLocation;
        this.context = context;
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        GoogleMap mMap = googleMap;
        UiSettings settings = mMap.getUiSettings();
        settings.setAllGesturesEnabled(true);
        settings.setCompassEnabled(true);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(placeLocation, 16));
        Marker placeMarker = mMap.addMarker(new MarkerOptions()
                .position(placeLocation)
                .title(placeName)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
        placeMarker.showInfoWindow();
        Location userLocation = EcApplication.currentLocation;
        if (userLocation != null) {
            Marker userLocationMarker = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(userLocation.getLatitude(), userLocation.getLongitude()))
                    .title(context.getString(R.string.your_location))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            userLocationMarker.showInfoWindow();
        }
    }
}
