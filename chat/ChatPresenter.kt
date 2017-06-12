package me.emotioncity.chat

import android.content.Context
import android.content.Intent
import android.support.v4.util.Pair
import android.util.Log
import com.google.android.gms.analytics.HitBuilders
import com.google.android.gms.analytics.Tracker
import com.google.flatbuffers.FlatBufferBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hannesdorfmann.mosby.mvp.MvpBasePresenter
import io.realm.Realm
import me.emotioncity.AnalyticsTrackers
import me.emotioncity.EcApplication
import me.emotioncity.R
import me.emotioncity.auth.AuthPreferences
import me.emotioncity.bot.BotTests
import me.emotioncity.bot.action.Action
import me.emotioncity.dao.ActivationDao
import me.emotioncity.dao.MessageDao
import me.emotioncity.dao.PlaceDao
import me.emotioncity.domain.PhotoCounter
import me.emotioncity.domain.TipsCounter
import me.emotioncity.domain.remote.model.Message
import me.emotioncity.domain.remote.model.Place
import me.emotioncity.domain.remote.model.flatbuffers.ChatMessage
import me.emotioncity.domain.remote.model.flatbuffers.MType
import me.emotioncity.domain.remote.model.flatbuffers.MType.*
import me.emotioncity.domain.remote.model.response.SubscriptionStatusView
import me.emotioncity.mqtt.ChatService
import me.emotioncity.mqtt.MqttServiceConnection
import me.emotioncity.subscriptions.OnSubscriptionListener
import me.emotioncity.subscriptions.SubscriptionsHandler
import me.emotioncity.utils.CryptoUtils.conversationId
import me.emotioncity.utils.CryptoUtils.md5
import java.io.UnsupportedEncodingException
import java.security.NoSuchAlgorithmException
import java.util.*
import javax.inject.Inject

/**
 * Created by stream on 21.06.16.
 */
class ChatPresenter internal constructor() : MvpBasePresenter<ChatView>(), OnSubscriptionListener {
    @Inject
    lateinit var messageDao: MessageDao
    @Inject
    lateinit var placeDao: PlaceDao
    @Inject
    lateinit var authPreferences: AuthPreferences
    @Inject
    lateinit var context: Context
    @Inject
    lateinit var mConnection: MqttServiceConnection
    @Inject
    lateinit var tracker: Tracker
    @Inject
    lateinit var activationDao: ActivationDao
    @Inject
    lateinit var gson: Gson

    private lateinit var conversationId: String


    private var userChatToken: String? = null
    private var placeChatToken: String? = null
    private var placeId: String? = null
    private var placeName: String? = null
    private lateinit var place: Place

    private val subHandler by lazy { SubscriptionsHandler().apply { setSubscriptionListener(this@ChatPresenter) } }

    internal fun loadPlace(placeId: String) {
        this.place = placeDao.realm.copyFromRealm(placeDao.findById(placeId))
        this.placeId = placeId
        this.placeName = place.name
        placeChatToken = place.chatToken
        Log.d(TAG, "User chat token: " + userChatToken!!)
        Log.d(TAG, "Place chat token: " + placeChatToken!!)
        conversationId = conversationId(userChatToken, placeChatToken)
        val messages = messageDao.findByConversationId(conversationId)
        Log.d(TAG, "ActionPack: " + place.actions)
        if (messages.isEmpty()) {
            var welcomeMessageBody = ""
            if (place.workTime != null && !place.workTime.trim { it <= ' ' }.isEmpty()) {
                welcomeMessageBody = place.info
            } else {
                welcomeMessageBody = "Можете задавать любые вопросы в этом чате :)\n\n" + place.info
            }

            val welcomeMessage = Message.Builder(RCV)
                    .body(welcomeMessageBody)
                    .timestamp(Date().time)
                    .userToken(userChatToken)
                    .placeToken(placeChatToken).build()
            messageDao.create(welcomeMessage)
        }
        if (isViewAttached) {
            messages.addChangeListener { element ->
                if (view != null) {
                    view!!.scrollToBottom()
                }
            }
            EcMessageArrivedListener.activeConversationId = conversationId
            val stringActions = place.actions
            val actionsType = object : TypeToken<List<Action>>() {}.type
            val inActions = gson.fromJson<List<Action>>(stringActions, actionsType)
            val actions: List<Action> = if (place.name == "Servestr" || inActions.isNotEmpty()) {
                Log.d("ChatActivity", "Serverstr!")
                inActions
            } else {
                BotTests.empty
            }
            view!!.loadPlace(place, messages, actions)
        }
    }

