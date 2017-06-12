package me.emotioncity.chat.info

import android.content.Context
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import me.emotioncity.EcApplication
import me.emotioncity.R
import me.emotioncity.domain.remote.EmotionCityApi
import me.emotioncity.domain.remote.model.Tip
import org.jetbrains.anko.*
import org.jetbrains.anko.cardview.v7.cardView
import javax.inject.Inject


class ReviewsFragment : LceFragment<List<Tip>, ReviewsAdapter, ReviewsViewHolder>() {

    @Inject
    lateinit var api: EmotionCityApi

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        EcApplication.getAppComponent().inject(this)
        init(api.getTips(arguments.getString("placeId")), ReviewsAdapter(context))
    }
}

class ReviewsAdapter(val context: Context) : RecyclerView.Adapter<ReviewsViewHolder>(), AdapterDataSet<List<Tip>> {

    private var tips = listOf<Tip>()

    override fun setData(data: List<Tip>) {
        tips = data
    }

    override fun onBindViewHolder(holder: ReviewsViewHolder, position: Int) {
        holder.bindReview(tips[position])
    }

    override fun getItemCount() = tips.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewsViewHolder =
            ReviewsViewHolder(ReviewsItemUI().createView(AnkoContext.create(context, parent)))

}

class ReviewsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    val reviewTextView: TextView = itemView.find(R.id.reviewTextView)
    val userNameTextView: TextView = itemView.find(R.id.userNameTextView)

    fun bindReview(tip: Tip) {
        with (itemView) {
            userNameTextView.text = tip.userName
            reviewTextView.text = tip.text
        }
    }

}

class ReviewsItemUI : AnkoComponent<ViewGroup> {
    override fun createView(ui: AnkoContext<ViewGroup>) = with(ui) {
        cardView {
            radius = dip(1).toFloat()
            cardElevation = dip(2).toFloat()
            setContentPadding(dip(16), dip(16), dip(16), dip(16))
            cardBackgroundColor = ContextCompat.getColorStateList(context, R.color.cardview_light_background)
            layoutParams = with(FrameLayout.LayoutParams(matchParent, wrapContent)) {
                bottomMargin = dip(4)
                this
            }
            verticalLayout {
                textView {
                    id = R.id.reviewTextView
                    textColor = ContextCompat.getColor(context, R.color.primary_text_default_material_light)
                    lparams { bottomMargin = dip(8) }
                }
                textView {
                    id = R.id.userNameTextView
                    lparams { gravity = Gravity.END }
                }
            }
        }
    }
}