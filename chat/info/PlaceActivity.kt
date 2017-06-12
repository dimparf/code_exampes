package me.emotioncity.chat.info

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.app.FragmentStatePagerAdapter
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.hannesdorfmann.mosby.mvp.MvpActivity
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import jp.wasabeef.glide.transformations.ColorFilterTransformation
import kotlinx.android.synthetic.main.chat_item_rcv.*
import kotlinx.android.synthetic.main.place_screen.*
import me.emotioncity.EcApplication
import me.emotioncity.R
import me.emotioncity.chat.ChatActivity
import me.emotioncity.dao.SubscriptionDao
import me.emotioncity.domain.remote.model.Place
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.support.v4.withArguments
import javax.inject.Inject


class PlaceActivity : MvpActivity<PlaceView, PlacePresenter>(), PlaceView {

    @Inject
    lateinit var subscriptionDao: SubscriptionDao

    private val placeId: String by lazy { intent.getStringExtra("placeId") }

    private lateinit var subMenuItem: MenuItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.place_screen)
        EcApplication.getAppComponent().inject(this)

        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_arrow_left_white_24dp)
        toolbar.setNavigationOnClickListener { finish() }

        writeUsButton.setOnClickListener {
            startActivity<ChatActivity>("placeId" to placeId, "placePhotoUrl" to presenter.place.photoUrl)
        }

        presenter.loadPlace(placeId)
    }

    override fun createPresenter() = PlacePresenter()

    override fun updatePlaceScreen(place: Place) {
        Glide.with(this)
                .load(place.photoUrl)
                .crossFade()
                .bitmapTransform(CenterCrop(this), ColorFilterTransformation(this, Color.argb(96, 61, 61, 61)))
                .error(R.drawable.placeholder)
                .into(placeImage)
        placeName.text = place.name
        placeCategory.text = place.category
        placeWorkTime.text = place.workTime

        viewPager.adapter = ViewPagerAdapter(this, supportFragmentManager, place.id)
        tabLayout.setupWithViewPager(viewPager)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.place_screen_menu, menu)
        subMenuItem = menu.findItem(R.id.action_subscribe)
        setSubscriptionState(subscriptionDao.placeIsSubscribed(placeId))
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.home -> {
                onBackPressed()
                return true
            }
            R.id.action_subscribe -> {
                presenter.sendSubscribeStatusToServerAndNotify(this)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun showNoNetworkMessage() {
        Snackbar.make(messageView, R.string.no_internet_connection, Snackbar.LENGTH_LONG).show()
    }

    override fun showSubscriptionStatusToast(placeName: String, state: Boolean) {
        val message = if (state) resources.getText(R.string.subscription_notificate) as String + " " + placeName
        else resources.getText(R.string.unsubscription_notificate) as String + " " + placeName
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun setSubscriptionState(state: Boolean) {
        val bookmark = if (state) MaterialDesignIconic.Icon.gmi_bookmark
        else MaterialDesignIconic.Icon.gmi_bookmark_outline
        subMenuItem.icon = IconicsDrawable(this)
                .icon(bookmark)
                .color(Color.WHITE)
                .sizeDp(24)
    }

    class ViewPagerAdapter(val context: Context, fragmentManager: FragmentManager, val placeId: String) : FragmentPagerAdapter(fragmentManager) {

        override fun getItem(position: Int): Fragment = when (position) {
            0 -> {
                PhotosFragment().withArguments("placeId" to placeId)
            }
            1 -> {
                ReviewsFragment().withArguments("placeId" to placeId)
            }
            2 -> {
                OnMapFragment().withArguments("placeId" to placeId)
            }
            else -> {
                Fragment()
            }
        }

        override fun getCount() = 3

        override fun getPageTitle(position: Int): CharSequence = when (position) {
            0 -> {
                context.getString(R.string.photos)
            }
            1 -> {
                context.getString(R.string.reviews)
            }
            2 -> {
                context.getString(R.string.on_map)
            }
            else -> {
                "Упс, ошибка"
            }
        }


    }

}