    internal fun sendMessage(buttonId: Int, textMessage: String) {
        var textMessage = textMessage
        val topicAndType = detectMessageTypeAndTopic(buttonId)
        val topic = topicAndType.first
        val type = topicAndType.second
        Log.i(TAG, "Message type is: " + Message.messageName(type))
        val chatView = view
        if (chatView != null && topic != null) {
            try {
                Log.d(TAG, "Conversation id in sendMessage: " + conversationId)
                val sendId = md5(Date().time.toString() + "" + userChatToken)
                val timestamp = Date().time

                saveMessageToDb(type, textMessage, sendId, conversationId, timestamp)
                textMessage = processReviewOrImageMessage(textMessage, type)
                subscribeIfFirstMessage()
                chatView.clearMessageField()
                chatView.scrollToBottom()

                if (type != MType.MAP) {
                    val builder = FlatBufferBuilder(0)
                    val sendIdNew = builder.createString(sendId)
                    val conversationIdNew = builder.createString(conversationId)
                    val body = builder.createString(textMessage)
                    ChatMessage.startChatMessage(builder)
                    ChatMessage.addCreatedAt(builder, timestamp)
                    ChatMessage.addMType(builder, type)
                    ChatMessage.addBody(builder, body)
                    ChatMessage.addConversationId(builder, conversationIdNew)
                    ChatMessage.addSendId(builder, sendIdNew)
                    val chatMessage = ChatMessage.endChatMessage(builder)
                    builder.finish(chatMessage)

                    val data = builder.sizedByteArray()

                    Log.i(TAG, "Send message " + Message.messageName(type) + " to topic " + topic)
                    val ownerTopic = activationDao.activation?.placeChatToken
                    if (ownerTopic != "place/+" || topic.contentEquals("service")) {
                        mConnection.chatService.sendMessage(data, topic)
                    }
                }
            } catch (e: NoSuchAlgorithmException) {
                e.printStackTrace()
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
            }

        }
    }

    private fun processReviewOrImageMessage(textMessage: String, type: Int?): String {
        var textMessage1 = textMessage
        val realm = Realm.getDefaultInstance()
        if (type == REVIEW) {
            realm.executeTransaction { realm ->
                var tipsCounter: TipsCounter? = realm.where(TipsCounter::class.java).equalTo("placeId", placeId).findFirst()
                if (tipsCounter == null) {
                    tipsCounter = TipsCounter(placeId, 0, null)
                    val inDbTipsCounter = realm.where(TipsCounter::class.java).equalTo("placeId", tipsCounter.placeId).findFirst()
                    if (inDbTipsCounter != null) {
                        Log.d(TAG, "Update tips with placeId: " + inDbTipsCounter.placeId)
                        realm.copyToRealmOrUpdate<TipsCounter>(tipsCounter)
                    } else {
                        Log.d(TAG, "Create new tips with placeId: " + tipsCounter.placeId)
                        realm.copyToRealm<TipsCounter>(tipsCounter)
                    }
                    textMessage1 = String.format("{\"placeId\": \"%s\", \"position\": %s}", placeId, 0)
                } else {
                    tipsCounter.position = tipsCounter.position + 1
                    textMessage1 = String.format("{\"placeId\": \"%s\", \"position\": %s}", placeId, tipsCounter.position)
                }
            }
        } else if (type == IMAGE) {
            realm.executeTransaction { realm ->
                var photoCounter: PhotoCounter? = realm.where(PhotoCounter::class.java).equalTo("placeId", placeId).findFirst();
                if (photoCounter == null) {
                    photoCounter = PhotoCounter(placeId, 0, null)
                    val inDbPhotoCounter = realm.where(PhotoCounter::class.java).equalTo("placeId", photoCounter.placeId).findFirst()
                    if (inDbPhotoCounter != null) {
                        Log.d(TAG, "Update photos with placeId: " + inDbPhotoCounter.placeId)
                        realm.copyToRealmOrUpdate<PhotoCounter>(photoCounter)
                    } else {
                        Log.d(TAG, "Create new photos with placeId: " + photoCounter.placeId)
                        realm.copyToRealm<PhotoCounter>(photoCounter)
                    }
                    textMessage1 = String.format("{\"placeId\": \"%s\", \"position\": %s}", placeId, 0)
                } else {
                    photoCounter.position = photoCounter.position + 1
                    textMessage1 = String.format("{\"placeId\": \"%s\", \"position\": %s}", placeId, photoCounter.position)
                }
            }
        }
        realm.close()
        return textMessage1
    }

