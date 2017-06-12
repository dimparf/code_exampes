package me.emotioncity.chat;

import android.content.Context;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.util.Linkify;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;
import com.google.gson.Gson;
import com.vanniktech.emoji.EmojiTextView;

import java.util.Date;

import javax.inject.Inject;

import io.realm.RealmBasedRecyclerViewAdapter;
import io.realm.RealmResults;
import io.realm.RealmViewHolder;
import me.emotioncity.EcApplication;
import me.emotioncity.R;
import me.emotioncity.adapters.ImageViewSquare;
import me.emotioncity.domain.remote.model.Message;
import me.emotioncity.domain.remote.model.Tip;
import pl.tajchert.sample.DotsTextView;

import static me.emotioncity.domain.remote.model.flatbuffers.MType.IMAGE;
import static me.emotioncity.domain.remote.model.flatbuffers.MType.MAP;
import static me.emotioncity.domain.remote.model.flatbuffers.MType.RCV;
import static me.emotioncity.domain.remote.model.flatbuffers.MType.RCV_INTENT;
import static me.emotioncity.domain.remote.model.flatbuffers.MType.REQUEST_NAME;
import static me.emotioncity.domain.remote.model.flatbuffers.MType.REVIEW;
import static me.emotioncity.domain.remote.model.flatbuffers.MType.SENT;


public class MessageAdapter extends RealmBasedRecyclerViewAdapter<Message, MessageAdapter.ViewHolder> {
    private final static String TAG = MessageAdapter.class.getSimpleName();
    @Inject
    Context mContext;
    @Inject
    Gson mapper;
    private String placeName;
    private LatLng placeLocation;

    MessageAdapter(
            Context context,
            RealmResults<Message> realmResults,
            boolean automaticUpdate,
            boolean animateIdType, String placeName, LatLng placeLocation) {
        super(context, realmResults, automaticUpdate, animateIdType);
        this.placeName = placeName;
        this.placeLocation = placeLocation;
        mContext = context;
        EcApplication.getAppComponent().inject(this);
    }

    @Override
    public ViewHolder onCreateRealmViewHolder(ViewGroup parent, int viewType) {
        int layout = -1;
        switch ((byte) viewType) {
            case SENT:
                layout = R.layout.chat_item_sent;
                break;
            case RCV:
            case REQUEST_NAME:
            case RCV_INTENT:
                layout = R.layout.chat_item_rcv;
                break;
            case IMAGE:
                layout = R.layout.chat_photo_view;
                break;
            case REVIEW:
                layout = R.layout.chat_item_review;
                break;
            case MAP:
                layout = R.layout.chat_map_view;
                break;
            default:
                break;
        }
        //Log.d(TAG, "Layout is " + Message.messageName(viewType));
        View v = LayoutInflater
                .from(parent.getContext())
                .inflate(layout, parent, false);
        ViewHolder viewHolder = new ViewHolder(v);
        viewHolder.mapViewOnCreate(null);
        return viewHolder;
    }

    @Override
    public void onBindRealmViewHolder(ViewHolder viewHolder, int position) {
        final Message message = realmResults.get(position);
        //Log.d(TAG, "Message: " + message);
        final boolean bodyIsEmpty = message.body.isEmpty();
        //Log.d(TAG, "body is empty: " + bodyIsEmpty);
        if (!bodyIsEmpty) {
            switch (message.mType) {
                case SENT:
                case RCV:
                case REQUEST_NAME:
                case RCV_INTENT:
                    viewHolder.setMessage(message.body);
                    viewHolder.setDate(new Date(message.createdAt));
                    break;
                case IMAGE:
                    viewHolder.setPhoto(message.body);
                    break;
                case REVIEW:
                    Tip tip = mapper.fromJson(message.body, Tip.class);
                    if (tip != null) {
                        viewHolder.setMessage(tip.text);
                        viewHolder.setAuthor(tip.userName + " (Foursquare)");//TODO move to resources
                    } else {
                        viewHolder.setMessage("Для этого заведения пока нет отзывов");//TODO move to resources
                        Log.d(TAG, "Tip is null");
                    }
                    break;
                case MAP:
                    viewHolder.mapViewOnResume();
                    viewHolder.initMap(placeName);
                    break;
                default:
                    break;
            }
            viewHolder.done();
        } else {
            viewHolder.loading();
        }
        viewHolder.setDate(new Date(message.createdAt));
    }

