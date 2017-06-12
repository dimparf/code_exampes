package me.emotioncity.places

import android.app.Dialog
import android.content.Context
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.BottomSheetDialogFragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckedTextView
import android.widget.RadioGroup
import com.beloo.widget.chipslayoutmanager.ChipsLayoutManager
import com.beloo.widget.chipslayoutmanager.SpacingItemDecoration
import io.realm.Realm
import io.realm.RealmRecyclerViewAdapter
import io.realm.RealmResults
import me.emotioncity.Category
import me.emotioncity.Filters
import me.emotioncity.R
import org.jetbrains.anko.*


class FilterBottomSheetFragment : BottomSheetDialogFragment() {

    lateinit var cancelButton: Button
    lateinit var acceptButton: Button
    lateinit var radioGroupSortType: RadioGroup
    lateinit var radioGroupSortOrder: RadioGroup
    lateinit var categoriesRecycler: RecyclerView

    lateinit var behavior: BottomSheetBehavior<View>
    lateinit var checkedItemsUpdatedListener: OnCheckedItemsUpdatedListener

    companion object {

        val KEY_SELECTED_RADIO_SORT_TYPE_ID = "selected_radio_sort_type_id"
        val KEY_SELECTED_RADIO_SORT_ORDER_ID = "selected_radio_sort_order_id"

    }

    interface OnCheckedItemsUpdatedListener {
        fun onFiltersUpdated(sortType: Int, sortOrder: Int)
    }

    val callback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, slideOffset: Float) {

        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            when (newState) {
                BottomSheetBehavior.STATE_HIDDEN -> {
                    dismiss()
                }
            }
        }
    }

    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val contentView = context.layoutInflater.inflate(R.layout.filter_bottom_sheet_layout, null)

        with(contentView) {
            cancelButton = find(R.id.cancelButton)
            acceptButton = find(R.id.acceptButton)
            categoriesRecycler = find(R.id.categoriesRecycler)
            radioGroupSortType = find(R.id.radiogroup_sort_type)
            radioGroupSortOrder = find(R.id.radiogroup_sort_order)
        }

        dialog.setContentView(contentView)

        setSelectedRadioButtons()
        setupListeners()

        behavior = BottomSheetBehavior.from(contentView.parent as View)
        behavior.setBottomSheetCallback(callback)
        behavior.peekHeight = context.dip(500)
        contentView.requestLayout()

    }

    private fun setSelectedRadioButtons() {
        val preferences = activity.getPreferences(Context.MODE_PRIVATE)
        radioGroupSortType.check(preferences.getInt(KEY_SELECTED_RADIO_SORT_TYPE_ID, R.id.radio_sort_distance))
        radioGroupSortOrder.check(preferences.getInt(KEY_SELECTED_RADIO_SORT_ORDER_ID, R.id.radio_sort_ascending))

    }

    private fun setupListeners() {
        cancelButton.setOnClickListener {
            dismiss()
        }

        acceptButton.setOnClickListener {
            val sortType = if (radioGroupSortType.checkedRadioButtonId == R.id.radio_sort_alphabetical) {
                Filters.SORT_ALPHABETICAL
            } else {
                Filters.SORT_NUMERIC
            }

            val sortOrder = if (radioGroupSortOrder.checkedRadioButtonId == R.id.radio_sort_ascending) {
                Filters.SORT_ASCENDING
            } else {
                Filters.SORT_DESCENDING
            }

            checkedItemsUpdatedListener.onFiltersUpdated(sortType, sortOrder)
            dismiss()
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        checkedItemsUpdatedListener = context as OnCheckedItemsUpdatedListener
    }

    override fun onResume() {
        super.onResume()
        Realm.getDefaultInstance().use {
            val categories = it.where(Category::class.java).findAllAsync()
            categories.addChangeListener { collection, _ ->
                setupRecycler(collection)
                categories.removeAllChangeListeners()
            }
        }
    }

    private fun setupRecycler(categories: RealmResults<Category>) {
        categoriesRecycler.layoutManager = ChipsLayoutManager.newBuilder(context)
                .setChildGravity(Gravity.TOP)
                .setGravityResolver { Gravity.CENTER }
                .setOrientation(ChipsLayoutManager.HORIZONTAL)
                .setRowStrategy(ChipsLayoutManager.STRATEGY_CENTER_DENSE)
                .withLastRow(true)
                .build()
        categoriesRecycler.addItemDecoration(SpacingItemDecoration(48, 48))
        categoriesRecycler.adapter = CategoriesAdapter(context, categories)
    }

    override fun onDestroy() {
        activity.getPreferences(Context.MODE_PRIVATE).edit()
                .putInt(KEY_SELECTED_RADIO_SORT_TYPE_ID, radioGroupSortType.checkedRadioButtonId)
                .putInt(KEY_SELECTED_RADIO_SORT_ORDER_ID, radioGroupSortOrder.checkedRadioButtonId)
                .apply()
        super.onDestroy()
    }
}


class CategoriesAdapter(val context: Context, categories: RealmResults<Category>) : RealmRecyclerViewAdapter<Category, CategoriesViewHolder>(categories, true) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            CategoriesViewHolder(CategoryListItemUI().createView(AnkoContext.create(parent.context, parent)))

    override fun onBindViewHolder(holder: CategoriesViewHolder, position: Int) {
        val item = getItem(position)
        if (item != null) holder.bind(item)
    }

}

class CategoryListItemUI : AnkoComponent<ViewGroup> {

    override fun createView(ui: AnkoContext<ViewGroup>) = with(ui) {
        checkedTextView {
            padding = dip(12)
            background = ContextCompat.getDrawable(context, R.drawable.filter_tag)
            setTextColor(ContextCompat.getColorStateList(context, R.color.category_textview_color))
        }
    }
}

class CategoriesViewHolder(val textView: CheckedTextView) : RecyclerView.ViewHolder(textView) {

    fun bind(category: Category) {
        textView.text = category.name
        textView.isChecked = category.isChecked

        textView.setOnClickListener {
            textView.isChecked = !category.isChecked
            Realm.getDefaultInstance().use {
                it.executeTransaction {
                    category.isChecked = !category.isChecked
                }
            }
        }

    }

}
