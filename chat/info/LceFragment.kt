package me.emotioncity.chat.info

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import me.emotioncity.R
import me.emotioncity.extensions.makeGone
import me.emotioncity.extensions.makeVisible
import org.jetbrains.anko.*
import org.jetbrains.anko.recyclerview.v7.recyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

interface AdapterDataSet<T> {

    fun setData(data: T)

}

abstract class LceFragment<D, A, VH : RecyclerView.ViewHolder> : Fragment()
    where A : RecyclerView.Adapter<VH>, A : AdapterDataSet<D> {


    lateinit var loadingView: ProgressBar
    lateinit var contentView: RecyclerView
    lateinit var errorView: ViewGroup
    lateinit var reloadButton: Button

    lateinit var call: Call<D>
    lateinit var adapter: A

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = LceFragmentUI().createView(AnkoContext.create(context, this))
        contentView = view.find(R.id.recycler)
        loadingView = view.find(R.id.loadingView)
        errorView = view.find(R.id.errorView)
        reloadButton = view.find(R.id.reloadButton)
        return view
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        showLoading()

        reloadButton.setOnClickListener {
            showLoading()
            loadData()
        }
    }

    fun init(call: Call<D>, adapter: A) {
        this.call = call
        this.adapter = adapter
        contentView.adapter = adapter
        loadData()
    }

    private fun setData(data: D) {
        showContent()
        adapter.setData(data)
        adapter.notifyDataSetChanged()
    }

    private fun loadData() {
        call.enqueue(object : Callback<D> {
            override fun onResponse(call: Call<D>, response: Response<D>) {
                if (response.isSuccessful) {
                    setData(response.body())
                } else {
                    showError()
                }
            }

            override fun onFailure(call: Call<D>, t: Throwable) {
                showError()
            }
        })
    }

    private fun showContent() {
        contentView.makeVisible()
        errorView.makeGone()
        loadingView.makeGone()
    }

    private fun showLoading() {
        loadingView.makeVisible()
        contentView.makeGone()
        errorView.makeGone()
    }

    private fun showError() {
        errorView.makeVisible()
        loadingView.makeGone()
        contentView.makeGone()
    }
}


class LceFragmentUI : AnkoComponent<Fragment> {
    override fun createView(ui: AnkoContext<Fragment>): View = with(ui) {
        frameLayout {
            lparams(matchParent, matchParent)
            progressBar {
                id = R.id.loadingView
                isIndeterminate = true
                lparams {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dip(128)
                }
            }
            recyclerView {
                id = R.id.recycler
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                lparams(matchParent, matchParent)
            }
            verticalLayout {
                id = R.id.errorView
                textView(text = R.string.bad_network) {
                    lparams { bottomMargin = dip(8) }
                }
                button(text = R.string.try_again) {
                    id = R.id.reloadButton
                    lparams { gravity = Gravity.CENTER }
                }
            }.lparams {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dip(128)
            }
        }
    }
}