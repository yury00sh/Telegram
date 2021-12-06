package org.telegram.ui.Components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.HashMap;

public class ReactionsListView extends FrameLayout {

    ArrayList<Long> peerIds = new ArrayList<>();
    public ArrayList<TLRPC.User> users = new ArrayList<>();
    AvatarsImageView avatarsImageView;
    TextView titleView;
    ImageView iconView;
    int currentAccount;
    boolean isVoice;

    public ArrayList<TLRPC.TL_messageUserReaction> userReactions = new ArrayList<>();
    String nextQueryOffset;
    String reactionFilter;
    int itemCount;
    TLRPC.Chat currentChat;
    MessageObject currentMessage;

    BaseFragment fragment;

    FlickerLoadingView flickerLoadingView;

    public ReactionsListView(@NonNull Context context, int currentAccount, MessageObject messageObject, TLRPC.Chat chat, BaseFragment fragment, String reactionFilter) {
        super(context);
        this.currentAccount = currentAccount;
        this.reactionFilter = reactionFilter;
        currentChat = chat;
        currentMessage = messageObject;
        this.fragment = fragment;
        isVoice = (messageObject.isRoundVideo() || messageObject.isVoice());
        flickerLoadingView = new FlickerLoadingView(context);
        flickerLoadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, null);
        flickerLoadingView.setViewType(FlickerLoadingView.MESSAGE_SEEN_TYPE);
        flickerLoadingView.setIsSingleCell(false);
        addView(flickerLoadingView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);

