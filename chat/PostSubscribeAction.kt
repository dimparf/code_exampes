package me.emotioncity.chat

import io.reactivex.functions.Consumer
import io.realm.Realm
import me.emotioncity.domain.remote.model.Place
import me.emotioncity.domain.remote.model.Subscription
import me.emotioncity.domain.remote.model.response.SubscriptionStatusView

/**
 * Created by stream on 08.12.16.
 */

class PostSubscribeAction(private val place: Place) : Consumer<SubscriptionStatusView> {

    override fun accept(statusView: SubscriptionStatusView) {
        Realm.getDefaultInstance().use {
            if (statusView.status) {
                val subscription = Subscription()
                subscription.id = statusView.subscriptionId
                subscription.placeId = statusView.placeId
                subscription.street = place.street
                subscription.city = place.city
                subscription.category = place.category
                subscription.chatToken = place.chatToken
                subscription.latitude = place.latitude
                subscription.longitude = place.longitude
                subscription.photoUrl = place.photoUrl
                subscription.name = place.name
                subscription.openChannel = statusView.openChannel
                it.executeTransactionAsync { realm ->
                    realm.copyToRealmOrUpdate(subscription)
                }
            } else {
                it.executeTransactionAsync { realm ->
                    realm.where(Subscription::class.java).equalTo("placeId", place.id).findFirst()?.deleteFromRealm()
                }
            }
        }
    }

}

