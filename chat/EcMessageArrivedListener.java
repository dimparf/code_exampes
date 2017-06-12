package me.emotioncity.chat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.RingtoneManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.Pair;

import com.google.flatbuffers.FlatBufferBuilder;
import com.google.gson.Gson;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;

import javax.inject.Inject;

import io.realm.Realm;
import me.emotioncity.EcApplication;
import me.emotioncity.R;
import me.emotioncity.auth.AuthPreferences;
import me.emotioncity.dao.ActivationDao;
import me.emotioncity.dao.MessageDao;
import me.emotioncity.dao.PhotoDao;
import me.emotioncity.dao.PlaceDao;
import me.emotioncity.dao.SubscriptionDao;
import me.emotioncity.dao.TipsDao;
import me.emotioncity.dao.UserChatDao;
import me.emotioncity.domain.ChatItem;
import me.emotioncity.domain.PhotoCounter;
import me.emotioncity.domain.TipsCounter;
import me.emotioncity.domain.remote.EmotionCityApi;
import me.emotioncity.domain.remote.model.Message;
import me.emotioncity.domain.remote.model.Photo;
import me.emotioncity.domain.remote.model.Place;
import me.emotioncity.domain.remote.model.Subscription;
import me.emotioncity.domain.remote.model.flatbuffers.ChatMessage;
import me.emotioncity.domain.remote.model.flatbuffers.MType;
import me.emotioncity.domain.remote.model.response.OwnerActivation;
import me.emotioncity.domain.remote.model.response.UserNameView;
import me.emotioncity.intentrecognition.UserIntent;
import me.emotioncity.mqtt.ChatService;
import me.emotioncity.mqtt.IMessageArrivedListener;
import me.emotioncity.mqtt.MqttServiceConnection;
import me.emotioncity.owner.users.conversation.ConversationActivity;
import me.emotioncity.settings.SettingsFragment;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
import static me.emotioncity.utils.CryptoUtils.conversationIdentifiers;

/**
 * Created by stream on 21.06.16.
 */
public class EcMessageArrivedListener implements IMessageArrivedListener {
    private final static String TAG = EcMessageArrivedListener.class.getSimpleName();
    public static String activeConversationId = null;
    @Inject
    MessageDao messageDao;
    @Inject
    ActivationDao activationDao;
    @Inject
    TipsDao tipsDao;
    @Inject
    PhotoDao photoDao;
    @Inject
    UserChatDao userChatDao;
    @Inject
    SubscriptionDao subscriptionDao;
    @Inject
    PlaceDao placeDao;
    @Inject
    EmotionCityApi api;
    @Inject
    Context context;
    @Inject
    AuthPreferences authPreferences;
    @Inject
    Gson mapper;
    @Inject
    MqttServiceConnection mConnection;

