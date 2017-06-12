package me.emotioncity.places.search;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import javax.inject.Inject;

import co.moonmonkeylabs.realmsearchview.RealmSearchView;
import io.realm.Case;
import me.emotioncity.EcApplication;
import me.emotioncity.R;
import me.emotioncity.adapters.PlaceListDividerItemDecoration;
import me.emotioncity.dao.PlaceDao;

/**
 * Created by stream on 19.07.16.
 */
public class SearchActivity extends AppCompatActivity {

    @Inject
    PlaceDao placeDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EcApplication.getAppComponent().inject(this);
        setContentView(R.layout.search_activity);
        RealmSearchView realmSearchView = (RealmSearchView) findViewById(R.id.search_view);

        PlaceSearchAdapter placeSearchAdapter = new PlaceSearchAdapter(this, placeDao.getRealm(), "category");
        placeSearchAdapter.setUseContains(true);
        placeSearchAdapter.setSortKey("distance");
        realmSearchView.setAdapter(placeSearchAdapter);
        PlaceListDividerItemDecoration itemDivider =
                new PlaceListDividerItemDecoration(this,
                        PlaceListDividerItemDecoration.VERTICAL_LIST,
                        20,
                        R.id.place_photo);
        realmSearchView.getRealmRecyclerView().addItemDecoration(itemDivider);
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
