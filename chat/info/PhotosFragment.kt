package me.emotioncity.chat.info

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.bumptech.glide.Glide
import me.emotioncity.EcApplication
import me.emotioncity.R
import me.emotioncity.domain.remote.EmotionCityApi
import me.emotioncity.domain.remote.model.Photo
import org.jetbrains.anko.*
import java.util.*
import javax.inject.Inject


class PhotosFragment : LceFragment<List<Photo>, PhotosAdapter, PhotosViewHolder>() {

    @Inject
    lateinit var api: EmotionCityApi

    override fun onStart() {
        super.onStart()
        EcApplication.getAppComponent().inject(this)
        init(api.getPhotos(arguments.getString("placeId")), PhotosAdapter(context))
    }
}

class PhotosAdapter(val context: Context) : RecyclerView.Adapter<PhotosViewHolder>(), AdapterDataSet<List<Photo>> {

    private var photos = listOf<Photo>()

    override fun setData(data: List<Photo>) {
        photos = data
    }

    override fun getItemCount() = photos.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            PhotosViewHolder(PhotosItemUI().createView(AnkoContext.create(context, parent)))

    override fun onBindViewHolder(holder: PhotosViewHolder, position: Int) {
        Glide.with(context)
                .load(photos[position].url)
                .placeholder(R.drawable.placeholder)
                .error(R.drawable.placeholder)
                .centerCrop()
                .dontAnimate()
                .into(holder.imageView)
    }
}

class PhotosViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

class PhotosItemUI : AnkoComponent<View> {

    override fun createView(ui: AnkoContext<View>) = with(ui) {
        imageView {
            layoutParams = with(FrameLayout.LayoutParams(matchParent, dip(182))) {
                margin = dip(4)
                this
            }
        }
    }
}