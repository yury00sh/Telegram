/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.AdminedChannelCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.LoadingCell;
import org.telegram.ui.Cells.RadioButtonCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.InviteLinkBottomSheet;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkActionView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class ChatEditReactionsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private HeaderCell headerCell2;
    private TextInfoPrivacyCell checkTextView;
    private LinearLayout linearLayout;

    private LinearLayout reactionsLinearContainer;

    private TLRPC.Chat currentChat;
    private TLRPC.ChatFull info;
    private long chatId;
    private HashSet<String> currentlyEnabled = new HashSet<>(11);

    private boolean isForcePublic;
    private boolean reactionsEnabled;

    public ChatEditReactionsActivity(long id) {
        chatId = id;
        isForcePublic = false;
    }

    @Override
    public boolean onFragmentCreate() {
        currentChat = getMessagesController().getChat(chatId);
        if (currentChat == null) {
            currentChat = getMessagesStorage().getChatSync(chatId);
            if (currentChat != null) {
                getMessagesController().putChat(currentChat, true);
            } else {
                return false;
            }
            if (info == null) {
                info = getMessagesStorage().loadChatInfo(chatId, ChatObject.isChannel(currentChat), new CountDownLatch(1), false, false);
                if (info == null) {
                    return false;
                }
            }
        }
        if (info == null) {
            getMessagesController().loadFullChat(chatId, classGuid, true);
        } else {
            currentlyEnabled.addAll(info.available_reactions);
        }
        getNotificationCenter().addObserver(this, NotificationCenter.chatInfoDidLoad);
        getNotificationCenter().addObserver(this, NotificationCenter.availableReactionsUpdated);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        applyChanges();
        super.onFragmentDestroy();
        getNotificationCenter().removeObserver(this, NotificationCenter.chatInfoDidLoad);
        getNotificationCenter().removeObserver(this, NotificationCenter.availableReactionsUpdated);
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    processDone();
                }
            }
        });

        fragmentView = new ScrollView(context) {
            @Override
            public boolean requestChildRectangleOnScreen(View child, Rect rectangle, boolean immediate) {
                rectangle.bottom += AndroidUtilities.dp(60);
                return super.requestChildRectangleOnScreen(child, rectangle, immediate);
            }
        };
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        ScrollView scrollView = (ScrollView) fragmentView;
        scrollView.setFillViewport(true);
        linearLayout = new LinearLayout(context);
        LayoutTransition transition = new LayoutTransition();
        transition.setDuration(150);
        linearLayout.setLayoutTransition(transition);
        scrollView.addView(linearLayout, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        linearLayout.setOrientation(LinearLayout.VERTICAL);

//        actionBar.setTitle(LocaleController.getString("EditReactionsTitle", R.string.EditReactionsTitle));
        actionBar.setTitle("Reactions");

        reactionsEnabled = info.available_reactions != null && !info.available_reactions.isEmpty();

        TextCheckCell reactionsEnabledCell = new TextCheckCell(context);
        reactionsEnabledCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundUnchecked));
        reactionsEnabledCell.setColors(Theme.key_windowBackgroundCheckText, Theme.key_switchTrackBlue, Theme.key_switchTrackBlueChecked, Theme.key_switchTrackBlueThumb, Theme.key_switchTrackBlueThumbChecked);
        reactionsEnabledCell.setDrawCheckRipple(true);
        reactionsEnabledCell.setHeight(56);
        reactionsEnabledCell.setTag(Theme.key_windowBackgroundUnchecked);
        reactionsEnabledCell.setTextAndCheck("Enable Reactions", reactionsEnabled, false);
        reactionsEnabledCell.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        reactionsEnabledCell.setOnClickListener(view -> {
            TextCheckCell cell = (TextCheckCell) view;
            reactionsEnabled = !cell.isChecked();
            cell.setBackgroundColorAnimated(reactionsEnabled, Theme.getColor(reactionsEnabled ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
            cell.setChecked(reactionsEnabled);
            setReactionsVisible(reactionsEnabled);
        });
        reactionsEnabledCell.setBackgroundColor(Theme.getColor(reactionsEnabled ? Theme.key_windowBackgroundChecked : Theme.key_windowBackgroundUnchecked));
        linearLayout.addView(reactionsEnabledCell);


        checkTextView = new TextInfoPrivacyCell(context);
        checkTextView.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        checkTextView.setText(ChatObject.isChannel(currentChat) ? "Allow subscribers to react to channel posts." : "Allow members to react to channel posts.");
        linearLayout.addView(checkTextView);

        reactionsLinearContainer = new LinearLayout(context);
        reactionsLinearContainer.setOrientation(LinearLayout.VERTICAL);
        reactionsLinearContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        linearLayout.addView(reactionsLinearContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        headerCell2 = new HeaderCell(context, 23);
        headerCell2.setHeight(46);
        headerCell2.setText("Available reactions");
        reactionsLinearContainer.addView(headerCell2);

        ArrayList<TLRPC.TL_availableReaction> reactionTypes = getMessagesController().getAvailableReactions();
        if (reactionTypes  != null) {
            for (int i = 0; i < reactionTypes.size(); i++) {
                TLRPC.TL_availableReaction rType = reactionTypes.get(i);
                TextCheckCell reactionCell = new TextCheckCell(context);
                reactionCell.setTextAndCheck(rType.title, true, true);
                reactionCell.setBackgroundDrawable(Theme.getSelectorDrawable(false));
                reactionCell.setOnClickListener(v -> {
                    if (currentlyEnabled.contains(rType.reaction)) {
                        currentlyEnabled.remove(rType.reaction);
                        ((TextCheckCell) v).setChecked(false);
                    } else {
                        currentlyEnabled.add(rType.reaction);
                        ((TextCheckCell) v).setChecked(true);
                    }
                });
                BackupImageView icon = new BackupImageView(context);
                icon.setImage(ImageLocation.getForDocument(rType.static_icon), null, null, null,"webp",0,1, null);
                reactionCell.setPrependIcon(icon);
                if (reactionsEnabled) {
                    reactionCell.setChecked(currentlyEnabled.contains(rType.reaction));
                } else {
                    reactionCell.setChecked(true);
                    currentlyEnabled.add(rType.reaction);
                }
                reactionsLinearContainer.addView(reactionCell);
            }
        }
        
        setReactionsVisible(reactionsEnabled);

        return fragmentView;
    }

    public void setReactionsVisible(boolean value) {
        reactionsLinearContainer.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.chatInfoDidLoad) {
            TLRPC.ChatFull chatFull = (TLRPC.ChatFull) args[0];
            if (info == null) {
                currentlyEnabled.clear();
                currentlyEnabled.addAll(info.available_reactions);
            }
            if (chatFull.id == chatId) {
                info = chatFull;
            }
        } else if (id == NotificationCenter.availableReactionsUpdated) {

        }
    }

    public void setInfo(TLRPC.ChatFull chatFull) {
        info = chatFull;
    }

    private void applyChanges() {
        if (reactionsEnabled) {
            boolean match = info.available_reactions != null;
            if (match) {
                match = info.available_reactions.size() == currentlyEnabled.size();
            }
            if (match) {
                match = currentlyEnabled.containsAll(info.available_reactions);
            }
            if (!match) {
                ArrayList<String> reactions = new ArrayList<>(currentlyEnabled);
                getMessagesController().setChatAvailableReactions(getMessagesController().getInputPeer(-chatId), reactions, getFragmentForAlert(1));
                info.available_reactions = reactions;
            }
        } else if (info.available_reactions != null && !info.available_reactions.isEmpty()) {
            getMessagesController().setChatAvailableReactions(getMessagesController().getInputPeer(-chatId), null, getFragmentForAlert(1));
            info.available_reactions = new ArrayList<>();
        }
    }

    private void processDone() {
        applyChanges();
        finishFragment();
    }
}
