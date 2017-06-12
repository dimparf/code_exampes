package me.emotioncity.places

import android.content.Context
import android.content.Intent
import android.support.v4.content.ContextCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import cn.nekocode.badge.BadgeDrawable
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.gms.analytics.HitBuilders
import com.google.android.gms.analytics.Tracker
import de.hdodenhof.circleimageview.CircleImageView
import io.realm.RealmBasedRecyclerViewAdapter
import io.realm.RealmResults
import io.realm.RealmViewHolder
import me.emotioncity.AnalyticsTrackers
import me.emotioncity.EcApplication
import me.emotioncity.R
import me.emotioncity.chat.ChatActivity
import me.emotioncity.domain.remote.model.Place
import javax.inject.Inject

class PlacesAdapter internal constructor(
        private val mContext: Context,
        realmResults: RealmResults<Place>,
        automaticUpdate: Boolean,
        animateIdType: Boolean) : RealmBasedRecyclerViewAdapter<Place, PlacesAdapter.PlaceListRowHolder>(mContext, realmResults, automaticUpdate, animateIdType) {

    @Inject
    lateinit var tracker: Tracker

    init {
        EcApplication.getAppComponent().inject(this)
    }

    override fun onCreateRealmViewHolder(parent: ViewGroup, viewType: Int): PlaceListRowHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.place_list_item, parent, false)
        return PlaceListRowHolder(view)
    }

    override fun onBindRealmViewHolder(placeListRowHolder: PlaceListRowHolder, position: Int) {
        val place = realmResults[position]
        //Log.d(TAG, place.toString());

        placeListRowHolder.itemView.setOnClickListener { v ->
            tracker.send(HitBuilders.EventBuilder()
                    .setCategory(AnalyticsTrackers.Category.USER)
                    .setAction(AnalyticsTrackers.Actions.CHAT_IN)
                    .build())
            val intent = Intent(mContext, ChatActivity::class.java)
            intent.putExtra("placeId", place.id)
            intent.putExtra("placePhotoUrl", place.photoUrl)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            mContext.startActivity(intent)
        }

        Glide.with(mContext).load(place.photoUrl)
                .diskCacheStrategy(DiskCacheStrategy.RESULT)
                .fitCenter()
                .dontAnimate()
                .placeholder(R.drawable.placeholder)
                .error(R.drawable.placeholder)
                .into(placeListRowHolder.placePhoto)

        placeListRowHolder.placeName.text = place.name
        val distance = place.distance.toInt()
        if (distance != 0) {
            val badgeDrawable = BadgeDrawable.Builder()
                    .type(BadgeDrawable.TYPE_ONLY_ONE_TEXT)
                    .text1(distance.toString() + " m")
                    .badgeColor(ContextCompat.getColor(mContext, R.color.colorAccent))
                    .build()
            placeListRowHolder.distanceView.visibility = View.VISIBLE
            placeListRowHolder.distanceView.text = badgeDrawable.toSpannable()
        } else {
            placeListRowHolder.distanceView.visibility = View.INVISIBLE
        }

        placeListRowHolder.categoryView.text = place.category
        placeListRowHolder.workTimeView.text = place.workTime
    }

    inner class PlaceListRowHolder(itemView: View) : RealmViewHolder(itemView) {
        var placePhoto: CircleImageView
        var distanceView: TextView
        var placeName: TextView
        var categoryView: TextView
        var workTimeView: TextView

        init {
            placePhoto = itemView.findViewById(R.id.place_photo) as CircleImageView
            distanceView = itemView.findViewById(R.id.distance) as TextView
            placeName = itemView.findViewById(R.id.place_name) as TextView
            categoryView = itemView.findViewById(R.id.category) as TextView
            workTimeView = itemView.findViewById(R.id.workTime) as TextView
        }

    }

}
