package me.emotioncity.chat;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ProgressBar;

/**
 * Created by stream on 12.12.16.
 */

public class MessagesScrollListener extends RecyclerView.OnScrollListener {
    private final ProgressBar progressBar;

    MessagesScrollListener(ProgressBar progressBar){
        this.progressBar = progressBar;
    }

    @Override
    public void onScrollStateChanged(RecyclerView recyclerView, int newState){
        switch (newState) {
            case RecyclerView.SCROLL_STATE_IDLE:
                System.out.println("The RecyclerView is not scrolling");
                progressBar.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                break;
            case RecyclerView.SCROLL_STATE_DRAGGING:
                System.out.println("Scrolling now");
                progressBar.setVisibility(View.VISIBLE);
                break;
            case RecyclerView.SCROLL_STATE_SETTLING:
                System.out.println("Scroll Settling");
                break;

        }

    }

}
