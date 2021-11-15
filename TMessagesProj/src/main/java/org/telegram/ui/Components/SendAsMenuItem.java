package org.telegram.ui.Components;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.ShareDialogCell;

public class SendAsMenuItem extends FrameLayout {

    private TextView titleTextView;
    private TextView subtitleTextView;
    private BackupImageView avatarImageView;
    private AvatarDrawable avatarDrawable;
    private CheckBox2 checkBox;

    public SendAsMenuItem(Context context) {
        super(context);

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(19));
        avatarDrawable = new AvatarDrawable();

        setBackgroundDrawable(Theme.getSelectorDrawable(false));

        addView(avatarImageView, LayoutHelper.createFrame(38, 38, Gravity.LEFT | Gravity.CENTER, 12, 0, 0, 0));

        checkBox = new CheckBox2(context, 21, null);
        checkBox.setColor(Theme.key_dialogRoundCheckBox, Theme.key_dialogBackground, Theme.key_dialogRoundCheckBoxCheck);
        checkBox.setDrawUnchecked(false);
        checkBox.setDrawBackgroundAsArc(4);
        checkBox.setDuration(150);
        checkBox.setProgressDelegate(progress -> {
            float scale = 1.0f - (1.0f - 0.857f) * checkBox.getProgress();
            avatarImageView.setScaleX(scale);
            avatarImageView.setScaleY(scale);
            invalidate();
            if (progress >= 1.0f && checkDoneDelegate != null) {
                checkDoneDelegate.done();
            }
        });
        addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 19, -42, 0, 0));
        checkBox.setChecked(true, true);


        titleTextView = new TextView(context);
        titleTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        titleTextView.setTextSize(16);
        titleTextView.setGravity(Gravity.LEFT);
        titleTextView.setMaxLines(1);
        titleTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(titleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 62, 7, 0, 0));


        subtitleTextView = new TextView(context);
        subtitleTextView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText3));
        subtitleTextView.setTag(Theme.key_actionBarDefaultSubtitle);
        subtitleTextView.setTextSize(13);
        subtitleTextView.setGravity(Gravity.LEFT);
        addView(subtitleTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 62, 0, 0, 8));
    }

    public interface CheckBoxAnimationDelegate {
        void done();
    }

    private CheckBoxAnimationDelegate checkDoneDelegate;

    public void setCheckboxDoneDelegate(CheckBoxAnimationDelegate delegate) {
        this.checkDoneDelegate = delegate;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int cx = avatarImageView.getLeft() + avatarImageView.getMeasuredWidth() / 2;
        int cy = avatarImageView.getTop() + avatarImageView.getMeasuredHeight() / 2;
        Theme.checkboxSquare_checkPaint.setColor(getThemedColor(Theme.key_dialogRoundCheckBox));
        Theme.checkboxSquare_checkPaint.setAlpha((int) (checkBox.getProgress() * 255));
        canvas.drawCircle(cx, cy, AndroidUtilities.dp(19), Theme.checkboxSquare_checkPaint);
    }

    public void setAvatarChat(TLRPC.Chat chat) {
        avatarDrawable.setInfo(chat);
        avatarImageView.setForUserOrChat(chat, avatarDrawable);
    }

    public void setAvatarUser(TLRPC.User user) {
        avatarDrawable.setInfo(user);
        avatarImageView.setForUserOrChat(user, avatarDrawable);
    }

    public void setChecked(boolean checked, boolean animated) {
        checkBox.setChecked(checked, animated);
    }

    public boolean isChecked() {
        return checkBox.isChecked();
    }

    public void setTitle(String title) {
        titleTextView.setText(title);
    }

    public void setSubtitle(String subtitle) {
        subtitleTextView.setText(subtitle);
    }

    private TLRPC.Peer peer;

    public void setPeerStored(TLRPC.Peer peer) {
        this.peer = peer;
    }

    public TLRPC.Peer getPeerStored() {
        return this.peer;
    }

    private int getThemedColor(String key) {
        return Theme.getColor(key);
    }
}