    private fun saveMessageToDb(type: Int, textMessage: String, sendId: String, conversationId: String, timestamp: Long) {
        val message: Message
        val messageBuilder = Message.Builder(type)
                .timestamp(timestamp)
                .conversationId(conversationId)
                .sendId(sendId)
        if (type == SENT) {
            if (textMessage.isEmpty() || textMessage.trim { it <= ' ' }.isEmpty()) {
                return
            }
            message = messageBuilder.body(textMessage).build()
        } else if (type == MAP) {
            message = messageBuilder.body("ready").build()
        } else {
            message = messageBuilder.body("").build()
        }
        Log.d(TAG, "Save message to db: " + message)
        messageDao.save(message)
    }

    private fun detectMessageTypeAndTopic(buttonId: Int): Pair<String, Int> {
        var topic: String? = null
        var type = SENT
        when (buttonId) {
            R.id.sendButton -> {
                tracker.send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsTrackers.Category.USER)
                        .setAction(AnalyticsTrackers.Actions.SEND_MESSAGE)
                        .build())
                topic = "inOwner"
                type = SENT
            }
            R.id.showPhotoButton -> {
                tracker.send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsTrackers.Category.USER)
                        .setAction(AnalyticsTrackers.Actions.SHOW_PIC)
                        .build())
                topic = "service"
                type = IMAGE
            }
            R.id.showReviewsButton -> {
                tracker.send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsTrackers.Category.USER)
                        .setAction(AnalyticsTrackers.Actions.SHOW_TIP)
                        .build())
                topic = "service"
                type = REVIEW
            }
            R.id.showMapButton -> {
                tracker.send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsTrackers.Category.USER)
                        .setAction(AnalyticsTrackers.Actions.SHOW_MAP)
                        .build())
                topic = "service"
                type = MAP
            }
            else -> {
            }
        }
        Log.i(TAG, "Topic is " + topic!!)
        return Pair.create<String, Int>(topic, type)
    }

    private fun subscribeIfFirstMessage() {
        subHandler.use { it.firstMessageSubscribe(place) }
    }

    fun sendSubscribeStatusToServerAndNotify() {
        subHandler.use{ it.subscribe(context, place, true) }
    }

    override fun onSubscriptionStatusChanged(statusView: SubscriptionStatusView) {
        view?.setSubscriptionState(statusView.openChannel)
        if (statusView.status && statusView.openChannel) {
            view?.showSubscriptionStatusToast(place.name, statusView.status)
        }
    }

    override fun onSubscriptionFailed() {
        view?.showNoNetworkMessage()
    }

    override fun attachView(view: ChatView) {
        EcApplication.getAppComponent().inject(this)
        userChatToken = authPreferences.chatToken
        val intent = Intent(context, ChatService::class.java)
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
        super.attachView(view)
    }


    override fun detachView(retainInstance: Boolean) {
        if (mConnection.bound) {
            context.unbindService(mConnection)
            mConnection.bound = false
        }
        EcMessageArrivedListener.activeConversationId = null
        super.detachView(retainInstance)
    }

    companion object {
        private val TAG = ChatPresenter::class.java.simpleName
    }
}

