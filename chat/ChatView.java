package me.emotioncity.chat;

import com.hannesdorfmann.mosby.mvp.MvpView;

import java.util.List;

import io.realm.RealmResults;
import me.emotioncity.bot.action.Action;
import me.emotioncity.domain.remote.model.Message;
import me.emotioncity.domain.remote.model.Place;
import me.emotioncity.domain.remote.model.Subscription;

/**
 * Created by stream on 21.06.16.
 */
public interface ChatView extends MvpView {
    void scrollToBottom();
    void setSubscriptionState(boolean state);
    void showSubscriptionStatusToast(String placeName, boolean state);
    void showNoNetworkMessage();
    void clearMessageField();
    void loadPlace(Place place, RealmResults<Message> messages,  List<Action> actions);
    void showMessageBuilder();
    void showCommandBar();
    void showMessageBox();
}
