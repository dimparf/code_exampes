package me.emotioncity.chat.info

import android.content.Context
import com.hannesdorfmann.mosby.mvp.MvpNullObjectBasePresenter
import me.emotioncity.EcApplication
import me.emotioncity.dao.PlaceDao
import me.emotioncity.domain.remote.model.Place
import me.emotioncity.domain.remote.model.response.SubscriptionStatusView
import me.emotioncity.subscriptions.OnSubscriptionListener
import me.emotioncity.subscriptions.SubscriptionsHandler
import javax.inject.Inject


class PlacePresenter : MvpNullObjectBasePresenter<PlaceView>(), OnSubscriptionListener {

    @Inject
    lateinit var placeDao: PlaceDao

    lateinit var place: Place

    private val subHandler by lazy { SubscriptionsHandler().apply { setSubscriptionListener(this@PlacePresenter) } }

    override fun attachView(view: PlaceView?) {
        super.attachView(view)
        EcApplication.getAppComponent().inject(this)
    }

    fun loadPlace(placeId: String) {
        place = placeDao.realm.copyFromRealm(placeDao.findById(placeId))
        view.updatePlaceScreen(place)
    }

    fun sendSubscribeStatusToServerAndNotify(context: Context) {
        subHandler.use { it.subscribe(context, place, true) }
    }

    override fun onSubscriptionStatusChanged(statusView: SubscriptionStatusView) {
        view.setSubscriptionState(statusView.openChannel)
        if (statusView.status && statusView.openChannel) {
            view.showSubscriptionStatusToast(place.name, statusView.status)
        }
    }

    override fun onSubscriptionFailed() {
        view.showNoNetworkMessage()
    }

    override fun detachView(retainInstance: Boolean) {
        super.detachView(retainInstance)
    }
}