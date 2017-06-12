package me.emotioncity.places

import android.location.Location
import android.util.Log
import com.hannesdorfmann.mosby.mvp.MvpBasePresenter
import io.nlopez.smartlocation.SmartLocation
import io.nlopez.smartlocation.location.LocationProvider
import io.nlopez.smartlocation.rx.ObservableFactory
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import io.realm.Sort
import me.emotioncity.Category
import me.emotioncity.EcApplication
import me.emotioncity.Filters
import me.emotioncity.auth.AuthPreferences
import me.emotioncity.dao.PlaceDao
import me.emotioncity.domain.remote.EmotionCityApi
import me.emotioncity.domain.remote.model.Place
import me.emotioncity.extensions.isNetworkAvailable
import me.emotioncity.utils.RxUtils
import javax.inject.Inject


/**
 * Created by stream on 09.06.16.
 */
class PlacesPresenter : MvpBasePresenter<PlacesView>() {
    private val TAG = PlacesPresenter::class.java.simpleName
    @Inject
    lateinit var api: EmotionCityApi
    @Inject
    lateinit var placeDao: PlaceDao
    @Inject
    lateinit var authPreferences: AuthPreferences
    @Inject
    lateinit var provider: LocationProvider

    private var placesSubscription: Disposable? = null
    private var placeRequestSubscription: Disposable? = null

    fun filterPlaceList(sortType: Int, sortOrder: Int) {
        Realm.getDefaultInstance().use {
            val cats = it.where(Category::class.java).equalTo("isChecked", true).findAllAsync()
            cats.addChangeListener { collection, _ ->
                val places = if (collection.isEmpty()) {
                    it.where(Place::class.java).findAllAsync()
                } else {
                    it.where(Place::class.java).
                            `in`("category", cats.map { it.name }.toTypedArray())
                            .findAllAsync()
                }
                places.addChangeListener { collection, _ ->
                    val fieldName = if (sortType == Filters.SORT_ALPHABETICAL) {
                        "name"
                    } else {
                        "distance"
                    }

                    val ascending = if (sortOrder == Filters.SORT_ASCENDING) {
                        Sort.ASCENDING
                    } else {
                        Sort.DESCENDING
                    }

                    view?.updatePlaceList(collection.sort(fieldName, ascending))
                    view!!.setRefreshing(false)
                    places.removeAllChangeListeners()
                }
                cats.removeAllChangeListeners()
            }
        }
    }

    fun updatePlaceList(allowLocation: Boolean) {
        updateCategories()
        Realm.getDefaultInstance().use {
            val places = it.where(Place::class.java).findAllSortedAsync("distance")
            places.addChangeListener { collection, _ ->
                view?.updatePlaceList(collection)
                updateLocationAndLoad(allowLocation)
                places.removeAllChangeListeners()
            }
        }
    }

    private fun updateCategories() {
        val categories = Filters.categories
        Realm.getDefaultInstance().use {
            it.executeTransactionAsync {
                it.insertOrUpdate(categories)
            }
        }
    }

    private fun updateLocationAndLoad(allowLocation: Boolean) {
        if (allowLocation) {
            val smartLocation = SmartLocation.Builder(view!!.activityContext).logging(true).build()
            val locationControl = smartLocation.location(provider)
            val lastLocation = locationControl.lastLocation
            placeRequestSubscription = ObservableFactory.from(locationControl.oneFix()).subscribe { location ->
                Log.d(TAG, "New location is " + location)
                if (location != null) {
                    EcApplication.currentLocation = location
                } else if (lastLocation != null) {
                    EcApplication.currentLocation = lastLocation
                    Log.d(TAG, "Location error, use lastLocation")
                } else {
                    Log.d(TAG, "Location is null")
                }
                if (authPreferences.city != null) {
                    Log.d(TAG, "Get city from preferences")
                    attemptLoadPlacesFromApi()
                } else {
                    smartLocation.geocoding().reverse(EcApplication.currentLocation) { original, addresses ->
                        if (!addresses.isEmpty()) {
                            val city = addresses[0].locality
                            Log.d(TAG, "Current city is " + city)
                            authPreferences.city = city
                            attemptLoadPlacesFromApi()
                        }
                    }
                }
            }
        } else {
            attemptLoadPlacesFromApi()
        }
    }

    private fun attemptLoadPlacesFromApi() {
        if (view?.activityContext?.isNetworkAvailable() ?: false) {
            view?.setRefreshing(true)
            val city = if (authPreferences.city == null) "Владивосток" else authPreferences.city //TODO stub, add city selector for user
            placesSubscription = api.getPlaces(city)
                    .subscribeOn(Schedulers.io())
                    .doOnNext { placeList ->
                        val places = placeList.places.map { place ->
                            val placeLocation = Location("")
                            placeLocation.latitude = place.latitude
                            placeLocation.longitude = place.longitude
                            if (EcApplication.currentLocation != null && !place.name.equals("Servestr", ignoreCase = true)) {
                                val floatDistance = EcApplication.currentLocation.distanceTo(placeLocation)
                                place.distance = floatDistance.toDouble()
                            }
                            place
                        }
                        Realm.getDefaultInstance().use { realm ->
                            realm.executeTransaction { realm ->
                                realm.copyToRealmOrUpdate(places)
                            }
                        }
                    }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            {
                                places ->
                                val savedPlaces = placeDao.findAll()
                                if (view != null) {
                                    Log.d(TAG, "Update place list")
                                    view!!.updatePlaceList(savedPlaces)
                                    view!!.setRefreshing(false)
                                }
                            },
                            {
                                error ->
                                error.printStackTrace()
                                Log.e(TAG, "Error retrieve place list: " + error.cause)
                                view!!.setRefreshing(false)
                                view!!.showBadNetworkMessage()
                            }
                    )
        } else {
            view!!.showNoNetworkMessage()
            view!!.setRefreshing(false)
        }
    }

    override fun attachView(view: PlacesView) {
        EcApplication.getAppComponent().inject(this)
        super.attachView(view)
    }

    override fun detachView(retainInstance: Boolean) {
        RxUtils.unsubscribe(placesSubscription, placeRequestSubscription)
        placeDao.realm.close()
        super.detachView(retainInstance)
    }

}

