package me.emotioncity.chat

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.view.LayoutInflaterCompat
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.text.TextWatcher
import android.transition.ChangeBounds
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.request.animation.GlideAnimation
import com.bumptech.glide.request.target.SimpleTarget
import com.google.android.gms.analytics.HitBuilders
import com.google.android.gms.analytics.Tracker
import com.google.android.gms.maps.model.LatLng
import com.hannesdorfmann.mosby.mvp.MvpActivity
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.context.IconicsLayoutInflater
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import com.vanniktech.emoji.EmojiPopup
import io.realm.Realm
import io.realm.RealmResults
import jp.wasabeef.glide.transformations.CropCircleTransformation
import me.emotioncity.AnalyticsTrackers
import me.emotioncity.EcApplication
import me.emotioncity.R
import me.emotioncity.bot.EcBot
import me.emotioncity.bot.action.Action
import me.emotioncity.bot.action.MessageBuilderAction
import me.emotioncity.bot.action.SendMessageAction
import me.emotioncity.bot.adapters.ActionClickListener
import me.emotioncity.bot.message_builder.MessageBuilder
import me.emotioncity.bot.message_builder.setSendButtonClickListener
import me.emotioncity.chat.info.PlaceActivity
import me.emotioncity.custom_view.commandbar.Command
import me.emotioncity.custom_view.commandbar.CommandBarClickListener
import me.emotioncity.custom_view.commandbar.CommandManager
import me.emotioncity.dao.SubscriptionDao
import me.emotioncity.domain.remote.model.Message
import me.emotioncity.domain.remote.model.Place
import me.emotioncity.extensions.*
import org.jetbrains.anko.*
import ru.yandex.speechkit.Recognizer
import ru.yandex.speechkit.gui.RecognizerActivity
import javax.inject.Inject
import kotlin.properties.Delegates

import kotlinx.android.synthetic.main.chat_activity.*