    @Override
    public int getItemViewType(int position) {
        final Message message = realmResults.get(position);
        return message.mType;
    }

    class ViewHolder extends RealmViewHolder {
        private TextView mDateView;
        private EmojiTextView mMessageView;
        private TextView mAuthorView;
        private ImageViewSquare mImageView;
        private MapView mMapView;
        private DotsTextView dots;

        ViewHolder(View container) {
            super(container);
            mDateView = (TextView) itemView.findViewById(R.id.dateView);
            mMessageView = (EmojiTextView) itemView.findViewById(R.id.messageView);
            mAuthorView = (TextView) itemView.findViewById(R.id.authorView);
            mImageView = (ImageViewSquare) itemView.findViewById(R.id.imageView);
            mMapView = (MapView) itemView.findViewById(R.id.chatMapView);
            dots = (DotsTextView) itemView.findViewById(R.id.dots);
        }

        void loading() {
            int gone = View.GONE;
            dots.setVisibility(View.VISIBLE);
            if (null != mDateView) mDateView.setVisibility(gone);
            if (null != mMessageView) mMessageView.setVisibility(gone);
            if (null != mAuthorView) mAuthorView.setVisibility(gone);
            if (null != mImageView) mImageView.setVisibility(gone);
            if (null != mMapView) mMapView.setVisibility(gone);
            dots.showAndPlay();
        }

        void done() {
            int visible = View.VISIBLE;
            if (null != mDateView) mDateView.setVisibility(visible);
            if (null != mMessageView) mMessageView.setVisibility(visible);
            if (null != mAuthorView) mAuthorView.setVisibility(visible);
            if (null != mImageView) mImageView.setVisibility(visible);
            if (null != mMapView) mMapView.setVisibility(visible);
            if (dots != null) {
                dots.stop();
                dots.setVisibility(View.GONE);
            }
        }

        public void setDate(Date date) {
            if (null == mDateView) return;
            mDateView.setVisibility(View.VISIBLE);
            String sDate = DateUtils.formatDateTime(mContext, date.getTime(), DateUtils.FORMAT_SHOW_TIME);
            mDateView.setText(sDate);
        }

        public void setMessage(String message) {
            if (null == mMessageView) return;
            mMessageView.setVisibility(View.VISIBLE);
            mMessageView.setText(message);
            Linkify.addLinks(mMessageView, Linkify.ALL);
        }

        void setAuthor(String author) {
            if (null == mAuthorView) return;
            mAuthorView.setVisibility(View.VISIBLE);
            mAuthorView.setText(author);
        }

        void setPhoto(String photoUrl) {
            if (null == mImageView || photoUrl == null || photoUrl.isEmpty()) return;
            mImageView.setVisibility(View.GONE);
            Glide.with(mContext).load(photoUrl)
                    .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                    .fitCenter()
                    .crossFade()
                    .error(R.drawable.placeholder)
                    .listener(new RequestListener<String, GlideDrawable>() {
                        @Override
                        public boolean onException(Exception e, String model, Target<GlideDrawable> target, boolean isFirstResource) {
                            mImageView.setVisibility(View.VISIBLE);//Or GONE?
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(GlideDrawable resource, String model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                            mImageView.setVisibility(View.VISIBLE);
                            return false;
                        }
                    })
                    .into(mImageView);
        }

        void initMap(String placeName) {
            if (null == mMapView) return;
            mMapView.setVisibility(View.VISIBLE);
            mMapView.getMapAsync(new EcOnMapReadyCallback(mContext, placeName, placeLocation));
        }

        void mapViewOnCreate(Bundle savedInstanceState) {
            if (mMapView != null) {
                mMapView.onCreate(savedInstanceState);
            }
        }

        void mapViewOnResume() {
            if (mMapView != null) {
                mMapView.onResume();
            }
        }

    }

}