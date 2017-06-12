package me.emotioncity.places.search

import android.content.Context
import android.content.Intent
import android.location.Location
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import cn.nekocode.badge.BadgeDrawable
import co.moonmonkeylabs.realmsearchview.RealmSearchAdapter
import co.moonmonkeylabs.realmsearchview.RealmSearchViewHolder
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.gms.analytics.HitBuilders
import com.google.android.gms.analytics.Tracker
import de.hdodenhof.circleimageview.CircleImageView
import io.nlopez.smartlocation.SmartLocation
import io.nlopez.smartlocation.location.LocationProvider
import io.realm.Realm
import me.emotioncity.AnalyticsTrackers
import me.emotioncity.EcApplication
import me.emotioncity.R
import me.emotioncity.chat.ChatActivity
import me.emotioncity.domain.remote.model.Place
import org.jetbrains.anko.find
import javax.inject.Inject

/**
 * Created by stream on 19.07.16.
 */
class PlaceSearchAdapter(
        private val mContext: Context,
        realm: Realm,
        filterColumnName: String) : RealmSearchAdapter<Place, PlaceSearchAdapter.PlaceListSearchRowHolder>(mContext, realm, filterColumnName) {

    @Inject
    lateinit var tracker: Tracker
    @Inject
    lateinit var provider: LocationProvider

    internal var smartLocation: SmartLocation

    init {
        EcApplication.getAppComponent().inject(this)
        smartLocation = SmartLocation.Builder(mContext).logging(true).preInitialize(true).build()
        val lastLocation = smartLocation.location(provider).lastLocation
        smartLocation.location(provider).oneFix().start { location ->
            Log.d(TAG, "New location is " + location)
            if (location != null) {
                currentLocation = location
            } else if (lastLocation != null) {
                currentLocation = lastLocation
                Log.d(TAG, "Location error!")
            }
        }
    }

    override fun onCreateRealmViewHolder(viewGroup: ViewGroup, viewType: Int): PlaceListSearchRowHolder {
        val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.place_list_item, viewGroup, false)
        return PlaceListSearchRowHolder(view)
    }

    override fun onBindRealmViewHolder(placeListRowHolder: PlaceListSearchRowHolder, position: Int) {
        val place = realmResults[position]

        placeListRowHolder.itemView.setOnClickListener { v ->
            tracker.send(HitBuilders.EventBuilder()
                    .setCategory(AnalyticsTrackers.Category.USER)
                    .setAction(AnalyticsTrackers.Actions.CHAT_IN_FROM_SEARCH)
                    .build())
            val intent = Intent(mContext, ChatActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.putExtra("placeId", place.id)
            mContext.startActivity(intent)
        }

        Glide.with(mContext).load(place.photoUrl)
                .diskCacheStrategy(DiskCacheStrategy.RESULT)
                //.bitmapTransform(CropCircleTransformation(mContext))
                .fitCenter()
                .dontAnimate()
                .placeholder(R.drawable.placeholder)
                .error(R.drawable.placeholder)
                .into(placeListRowHolder.placePhoto)

        placeListRowHolder.placeName.text = place.name

        if (currentLocation != null) {
            val placeLocation = Location("")
            placeLocation.latitude = place.latitude
            placeLocation.longitude = place.longitude
            val distance = currentLocation!!.distanceTo(placeLocation)//in meters
            val badgeDrawable = BadgeDrawable.Builder()
                    .type(BadgeDrawable.TYPE_ONLY_ONE_TEXT)
                    .text1(distance.toInt().toString() + " m")
                    .badgeColor(ContextCompat.getColor(mContext, R.color.colorAccent))
                    .build()
            placeListRowHolder.distanceView.visibility = View.VISIBLE
            placeListRowHolder.distanceView.text = badgeDrawable.toSpannable()
        } else {
            placeListRowHolder.distanceView.visibility = View.INVISIBLE
            Log.d(TAG, "Location is null!")
        }

        placeListRowHolder.categoryView.text = place.category
        placeListRowHolder.workTimeView.text = place.workTime
    }

    inner class PlaceListSearchRowHolder(itemView: View) : RealmSearchViewHolder(itemView) {
        val placePhoto: CircleImageView = itemView.find(R.id.place_photo)
        val distanceView: TextView =  itemView.find(R.id.distance)
        val placeName: TextView = itemView.find(R.id.place_name)
        val categoryView: TextView = itemView.find(R.id.category)
        val workTimeView: TextView = itemView.find(R.id.workTime)
    }

    companion object {
        private val TAG = "PlaceSearchAdapter"
        private var currentLocation: Location? = null
    }

}