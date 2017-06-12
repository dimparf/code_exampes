package me.emotioncity.chat.info

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.toMaybe
import io.reactivex.rxkotlin.toSingle
import me.emotioncity.EcApplication
import me.emotioncity.R
import me.emotioncity.chat.EcOnMapReadyCallback
import me.emotioncity.dao.PlaceDao
import me.emotioncity.domain.remote.model.Place
import org.jetbrains.anko.find
import javax.inject.Inject


class OnMapFragment : Fragment() {

    @Inject
    lateinit var placeDao: PlaceDao

    lateinit var mMapView: MapView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        EcApplication.getAppComponent().inject(this)
        val view = inflater.inflate(R.layout.on_map_fragment, container, false)

        mMapView = view.find(R.id.chatInfoMapView)
        mMapView.onCreate(savedInstanceState)
        mMapView.onResume()

        MapsInitializer.initialize(activity.applicationContext)

        val placeId = arguments.getString("placeId")

        placeDao.findByIdAsync(placeId)
                .toSingle()
                .filter { person -> person.isLoaded }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { place ->
                    mMapView.getMapAsync(EcOnMapReadyCallback(context, place.name, LatLng(place.latitude, place.longitude)))
                }


        return view
    }

    override fun onStart() {
        super.onStart()
        mMapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mMapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mMapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mMapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mMapView.onLowMemory()
    }

}