        addView(titleView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 40, 0, 62, 0));

        avatarsImageView = new AvatarsImageView(context, false);
        avatarsImageView.setStyle(AvatarsImageView.STYLE_MESSAGE_SEEN);
        addView(avatarsImageView, LayoutHelper.createFrame(24 + 12 + 12 + 8, LayoutHelper.MATCH_PARENT, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

        titleView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));

        TLRPC.TL_messages_getMessageReadParticipants req = new TLRPC.TL_messages_getMessageReadParticipants();
        req.msg_id = messageObject.getId();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(messageObject.getDialogId());

        iconView = new ImageView(context);
        addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
        Drawable drawable = ContextCompat.getDrawable(context, isVoice ? R.drawable.msg_played : R.drawable.msg_seen).mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.MULTIPLY));
        iconView.setImageDrawable(drawable);

        avatarsImageView.setAlpha(0);
        titleView.setAlpha(0);
        long fromId = 0;
        if (messageObject.messageOwner.from_id != null) {
            fromId = messageObject.messageOwner.from_id.user_id;
        }
        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), AndroidUtilities.dp(4), AndroidUtilities.dp(4)));
        setEnabled(false);
    }

    boolean ignoreLayout;

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (flickerLoadingView.getVisibility() == View.VISIBLE) {
            ignoreLayout = true;
            flickerLoadingView.setVisibility(View.GONE);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            flickerLoadingView.getLayoutParams().width = getMeasuredWidth();
            flickerLoadingView.setVisibility(View.VISIBLE);
            ignoreLayout = false;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    private boolean loadingNextPage = false;
    private boolean endReached = false;

    private void loadNextPage(RecyclerView.Adapter adapter) {
        if (loadingNextPage || endReached) {
            return;
        }
        if (currentChat == null || currentMessage == null) {
            return;
        }
        loadingNextPage = true;
        TLRPC.TL_messages_getMessageReactionsList req = new TLRPC.TL_messages_getMessageReactionsList();
        req.peer = MessagesController.getInputPeer(currentChat);
        req.id = currentMessage.getId();
        req.flags = 0;
        if (reactionFilter != null) {
            req.flags |= 1;
            req.reaction = reactionFilter;
        }
        req.limit = 100;
        if (nextQueryOffset != null) {
            req.flags |= 2;
            req.offset = nextQueryOffset;
            req.limit = 50;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            loadingNextPage = false;
            if (err == null) {
                TLRPC.TL_messages_messageReactionsList list = (TLRPC.TL_messages_messageReactionsList) res;
                if (list.next_offset != null) {
                    nextQueryOffset = list.next_offset;
                } else {
                    endReached = true;
                }
                userReactions.addAll(list.reactions);
                MessagesController.getInstance(currentAccount).putUsers(list.users, false);
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
            }
        }));
    }


    public ArrayList<TLRPC.TL_messageUserReaction> getUserReactions() {
        return userReactions;
    }

    public RecyclerListView createListView() {
        RecyclerListView recyclerListView = new RecyclerListView(getContext());
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerListView.setLayoutManager(layoutManager);
        recyclerListView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                int p = parent.getChildAdapterPosition(view);
                if (p == 0) {
                    outRect.top = AndroidUtilities.dp(4);
                }
//                if (p == users.size() - 1) {
//                    outRect.bottom = AndroidUtilities.dp(4);
//                }
            }
        });
        RecyclerListView.SelectionAdapter adapter = new RecyclerListView.SelectionAdapter() {

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return true;
            }

            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                UserCell userCell = new UserCell(parent.getContext());
                userCell.setLayoutParams(new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                return new RecyclerListView.Holder(userCell);
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
                UserCell cell = (UserCell) holder.itemView;
                if (position >= userReactions.size()) {
                    cell.setLoading(true);
                } else {
                    cell.setLoading(false);
                    TLRPC.TL_messageUserReaction userReaction = userReactions.get(position);
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(userReaction.user_id);
                    TLRPC.TL_availableReaction reaction = null;
                    ArrayList<TLRPC.TL_availableReaction> list = MessagesController.getInstance(currentAccount).getAvailableReactions();
                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i).reaction.equals(userReaction.reaction)) {
                            reaction = list.get(i);
                            break;
                        }
                    }
                    cell.setUserAndReaction(user, reaction);
                }
            }

            @Override
            public int getItemCount() {
                return userReactions.size() + (endReached ? 0 : 2);
            }
        };
        recyclerListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (!endReached) {
                    if (layoutManager.findLastVisibleItemPosition() < userReactions.size() - 10) {
                        loadNextPage(adapter);
                    }
                }
            }
        });

        recyclerListView.setAdapter(adapter);
        if (userReactions.isEmpty()) {
            loadNextPage(adapter);
        }
        return recyclerListView;
    }

    private static class UserCell extends FrameLayout {

        BackupImageView avatarImageView;
        BackupImageView reactionIconImageView;
        TextView nameView;
        AvatarDrawable avatarDrawable = new AvatarDrawable();

        FlickerLoadingView loadingView;

        public UserCell(Context context) {
            super(context);
            loadingView = new FlickerLoadingView(context);
            loadingView.setColors(Theme.key_actionBarDefaultSubmenuBackground, Theme.key_listSelector, null);
            loadingView.setViewType(FlickerLoadingView.USER_REACTION_TYPE);
            loadingView.setIsSingleCell(false);
            addView(loadingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48));

            avatarImageView = new BackupImageView(context);
            addView(avatarImageView, LayoutHelper.createFrame(34, 34, Gravity.CENTER_VERTICAL, 13, 0, 0, 0));
            avatarImageView.setRoundRadius(AndroidUtilities.dp(16));
            nameView = new TextView(context);
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setLines(1);
            nameView.setEllipsize(TextUtils.TruncateAt.END);
            addView(nameView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 13 + 34 + 13, 0, 13 + 22 + 13, 0));

            reactionIconImageView = new BackupImageView(context);
            addView(reactionIconImageView, LayoutHelper.createFrame(22, 22, Gravity.CENTER_VERTICAL | Gravity.RIGHT, 0, 0, 13, 0));

            nameView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
        }

        public void setUserAndReaction(TLRPC.User user, TLRPC.TL_availableReaction reaction) {
            if (user != null) {
                avatarDrawable.setInfo(user);
                ImageLocation imageLocation = ImageLocation.getForUser(user, ImageLocation.TYPE_SMALL);
                avatarImageView.setImage(imageLocation, "50_50", avatarDrawable, user);
                nameView.setText(ContactsController.formatName(user.first_name, user.last_name));
            }
            if (reaction != null) {
                reactionIconImageView.setImage(ImageLocation.getForDocument(reaction.static_icon), null, null, "webp", null, 1);
                reactionIconImageView.setVisibility(View.VISIBLE);
            } else {
                reactionIconImageView.setVisibility(View.GONE);
            }
        }

        boolean isLoading = false;

        public void setLoading(boolean loading) {
            nameView.setVisibility(loading ? View.GONE : View.VISIBLE);
            avatarImageView.setVisibility(loading ? View.GONE : View.VISIBLE);
            reactionIconImageView.setVisibility(loading ? View.GONE : View.VISIBLE);
            loadingView.setVisibility(!loading ? View.GONE : View.VISIBLE);
            if (isLoading && !loading) {
                nameView.setAlpha(0);
                avatarImageView.setAlpha(0);
                reactionIconImageView.setAlpha(0);
                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(ObjectAnimator.ofFloat(nameView, View.ALPHA, 1), ObjectAnimator.ofFloat(avatarImageView, View.ALPHA, 1), ObjectAnimator.ofFloat(reactionIconImageView, View.ALPHA, 1));
                animatorSet.setDuration(150);
                animatorSet.start();
            }
            isLoading = loading;
        }
    }
}
