package me.emotioncity.places;

import android.content.Context;

import com.hannesdorfmann.mosby.mvp.MvpView;

import io.realm.RealmResults;
import me.emotioncity.domain.remote.model.Place;

/**
 * Created by stream on 09.06.16.
 */
public interface PlacesView extends MvpView {
    void updatePlaceList(final RealmResults<Place> places);
    Context getActivityContext();
    void setRefreshing(boolean refreshing);
    void showNoNetworkMessage();
    void showBadNetworkMessage();
}