    public EcMessageArrivedListener() {
        EcApplication.getAppComponent().inject(this);
        Intent intent = new Intent(context, ChatService.class);
        context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void messageArrived(MqttMessage mqttMessage, String topic) {
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(mqttMessage.getPayload());
            ChatMessage message = ChatMessage.getRootAsChatMessage(byteBuffer);
            String body = message.body();
            String conversationId = message.conversationId();
            Log.d(TAG, "Message is " + Message.messageName(message.mType()) + ": " + body);
            Log.d(TAG, "Conversation id: " + conversationId);
            switch (message.mType()) {
                case MType.SENT:
                case MType.RCV:
                case MType.REQUEST_NAME:
                case MType.RCV_INTENT:
                case MType.MAP:
                    if (message.mType() == MType.RCV_INTENT) {
                        Log.d(TAG, "Body: " + body);
                        UserIntent userIntent = mapper.fromJson(body, UserIntent.class);
                        if (userIntent.reply != null && !userIntent.reply.isEmpty()) {
                            body = userIntent.reply;
                        }
                    }
                    processChatItem(conversationId, body);
                    Message dbMessage = new Message.Builder(message.mType())
                            .body(body)
                            .conversationId(conversationId)
                            .timestamp(message.createdAt())
                            .sendId(message.sendId())
                            .build();
                    messageDao.updateOrCreate(dbMessage);
                    if (message.mType() == MType.RCV) {
                        sendDeliveryDeliveryMessage(topic, message.sendId(), "d", conversationId);
                    }
                    break;
                case MType.REVIEW:
                    TipsCounter tipsCounter = mapper.fromJson(body, TipsCounter.class);
                    tipsDao.updateOrCreate(tipsCounter);
                    String serializedTip = mapper.toJson(tipsCounter.tip);
                    Message tipMessage = new Message.Builder(message.mType())
                            .body(serializedTip)
                            .conversationId(conversationId)
                            .timestamp(message.createdAt())
                            .sendId(message.sendId())
                            .build();
                    messageDao.updateOrCreate(tipMessage);
                    break;
                case MType.IMAGE:
                    PhotoCounter photoCounter = mapper.fromJson(body, PhotoCounter.class);
                    photoDao.updateOrCreate(photoCounter);
                    Message.Builder messageBuilder = new Message.Builder();
                    Photo photo = photoCounter.photo;
                    if (photo != null) {
                        String photoUrl = photoCounter.photo.url;
                        Message photoMessage = messageBuilder
                                .mType(message.mType())
                                .body(photoUrl)
                                .conversationId(conversationId)
                                .timestamp(message.createdAt())
                                .sendId(message.sendId())
                                .build();
                        messageDao.updateOrCreate(photoMessage);
                    } else {
                        Message rcvMessage = messageBuilder
                                .mType(MType.RCV)
                                .body("Для этого заведения пока нет фотографий")//TODO move to resources
                                .conversationId(conversationId)
                                .timestamp(message.createdAt())
                                .sendId(message.sendId())
                                .build();
                        messageDao.updateOrCreate(rcvMessage);
                    }
                    break;
                case MType.DELIVERED:
                    //Find message by sendId
                    //Check it is delivered.
                    //Send delivery delivery to server
                    Message deliveredMessage = new Message.Builder(message.mType())
                            .body(body)
                            .delivered(true)
                            .conversationId(conversationId)
                            .timestamp(message.createdAt())
                            .sendId(message.sendId())
                            .build();
                    messageDao.updateOrCreate(deliveredMessage);
                    sendDeliveryDeliveryMessage(topic, message.sendId(), "dd", conversationId);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendDeliveryDeliveryMessage(String topic, String sendId, String deliveryType, String conversationId) {
        FlatBufferBuilder builder = new FlatBufferBuilder(0);
        int body = builder.createString(deliveryType);
        int sendIdNew = builder.createString(sendId);
        int conversationIdNew = builder.createString(conversationId);
        ChatMessage.startChatMessage(builder);
        ChatMessage.addCreatedAt(builder, new Date().getTime());
        ChatMessage.addMType(builder, MType.DELIVERED);
        ChatMessage.addSendId(builder, sendIdNew);
        ChatMessage.addConversationId(builder, conversationIdNew);
        ChatMessage.addBody(builder, body);
        int chatMessage = ChatMessage.endChatMessage(builder);
        builder.finish(chatMessage);

        byte[] data = builder.sizedByteArray();
        String outgoingTopic;
        if (topic.contains("place/")) {
            outgoingTopic = "inOwner";
        } else {
            outgoingTopic = "inUser";
        }
        Log.d(TAG, "send delivery message status: " + deliveryType + " to topic " + outgoingTopic);

        mConnection.getChatService().sendMessage(data, outgoingTopic);
    }

    private void processChatItem(String conversationId, String body) {
        Pair<String, String> userAndPlaceToken = conversationIdentifiers(conversationId);
        String userToken = userAndPlaceToken.first;
        String placeToken = userAndPlaceToken.second;
        Place lookupPlace = placeDao.findByChatToken(placeToken);
        OwnerActivation activation = activationDao.getActivation();
        if (lookupPlace != null) {
            if (activation == null) {
                processOwnerToUserConversation(lookupPlace, conversationId, body);
            } else {
                processUserToOwnerConversation(conversationId, body, userToken, placeToken, lookupPlace.name);
            }
        }
    }

    private void processOwnerToUserConversation(final Place lookupPlace, String conversationId, String body) {
        String placeId = lookupPlace.id;
        /*Vocalizer vocalizer = Vocalizer.createVocalizer(Vocalizer.Language.RUSSIAN, body, true, Vocalizer.Voice.ZAHAR);
        vocalizer.start();*/
        Subscription subscription = subscriptionDao.findByPlaceId(placeId);
        if (subscription != null) {
            subscriptionDao.update(Realm.getDefaultInstance().copyFromRealm(subscription), body);
            if (!conversationId.equalsIgnoreCase(activeConversationId)) {
                showNotification("Chat", "Сообщение из " + lookupPlace.name, body, placeId);
            }
        }
    }

    private void processUserToOwnerConversation(final String conversationId,
                                                final String body,
                                                final String userToken,
                                                final String placeToken,
                                                final String lookupPlaceName) {
        String authToken = authPreferences.getAuthToken();
        Log.d(TAG, "Conversation id: " + conversationId);
        final ChatItem chatItemOnDevice = userChatDao.findByConversationId(conversationId);
        //TODO true logic, replace to it
        /*if (chatItemOnDevice != null) {
            if (chatItemOnDevice.userName.isEmpty()) {
                //lookupUserName
                //update body and name
            } else {
                //update body
                userChatDao.update(chatItemOnDevice, body);
            }
        } else {
            // create chatItem
            // lookupUserName
            // update body and name
        }*/
        api.lookupUserName(authToken, userToken, conversationId).enqueue(new Callback<UserNameView>() {
            @Override
            public void onResponse(Call<UserNameView> call, Response<UserNameView> response) {
                if (response.code() == 200) {
                    String userName = response.body().name;
                    Log.d(TAG, "Lookup name: " + userName);
                    if (chatItemOnDevice == null) {
                        ChatItem chatItem =
                                userChatDao.create(
                                        lookupPlaceName,
                                        userToken,
                                        placeToken,
                                        userName,
                                        body,
                                        conversationId);
                        userChatDao.save(chatItem);
                    } else {
                        if (chatItemOnDevice.userName.isEmpty()) {
                            userChatDao.update(chatItemOnDevice, userName, body);
                        } else {
                            userChatDao.update(chatItemOnDevice, body);
                        }
                    }
                    showNotification("Conversation", userName + " from " + lookupPlaceName, body, conversationId);
                } else {
                    try {
                        Log.d(TAG, "Code is " + response.code());
                        Log.d(TAG, response.errorBody().string());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(Call<UserNameView> call, Throwable t) {
                Log.d(TAG, t.toString());
            }
        });
    }

    private void showNotification(String type, String title, String message, String placeOrConversationId) {
        PendingIntent pi;
        if (type.equalsIgnoreCase("Chat")) {
            Log.d(TAG, "Chat message received");
            Intent notificationIntent = new Intent(context, ChatActivity.class);
            notificationIntent.putExtra("placeId", placeOrConversationId);
            pi = PendingIntent.getActivity(context, 0, notificationIntent, FLAG_UPDATE_CURRENT);
        } else {
            Log.d(TAG, "Conversation message received");
            Intent notificationIntent = new Intent(context, ConversationActivity.class);
            notificationIntent.putExtra("conversationId", placeOrConversationId);
            pi = PendingIntent.getActivity(context, 0, notificationIntent, FLAG_UPDATE_CURRENT);
        }
        Resources r = context.getResources();
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        boolean ringtoneActivated = sharedPref.getBoolean(SettingsFragment.KEY_RINGTONE, false);
        boolean vibrateActivated = sharedPref.getBoolean(SettingsFragment.KEY_VIBRATE, false);
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context);
        notificationBuilder
                .setTicker(r.getString(R.string.new_message_arrived))
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setDefaults(Notification.DEFAULT_LIGHTS);

        if (ringtoneActivated) {
            notificationBuilder.setSound(alarmSound);
        }

        if (vibrateActivated) {
            notificationBuilder.setDefaults(Notification.DEFAULT_LIGHTS | Notification.DEFAULT_VIBRATE);
        }

        Notification notification = notificationBuilder.build();

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, notification);
    }

}
