package org.telegram.ui.Components;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

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
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.DividerCell;
import org.telegram.ui.Cells.ShadowSectionCell;

import java.util.ArrayList;
import java.util.HashMap;

public class UserReactionsListLayout extends FrameLayout {

    ArrayList<Long> peerIds = new ArrayList<>();
    public ArrayList<TLRPC.User> usersSeen = new ArrayList<>();
    AvatarsImageView avatarsImageView;
    TextView titleView;
    ImageView iconView;
    int currentAccount;
    boolean isVoice;

    public ArrayList<TLRPC.TL_reactionCount> reactionCounts;
    public int totalReactionCount;

    String nextQueryOffset;
    String reactionFilter;
    int itemCount;
    TLRPC.Chat currentChat;
    MessageObject currentMessage;

    FlickerLoadingView flickerLoadingView;

    boolean allowMessageSeen = false;
    boolean onlyMessagesSeen = false;

    public UserReactionsListLayout(@NonNull Context context, int currentAccount, MessageObject messageObject, TLRPC.Chat chat, boolean showMessageSeen) {
        super(context);
        this.currentAccount = currentAccount;
        currentChat = chat;
        currentMessage = messageObject;
        isVoice = (messageObject.isRoundVideo() || messageObject.isVoice());

        if (messageObject.messageOwner.reactions != null) {
            reactionCounts = messageObject.messageOwner.reactions.results;
        }

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

        avatarsImageView.setAlpha(0);
        titleView.setAlpha(0);

        totalReactionCount = 0;
        if (reactionCounts != null) {
            for (int i = 0; i < reactionCounts.size(); i++) {
                totalReactionCount += reactionCounts.get(i).count;
            }
        }

//        generalUserReactions = new ArrayList<>(totalReactionCount);

        setEnabled(false);

        allowMessageSeen = showMessageSeen;
        if (reactionCounts == null || reactionCounts.isEmpty()) {
            if (showMessageSeen) {
                onlyMessagesSeen = true;
            }
        } else if (!allowMessageSeen) {
            updateView();
        }
        if (allowMessageSeen) {
            loadUsersSeen();
        }

        iconView = new ImageView(context);
        addView(iconView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.CENTER_VERTICAL, 11, 0, 0, 0));
        Drawable drawable;
        if (onlyMessagesSeen) {
            drawable = ContextCompat.getDrawable(context, isVoice ? R.drawable.msg_played : R.drawable.msg_seen).mutate();
        } else {
            drawable = ContextCompat.getDrawable(context, R.drawable.msg_reactions).mutate();
        }
        drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultSubmenuItemIcon), PorterDuff.Mode.MULTIPLY));
        iconView.setImageDrawable(drawable);

        setBackground(Theme.createRadSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), AndroidUtilities.dp(4), AndroidUtilities.dp(0)));
    }

    public interface UserReactionsListLayoutDelegate {
        void clickedReactionListMenuBackButton();

        default void clickedUserProfile(TLRPC.User user) {};
    }

    UserReactionsListLayoutDelegate delegate;

    public void setDelegate(UserReactionsListLayoutDelegate delegate) {
        this.delegate = delegate;
    }

    public FrameLayout getButton() {
        return this;
    }

    LinearLayout cachedLayout;

    PagerAdapter viewPagerAdapter;
    ViewPager viewPager;
    ArrayList<ReactionChip> tabChips = new ArrayList<>(15);
    ReactionChip activeTab;

    public LinearLayout createReactionsListMenu(Context context) {
        if (cachedLayout != null) {
            return cachedLayout;
        }
        boolean showTabs = shouldShowTabs();

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setClickable(true);

        ActionBarMenuSubItem backCell = new ActionBarMenuSubItem(context, true, false);
        backCell.setItemHeight(44);
        backCell.setTextAndIcon(LocaleController.getString("Back", R.string.Back), R.drawable.msg_arrow_back);
        backCell.getTextView().setPadding(LocaleController.isRTL ? 0 : AndroidUtilities.dp(40), 0, LocaleController.isRTL ? AndroidUtilities.dp(40) : 0, 0);
        layout.addView(backCell);

        backCell.setOnClickListener(v -> {
            if (delegate != null) {
                delegate.clickedReactionListMenuBackButton();
            }
        });

        if (!showTabs) {
            ShadowSectionCell shadowCell = new ShadowSectionCell(context, 8, Theme.getColor(Theme.key_windowBackgroundGray));
            layout.addView(shadowCell);

            generalReactionLoader = new ReactionLoader();
            generalReactionLoader.data = new ArrayList<>(totalReactionCount + 10);

            RecyclerView listView = createListView(generalReactionLoader);
            layout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        } else {
            HorizontalScrollView tabsScrollView = new HorizontalScrollView(context);
            LinearLayout tabsLayout = new LinearLayout(context);
            tabsScrollView.setHorizontalScrollBarEnabled(false);
            layout.addView(tabsScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40));

            ArrayList<TLRPC.TL_availableReaction> rTypes = MessagesController.getInstance(currentAccount).getAvailableReactions();
            if (rTypes == null) {
                rTypes = new ArrayList<>();
            }

            tabsScrollView.addView(tabsLayout);
            tabsLayout.setOrientation(LinearLayout.HORIZONTAL);

            viewPager = new ViewPager(context);

            ReactionChip generalTab = new ReactionChip(context, Theme.getColor(Theme.key_chat_inPreviewInstantText), null);
            Drawable drawable = ContextCompat.getDrawable(context, R.drawable.msg_reactions_filled).mutate();
            drawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_inPreviewInstantText), PorterDuff.Mode.MULTIPLY));
            generalTab.setIconDrawable(drawable);
            generalTab.setSelected(true);
            generalTab.setCount(totalReactionCount, false);
            tabChips.add(generalTab);
            activeTab = generalTab;
            generalTab.setOnClickListener(v -> {
                viewPager.setCurrentItem(0, true);
            });
            tabsLayout.addView(generalTab, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 3, 0, 3, 10));

            for (int i = 0; i < reactionCounts.size(); i++) {
                ReactionChip reactionChip = new ReactionChip(context, Theme.getColor(Theme.key_chat_inPreviewInstantText), null);
                reactionChip.setValue(reactionCounts.get(i));
                reactionChip.setCount(reactionChip.getValue().count, false);
                for (int j = 0; j < rTypes.size(); j++) {
                    if (rTypes.get(j).reaction.equals(reactionChip.getValue().reaction)) {
                        reactionChip.setIconDocument(rTypes.get(j).static_icon);
                        break;
                    }
                }
                final int index = i;
                reactionChip.setOnClickListener(v -> {
                    viewPager.setCurrentItem(index + 1, true);
                });
                tabChips.add(reactionChip);
                tabsLayout.addView(reactionChip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 3, 0, 3, 10));
            }
            DividerCell dividerCell = new DividerCell(context);
            dividerCell.setPadding(0,0,0,0);
            layout.addView(dividerCell);

            viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    activeTab.setSelected(false);
                    activeTab = tabChips.get(position);
                    activeTab.setSelected(true);

                    tabsScrollView.smoothScrollTo(activeTab.getLeft() + (activeTab.getMeasuredWidth() - tabsScrollView.getMeasuredWidth()) / 2, 0);
                }
            });

            viewPager.setAdapter(viewPagerAdapter = new PagerAdapter() {
                @Override
                public int getCount() {
                    return reactionCounts.size() + 1;
                }

                @Override
                public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                    if (((RecyclerView) view).getAdapter() == ((ReactionLoader) object).listener) {
                        return true;
                    }
                    return false;
                }

                @Override
                public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
                    container.removeView(((ReactionLoader) object).currentView);
                }

                @NonNull
                @Override
                public Object instantiateItem(@NonNull ViewGroup container, int position) {
                    ReactionLoader rLoader;
                    if (position >= 1) {
                        TLRPC.TL_reactionCount rc = reactionCounts.get(position - 1);
                        rLoader = reactionLoaders.get(rc.reaction);
                        if (rLoader == null) {
                            rLoader = new ReactionLoader();
                            rLoader.data = new ArrayList<>(rc.count + 10);
                            rLoader.filter = rc.reaction;
                            reactionLoaders.put(rc.reaction, rLoader);
                        }
                    } else {
                        if (generalReactionLoader == null) {
                            rLoader = new ReactionLoader();
                            rLoader.data = new ArrayList<>(totalReactionCount + usersSeen.size() + 10);
                            generalReactionLoader = rLoader;
                        }
                        rLoader = generalReactionLoader;
                    }
                    container.addView(rLoader.currentView = createListView(rLoader));
                    return rLoader;
                }
            });

            layout.addView(viewPager, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            ((LinearLayout.LayoutParams) viewPager.getLayoutParams()).weight = 1;
        }

        layout.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));

        cachedLayout = layout;
        return layout;
    }

    void onItemClickListener(View v, int position) {

    }

    boolean shouldShowTabs() {
        if (reactionCounts == null) {
            return false;
        }
        if (reactionCounts.size() <= 1) {
            return false;
        }
        return totalReactionCount > 10;
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

    boolean loadingUsersSeen = false;

    private void loadUsersSeen() {
        if (loadingUsersSeen) {
            return;
        }
        loadingUsersSeen = true;
        long fromId = 0;
        if (currentMessage.messageOwner.from_id != null) {
            fromId = currentMessage.messageOwner.from_id.user_id;
        }

        TLRPC.TL_messages_getMessageReadParticipants req = new TLRPC.TL_messages_getMessageReadParticipants();
        req.msg_id = currentMessage.getId();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(currentMessage.getDialogId());
        long finalFromId = fromId;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            FileLog.e("MessageSeenView request completed");
            if (error == null) {
                TLRPC.Vector vector = (TLRPC.Vector) response;
                ArrayList<Long> unknownUsers = new ArrayList<>();
                HashMap<Long, TLRPC.User> usersLocal = new HashMap<>();
                ArrayList<Long> allPeers = new ArrayList<>();
                for (int i = 0, n = vector.objects.size(); i < n; i++) {
                    Object object = vector.objects.get(i);
                    if (object instanceof Long) {
                        Long peerId = (Long) object;
                        if (finalFromId == peerId) {
                            continue;
                        }
                        TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(peerId);
                        allPeers.add(peerId);
                        if (true || user == null) {
                            unknownUsers.add(peerId);
                        } else {
                            usersLocal.put(peerId, user);
                        }
                    }
                }

                if (unknownUsers.isEmpty()) {
                    for (int i = 0; i < allPeers.size(); i++) {
                        peerIds.add(allPeers.get(i));
                        usersSeen.add(usersLocal.get(allPeers.get(i)));
                    }
                    updateView();
                } else {
                    if (ChatObject.isChannel(currentChat)) {
                        TLRPC.TL_channels_getParticipants usersReq = new TLRPC.TL_channels_getParticipants();
                        usersReq.limit = 50;
                        usersReq.offset = 0;
                        usersReq.filter = new TLRPC.TL_channelParticipantsRecent();
                        usersReq.channel = MessagesController.getInstance(currentAccount).getInputChannel(currentChat.id);
                        ConnectionsManager.getInstance(currentAccount).sendRequest(usersReq, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                            if (response1 != null) {
                                TLRPC.TL_channels_channelParticipants users = (TLRPC.TL_channels_channelParticipants) response1;
                                for (int i = 0; i < users.users.size(); i++) {
                                    TLRPC.User user = users.users.get(i);
                                    MessagesController.getInstance(currentAccount).putUser(user, false);
                                    usersLocal.put(user.id, user);
                                }
                                for (int i = 0; i < allPeers.size(); i++) {
                                    peerIds.add(allPeers.get(i));
                                    this.usersSeen.add(usersLocal.get(allPeers.get(i)));
                                }
                            }
                            updateView();
                        }));
                    } else {
                        TLRPC.TL_messages_getFullChat usersReq = new TLRPC.TL_messages_getFullChat();
                        usersReq.chat_id = currentChat.id;
                        ConnectionsManager.getInstance(currentAccount).sendRequest(usersReq, (response1, error1) -> AndroidUtilities.runOnUIThread(() -> {
                            if (response1 != null) {
                                TLRPC.TL_messages_chatFull chatFull = (TLRPC.TL_messages_chatFull) response1;
                                for (int i = 0; i < chatFull.users.size(); i++) {
                                    TLRPC.User user = chatFull.users.get(i);
                                    MessagesController.getInstance(currentAccount).putUser(user, false);
                                    usersLocal.put(user.id, user);
                                }
                                for (int i = 0; i < allPeers.size(); i++) {
                                    peerIds.add(allPeers.get(i));
                                    this.usersSeen.add(usersLocal.get(allPeers.get(i)));
                                }
                            }
                            updateView();
                        }));
                    }
                }
            } else {
                updateView();
            }
        }));
    }

    private boolean loadingNextPage = false;
    private boolean endReached = false;

    static class ReactionLoader {
        public String currentOffset;
        public ArrayList<TLRPC.TL_messageUserReaction> data;
        public boolean endReached = false;
        public boolean loadingNextPage = false;
        RecyclerView.Adapter listener;
        RecyclerListView currentView;
        String filter;
    }

    HashMap<String, ReactionLoader> reactionLoaders = new HashMap<>(15);
    ReactionLoader generalReactionLoader;

    private void loadNextPage(String filter) {
        ReactionLoader rLoader;
        if (filter == null) {
            rLoader = generalReactionLoader;
        } else {
            rLoader = reactionLoaders.get(filter);
        }
        if (rLoader == null) {
            rLoader = new ReactionLoader();
            rLoader.filter = filter;
            rLoader.data = new ArrayList<>();
        }
        if (rLoader.loadingNextPage || rLoader.endReached) {
            return;
        }
        if (currentChat == null || currentMessage == null) {
            return;
        }
        rLoader.loadingNextPage = true;
        TLRPC.TL_messages_getMessageReactionsList req = new TLRPC.TL_messages_getMessageReactionsList();
        req.peer = MessagesController.getInputPeer(currentChat);
        req.id = currentMessage.getId();
        req.flags = 0;
        if (filter != null) {
            req.flags |= 1;
            req.reaction = filter;
            req.limit = 50;
        } else {

        }
        req.limit = 100;
        if (rLoader.currentOffset != null) {
            req.flags |= 2;
            req.offset = rLoader.currentOffset;
        }
        final ReactionLoader rLoaderFinal = rLoader;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            rLoaderFinal.loadingNextPage = false;
            if (err == null) {
                TLRPC.TL_messages_messageReactionsList list = (TLRPC.TL_messages_messageReactionsList) res;
                if (list.next_offset != null) {
                    rLoaderFinal.currentOffset = list.next_offset;
                } else {
                    rLoaderFinal.endReached = true;
                }
                rLoaderFinal.data.addAll(list.reactions);
                MessagesController.getInstance(currentAccount).putUsers(list.users, false);
                if (rLoaderFinal.listener != null) {
                    rLoaderFinal.listener.notifyDataSetChanged();
                }
            } else {
                rLoaderFinal.endReached = true;
                if (rLoaderFinal.listener != null) {
                    rLoaderFinal.listener.notifyDataSetChanged();
                }
            }
        }));
    }

    private void updateView() {
        if (onlyMessagesSeen) {
            setEnabled(usersSeen.size() > 0);
            for (int i = 0; i < 3; i++) {
                if (i < usersSeen.size()) {
                    avatarsImageView.setObject(i, currentAccount, usersSeen.get(i));
                } else {
                    avatarsImageView.setObject(i, currentAccount, null);
                }
            }
            if (usersSeen.size() == 1) {
                avatarsImageView.setTranslationX(AndroidUtilities.dp(24));
            } else if (usersSeen.size() == 2) {
                avatarsImageView.setTranslationX(AndroidUtilities.dp(12));
            } else {
                avatarsImageView.setTranslationX(0);
            }

            avatarsImageView.commitTransition(false);
            if (peerIds.size() == 1 && usersSeen.get(0) != null) {
                titleView.setText(ContactsController.formatName(usersSeen.get(0).first_name, usersSeen.get(0).last_name));
            } else {
                titleView.setText(LocaleController.formatPluralString(isVoice ? "MessagePlayed" : "MessageSeen", peerIds.size()));
            }
            titleView.animate().alpha(1f).setDuration(220).start();
            avatarsImageView.animate().alpha(1f).setDuration(220).start();
            flickerLoadingView.animate().alpha(0f).setDuration(220).setListener(new HideViewAfterAnimation(flickerLoadingView)).start();
        } else {
            setEnabled(true);
            ArrayList<TLRPC.TL_messageUserReaction> recentReactions = currentMessage.messageOwner.reactions.recent_reactons;
            for (int i = 0; i < 3; i++) {
                if (i < recentReactions.size()) {
                    avatarsImageView.setObject(i, currentAccount, MessagesController.getInstance(currentAccount).getUser(recentReactions.get(i).user_id));
                } else {
                    avatarsImageView.setObject(i, currentAccount, null);
                }
            }
            if (recentReactions.size() == 1) {
                avatarsImageView.setTranslationX(AndroidUtilities.dp(24));
            } else if (recentReactions.size() == 2) {
                avatarsImageView.setTranslationX(AndroidUtilities.dp(12));
            } else {
                avatarsImageView.setTranslationX(0);
            }

            avatarsImageView.commitTransition(false);
            if (currentMessage.isOutOwner()) {
                titleView.setText( String.format("%d/%d Reacted", totalReactionCount, peerIds.size()));
            } else {
                if (totalReactionCount == 1 && recentReactions.size() > 0) {
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(recentReactions.get(0).user_id);
                    titleView.setText(ContactsController.formatName(user.first_name, user.last_name));
                } else {
                    titleView.setText(String.format("%d Reactions", totalReactionCount));
                }
            }

            titleView.animate().alpha(1f).setDuration(220).start();
            avatarsImageView.animate().alpha(1f).setDuration(220).start();
            flickerLoadingView.animate().alpha(0f).setDuration(220).setListener(new HideViewAfterAnimation(flickerLoadingView)).start();
        }
    }

    public RecyclerListView createListView(ReactionLoader rLoader) {
        RecyclerListView recyclerListView = new RecyclerListView(getContext());
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        recyclerListView.setLayoutManager(layoutManager);
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
                if (position >= rLoader.data.size()) {
                    if (rLoader.endReached && allowMessageSeen) {
                        cell.setLoading(false);
                        TLRPC.User user = usersSeen.get(position - rLoader.data.size());
                        cell.setUserAndReaction(user, null);
                    } else {
                        cell.setLoading(true);
                    }
                } else {
                    cell.setLoading(false);
                    TLRPC.TL_messageUserReaction userReaction = rLoader.data.get(position);
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
                if (rLoader.endReached && rLoader.filter == null && allowMessageSeen) {
                    return rLoader.data.size() + usersSeen.size();
                }
                return rLoader.data.size() + (rLoader.endReached ? 0 : 2);
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
                    if (layoutManager.findLastVisibleItemPosition() < rLoader.data.size() - 10) {
                        loadNextPage(rLoader.filter);
                    }
                }
            }
        });
        recyclerListView.setOnItemClickListener((v, position) -> {
            if (delegate != null) {
                delegate.clickedUserProfile(((UserCell) v).user);
            }
        });
        rLoader.listener = adapter;
        recyclerListView.setAdapter(adapter);
        if (rLoader.data.isEmpty()) {
            loadNextPage(rLoader.filter);
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

        public TLRPC.User user;

        public void setUserAndReaction(TLRPC.User user, TLRPC.TL_availableReaction reaction) {
            this.user = user;
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
