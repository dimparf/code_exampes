package me.emotioncity.chat.info

import com.hannesdorfmann.mosby.mvp.MvpView
import me.emotioncity.domain.remote.model.Place


interface PlaceView : MvpView {

    fun showNoNetworkMessage()
    fun updatePlaceScreen(place: Place)
    fun setSubscriptionState(state: Boolean)
    fun showSubscriptionStatusToast(placeName: String, state: Boolean)

}