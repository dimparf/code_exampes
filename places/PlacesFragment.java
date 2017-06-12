package me.emotioncity.places;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.hannesdorfmann.mosby.mvp.MvpFragment;

import javax.inject.Inject;

import co.moonmonkeylabs.realmrecyclerview.RealmRecyclerView;
import io.realm.RealmResults;
import me.emotioncity.EcApplication;
import me.emotioncity.Filters;
import me.emotioncity.R;
import me.emotioncity.adapters.PlaceListDividerItemDecoration;
import me.emotioncity.domain.remote.model.Place;

/**
 * Created by stream on 01.02.16.
 */

public class PlacesFragment extends MvpFragment<PlacesView, PlacesPresenter>
        implements PlacesView, SwipeRefreshLayout.OnRefreshListener {
    private static final String TAG = "PlacesFragment";
    private final boolean allowLocation;
    private SwipeRefreshLayout mSwipeLayout;
    private RealmRecyclerView mRecyclerView;
    private PlacesAdapter placesAdapter;
    @Inject
    Context context;
    int lastFirstVisiblePosition;

    public PlacesFragment(){
        super();
        this.allowLocation = false;
    }

    @SuppressLint("ValidFragment")
    public PlacesFragment(boolean allowLocation){
        super();
        this.allowLocation = allowLocation;
    }

    @NonNull
    @Override
    public PlacesPresenter createPresenter() {
        return new PlacesPresenter();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EcApplication.getAppComponent().inject(this);
        //setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_places, container, false);
        mSwipeLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipe_container_places);
        mSwipeLayout.setOnRefreshListener(this);
        mSwipeLayout.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light, android.R.color.holo_orange_light,
                android.R.color.holo_red_light);
        mRecyclerView = (RealmRecyclerView) view.findViewById(R.id.recycler_view_places);
        PlaceListDividerItemDecoration itemDivider =
                new PlaceListDividerItemDecoration(getContext(),
                        PlaceListDividerItemDecoration.VERTICAL_LIST,
                        20,
                        R.id.place_photo);
        mRecyclerView.addItemDecoration(itemDivider);
        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstance) {
        super.onViewCreated(view, savedInstance);
        Log.d(TAG, "onViewCreated");
        presenter.updatePlaceList(allowLocation);
    }

    public void updatePlaceList(RealmResults<Place> places) {
        placesAdapter = new PlacesAdapter(context, places, true, false);
        mRecyclerView.setAdapter(placesAdapter);
    }

    public void filterPlaceList(int sortType, int sortOrder) {
        presenter.filterPlaceList(sortType, sortOrder);
    }

    public void setRefreshing(boolean refreshing) {
        mSwipeLayout.setRefreshing(refreshing);
    }

    public void showNoNetworkMessage() {
        Snackbar.make(mRecyclerView, R.string.no_internet_connection, Snackbar.LENGTH_LONG).show();
    }

    public void showBadNetworkMessage() {
        Snackbar.make(mRecyclerView, R.string.bad_network, Snackbar.LENGTH_LONG).show();
    }

    public Context getActivityContext() {
        return getActivity();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            if (placesAdapter != null) {
                placesAdapter.notifyDataSetChanged();
            }
        }
    }

    @Override
    public void onRefresh() {
        presenter.updatePlaceList(allowLocation);
    }

    @Override
    public void onPause() {
        lastFirstVisiblePosition = ((LinearLayoutManager) mRecyclerView.getRecycleView().getLayoutManager()).findFirstCompletelyVisibleItemPosition();
        super.onPause();
    }

    @Override
    public void onResume() {
        mRecyclerView.getRecycleView().getLayoutManager().scrollToPosition(lastFirstVisiblePosition);
        super.onResume();
    }
}