class ChatActivity : MvpActivity<ChatView, ChatPresenter>(), ChatView, AnkoLogger, ActionClickListener,
        CommandBarClickListener {

    companion object {
        val COMMAND_PLACE_INFO = 0
        val COMMAND_MAP = 1
        val COMMAND_REVIEW = 2
        val COMMAND_IMAGE = 3
        val COMMAND_CLOSE = 4
    }

    val REQUEST_RECOGNIZE_SPEECH = 5

    private val placeId: String by lazy { intent.getStringExtra("placeId") }
    private val placePhotoUrl: String by lazy { intent.getStringExtra("placePhotoUrl") }

    @Inject
    lateinit var subscriptionDao: SubscriptionDao
    @Inject
    lateinit var tracker: Tracker
    private var mAdapter: MessageAdapter? = null
    private lateinit var actionSubscribe: MenuItem

    private var mEmojiPopup: EmojiPopup? = null
    private val commandManager by lazy { createCommandManager() }

    lateinit var sendIcon: IconicsDrawable
    lateinit var micIcon: IconicsDrawable

    val sendClick: (view: View) -> Unit = { view ->
        val buttonId = view.id
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        val textMessage = messageEditText.text.toString()
        presenter.sendMessage(buttonId, textMessage)
    }

    val recognizeSpeechClick: (view: View) -> Unit = { view ->
        startActivityForResult<RecognizerActivity>(REQUEST_RECOGNIZE_SPEECH,
                Recognizer.Model.QUERIES to RecognizerActivity.EXTRA_MODEL,
                Recognizer.Language.RUSSIAN to RecognizerActivity.EXTRA_LANGUAGE)
    }

    var chatBot by Delegates.notNull<EcBot>()

    override fun onCreate(savedInstanceState: Bundle?) {
        LayoutInflaterCompat.setFactory(layoutInflater, IconicsLayoutInflater(delegate))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.sharedElementEnterTransition = ChangeBounds()
        }
        super.onCreate(savedInstanceState)
        EcApplication.getAppComponent().inject(this)

        setContentView(R.layout.chat_activity)

        sendIcon = IconicsDrawable(this).icon(MaterialDesignIconic.Icon.gmi_mail_send).color(Color.WHITE).sizeDp(24)
        micIcon = IconicsDrawable(this).icon(MaterialDesignIconic.Icon.gmi_mic).color(Color.WHITE).sizeDp(24)

        placeToolbar.setNavigationOnClickListener({ v -> onBackPressed() })
        setSupportActionBar(placeToolbar)
        placeToolbar.setOnClickListener {
            tracker.send(HitBuilders.EventBuilder()
                    .setCategory(AnalyticsTrackers.Category.USER)
                    .setAction(AnalyticsTrackers.Actions.SHOW_CHAT_INFO)
                    .build())
            startActivity(intentFor<PlaceActivity>("placeId" to placeId).newTask())
        }

        commandBarButton.setOnClickListener {
            tracker.send(HitBuilders.EventBuilder()
                    .setCategory(AnalyticsTrackers.Category.USER)
                    .setAction(AnalyticsTrackers.Actions.SHOW_BOT_BUTTONS).build())
            showCommandBar()
        }

        commandBar.setCommandManager(commandManager)

        messageEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) { }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) { }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                if (!p0.isNullOrBlank()) {
                    sendButton.setImageDrawable(sendIcon)
                    sendButton.setOnClickListener { sendClick(it) }
                } else {
                    sendButton.setImageDrawable(micIcon)
                    sendButton.setOnClickListener { recognizeSpeechClick(it) }
                }
            }
        })

        sendButton.setImageDrawable(micIcon)
        sendButton.setOnClickListener {
            startActivityForResult<RecognizerActivity>(REQUEST_RECOGNIZE_SPEECH,
                    Recognizer.Model.QUERIES to RecognizerActivity.EXTRA_MODEL,
                    Recognizer.Language.RUSSIAN to RecognizerActivity.EXTRA_LANGUAGE)
        }

    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        presenter.loadPlace(placeId)
    }

    fun createCommandManager() = CommandManager(this, listOf(
            Command(COMMAND_IMAGE, IconicsDrawable(this).icon(MaterialDesignIconic.Icon.gmi_image_o).color(Color.WHITE).sizeDp(46).paddingDp(14)),
            Command(COMMAND_REVIEW, IconicsDrawable(this).icon(MaterialDesignIconic.Icon.gmi_comment_outline).color(Color.WHITE).sizeDp(46).paddingDp(14)),
            Command(COMMAND_MAP, IconicsDrawable(this).icon(MaterialDesignIconic.Icon.gmi_map).color(Color.WHITE).sizeDp(46).paddingDp(14)),
            Command(COMMAND_PLACE_INFO, IconicsDrawable(this).icon(MaterialDesignIconic.Icon.gmi_info_outline).color(Color.WHITE).sizeDp(46).paddingDp(14)),
            Command(COMMAND_CLOSE, IconicsDrawable(this).icon(MaterialDesignIconic.Icon.gmi_close_circle_o).color(Color.WHITE).sizeDp(46).paddingDp(14))
    ))


    override fun onCommandClick(commandId: Int) {
        when (commandId) {
            COMMAND_PLACE_INFO -> {
                toast("В разработке")
            } //TODO
            COMMAND_MAP -> {
                presenter.sendMessage(R.id.showMapButton, messageEditText.text.toString())
            }
            COMMAND_REVIEW -> {
                presenter.sendMessage(R.id.showReviewsButton, messageEditText.text.toString())
            }
            COMMAND_IMAGE -> {
                presenter.sendMessage(R.id.showPhotoButton, messageEditText.text.toString())
            }
            COMMAND_CLOSE -> {
                showMessageBox()
            }
        }
    }

    override fun actionClicked(position: Int) {
        val action = chatBot.actions[position]
        when (action) {
            is SendMessageAction -> {
                presenter.sendMessage(R.id.sendButton, action.msgToSend)
                if (action.msgToReceive.isNotEmpty()) {
                    // TODO send message to user after short delay
                }
            }
            is MessageBuilderAction -> {
                action.items.forEach { it.actualValue = "" }
                val messageBuilder = MessageBuilder(this, chatBot, editFrame, action)
                messageBuilder.setSendButtonClickListener { s ->
                    presenter.sendMessage(R.id.sendButton, s)
                }
            }
        }
    }

    override fun showMessageBuilder() {
    //    messageBuilderView.makeVisible()
        msgBox.makeGone()
        commandBar.makeGone()
    }

    override fun showCommandBar() {
        if (Build.VERSION.SDK_INT >= 21) {
            sendButton.hide()
            msgBox.circularDisappear { commandBar.show(true) }
        }
        else {
            sendButton.makeInvisible()
            msgBox.makeInvisible()
            commandBar.show(false)
        }
    }

    override fun showMessageBox() {
        if (commandBar.isVisible()) {
            commandBar.hide(true) {
                msgBox.circularReveal()
                sendButton.show()
            }
        } else {
   //         messageBuilderView.makeGone()
            msgBox.circularReveal()
            sendButton.show()
        }
    }

    override fun createPresenter(): ChatPresenter {
        return ChatPresenter()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        val actionMap = menu.findItem(R.id.action_map)
        actionSubscribe = menu.findItem(R.id.action_subscribe)
        val placeIsSubscribe = subscriptionDao.placeIsSubscribed(placeId)
        Glide.with(this).load(placePhotoUrl)
                .asBitmap()
                .fitCenter()
                .dontAnimate()
                .transform(CropCircleTransformation(this))
                .placeholder(R.drawable.placeholder)
                .error(R.drawable.placeholder).into(object : SimpleTarget<Bitmap>(60, 60) {
            override fun onResourceReady(resource: Bitmap?, glideAnimation: GlideAnimation<in Bitmap>?) {
                actionMap.icon = BitmapDrawable(resources, resource)
            }
        })

        setSubscriptionState(placeIsSubscribe)

        return super.onCreateOptionsMenu(menu)
    }

    override fun setSubscriptionState(state: Boolean) {
        val bookmark = if (state) MaterialDesignIconic.Icon.gmi_bookmark
        else MaterialDesignIconic.Icon.gmi_bookmark_outline
        actionSubscribe.icon = IconicsDrawable(this)
                .icon(bookmark)
                .color(Color.WHITE)
                .sizeDp(24)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            R.id.action_subscribe -> {
                presenter.sendSubscribeStatusToServerAndNotify()
                return true
            }
            R.id.action_map -> {
                tracker.send(HitBuilders.EventBuilder()
                        .setCategory(AnalyticsTrackers.Category.USER)
                        .setAction(AnalyticsTrackers.Actions.SHOW_CHAT_INFO)
                        .build())
                startActivity(intentFor<PlaceActivity>("placeId" to placeId).newTask())
                return false
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_RECOGNIZE_SPEECH) {
            if (resultCode == RecognizerActivity.RESULT_OK && data != null) {
                messageEditText.setText(data.getStringExtra(RecognizerActivity.EXTRA_RESULT))
            }
        }
    }

    override fun showSubscriptionStatusToast(placeName: String?, state: Boolean) {
        val message = if (state) resources.getText(R.string.subscription_notificate) as String + " " + placeName
        else resources.getText(R.string.unsubscription_notificate) as String + " " + placeName
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun showNoNetworkMessage() {
        Snackbar.make(messageEditText, R.string.no_internet_connection, Snackbar.LENGTH_LONG).show()
    }

    override fun clearMessageField() {
        messageEditText.text = null
    }

    override fun loadPlace(place: Place, messages: RealmResults<Message>?, actions: List<Action>) {
        chatBot = EcBot(actions, botButtonsContainer, this)

        botButtonsContainer.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        botButtonsContainer.setHasFixedSize(true)
        botButtonsContainer.adapter = chatBot.actionsAdapter

        supportActionBar?.title = place.name
        supportActionBar?.subtitle = place.category

        //(messageList.recycleView.layoutManager as LinearLayoutManager).reverseLayout = true
        (messageList.recycleView.layoutManager as LinearLayoutManager).stackFromEnd = true

        mAdapter = MessageAdapter(this, messages, true, false, place.name, LatLng(place.latitude, place.longitude))
        messageList.setAdapter(mAdapter)
        messageList.addOnLayoutChangeListener({ v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (bottom < oldBottom) {
                messageList.postDelayed({ scrollToBottom() }, 100)
            }
        })
        scrollToBottom()
        setupEmojiPopup()
    }

    override fun scrollToBottom() {
        val itemCount = mAdapter?.itemCount
        if (itemCount != null) {
            messageList.smoothScrollToPosition(itemCount)
        }
    }

    override fun onBackPressed() {
        if (commandBar.isVisible()) {
            commandBar.hide(true) {
                msgBox.circularReveal()
                sendButton.show()
            }
        } else if (mEmojiPopup?.isShowing ?: false)
            mEmojiPopup?.dismiss()
        else super.onBackPressed()
    }

    private fun setupEmojiPopup() {
        mEmojiPopup = EmojiPopup.Builder
                .fromRootView(findViewById(R.id.root_view))
                .setOnEmojiPopupShownListener {
                    emojiButton.setImageResource(R.drawable.ic_keyboard_grey600_24dp)
                    showSoftKeyboard()
                }
                .setOnEmojiPopupDismissListener { emojiButton.setImageResource(R.drawable.ic_emoticon_grey600_24dp) }
                .setOnSoftKeyboardCloseListener { mEmojiPopup?.dismiss() }
                .build(messageEditText)

        emojiButton.setOnClickListener({ v -> mEmojiPopup?.toggle() })
    }

    private fun showSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(messageEditText, InputMethodManager.SHOW_IMPLICIT)
    }

}