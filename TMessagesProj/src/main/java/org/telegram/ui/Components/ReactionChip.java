/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.Interpolator;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.MenuDrawable;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.Random;

public class ReactionChip extends View {

    public static final int height = AndroidUtilities.dp(26);

    private static TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private static Paint backPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF rect = new RectF();

    private int color;
    private int backgroundColor;
    private int backgroundAlpha;
    private View parent;

    private ImageReceiver reactionWebp;
    private AnimatedNumberLayout countLayout;
    private AvatarDrawable[] avatarDrawables;
    private ImageReceiver[] avatarImages;
    private InfiniteProgress infiniteProgress;

    private TLRPC.TL_reactionCount value;
    private int elevatedColor;

    private ValueAnimator selectAnimator;
    private ValueAnimator appearAnimation;
    private ValueAnimator crossfadeAnimation;
    private boolean isSelected = false;

//    AvatarsImageView avatarsImageView;

    public ReactionChip(Context context, int color, View parent) {
        super(context);

        this.parent = parent;

        this.elevatedColor = Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground);

        countLayout = new AnimatedNumberLayout(this, textPaint, true, true);
        textPaint.setTextSize(AndroidUtilities.dp(12));
        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));

        infiniteProgress = new InfiniteProgress(AndroidUtilities.dp(6));

//        ReactionChip self = this;
//        avatarsImageView = new AvatarsImageView(context, false) {
//            @Override
//            public void invalidate() {
//                super.invalidate();
//                self.invalidate();
//            }
//        };
//        avatarsImageView.setStyle(AvatarsImageView.STYLE_MESSAGE_SEEN);

        setColor(color);
        setBackColor(color, 25);

        reactionWebp = new ImageReceiver(this);
        reactionWebp.onAttachedToWindow();
        reactionWebp.setParentView(this);
//        Drawable background = Theme.createSelectorDrawable(color, 2, AndroidUtilities.dp(14));
//        background = Theme.getSelectorDrawable(color, false);

        setMeasuredDimension(getCalculatedWidth(), height);
        layout(0,0,getCalculatedWidth(), height);

        countLayout.setNumber(12, false);

        setClickable(true);

        setOnClickListener((v) -> {

//            int n = (new Random()).nextInt(10000);
//            setCount(n,true);
        });

        selectAnimator = ValueAnimator.ofFloat(0, 1f);
        selectAnimator.setDuration(200);
        selectAnimator.addUpdateListener(an -> {
            invalidate();
        });

        appearAnimation = ValueAnimator.ofFloat(0, 1f);
        appearAnimation.setDuration(150);
        appearAnimation.addUpdateListener(an -> {
            invalidate();
        });

        crossfadeAnimation = ValueAnimator.ofFloat(0, 1f);
        crossfadeAnimation.setDuration(150);
        crossfadeAnimation.addUpdateListener(an -> {
            invalidate();
        });
    }

    public ReactionChip(Context context, int color) {
        this(context, color, null);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (parent != null) {
            parent.invalidate();
        }
    }

    @Override
    public void invalidate(int l, int t, int r, int b) {
        super.invalidate(l, t, r, b);
        if (parent != null) {
            parent.invalidate();
        }
    }

//    public void setRecentReactions(ArrayList<TLRPC.TL_messageUserReaction> reactions, int account) {
//        avatarsImageView.setObject(0, account, reactions.get(0));
//    }

    private boolean invalidatesParentsParent = false;

    public void setInvalidatesParentsParent(boolean sure) {
        invalidatesParentsParent = sure;
    }

    public void setSelected(boolean selected) {
        if (selected == isSelected) {
            return;
        }
        if (!selected) {
            isSelected = false;
            selectAnimator.reverse();
        } else {
            isSelected = true;
            selectAnimator.start();
        }
        invalidate();
    }

    public void setIconDocument(TLRPC.Document doc) {
        reactionWebp.setImage(ImageLocation.getForDocument(doc), null, null, "webp", null, 1);
    }

    Drawable iconDrawable;

    public void setIconDrawable(Drawable drawable) {
        iconDrawable = drawable;
    }

    public void setValue(TLRPC.TL_reactionCount value) {
        this.value = value;
    }

    public TLRPC.TL_reactionCount getValue() {
        return value;
    }

    public void setColor(int color) {
        this.color = color;
        this.infiniteProgress.setColor(color);
        setBackgroundDrawable(Theme.createRadSelectorDrawable(color, 14, 14));
    }

    public void setBackColor(int color, int alpha) {
        backgroundColor = color;
        backgroundAlpha = alpha;
        backgroundPaint = null;
    }

    private Paint backgroundPaint;
    private int realColor;

    public void setBackPaint(Paint paint, int realColor) {
        backgroundPaint = paint;
        this.realColor = realColor;
    }

    public void setCount(int n, boolean animated) {
        countLayout.setNumber(n, animated);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getCalculatedWidth(), getCalculatedHeight());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
    }

    public int getCalculatedHeight() {
        return height + AndroidUtilities.dp(2);
    }

    public int getCalculatedWidth() {
        return countLayout.getWidth() + AndroidUtilities.dp(6 + 20 + 3 + 10 + 2);
    }

//    @Override
//    public boolean post(Runnable action) {
//        if (parent != null) {
////            return false;
//            return parent.post(action);
//        }
//        return super.post(action);
//    }
//    @Override
//    public boolean postDelayed(Runnable action, long ms) {
//        if (parent != null) {
////            return false;
//            return parent.postDelayed(action, ms);
//        }
//        return super.postDelayed(action, ms);
//    }

    private boolean elevated;
    private ValueAnimator elevatedProgress;

    public void setElevated(boolean elevated) {
        this.elevated = elevated;
        if (elevatedProgress == null) {
            elevatedProgress = ValueAnimator.ofFloat(0, 1f).setDuration(150);
//            elevatedProgress.setInterpolator(CubicBezierInterpolator.EASE_IN);
            elevatedProgress.addUpdateListener(animation -> {
                invalidate();
            });
        }
        if (elevated) {
            elevatedProgress.start();
        } else {
            elevatedProgress.reverse();
        }
    }

    public boolean isElevated() {
        return elevated || elevatedProgress != null && ((float) elevatedProgress.getAnimatedValue() != 0);
    }

    @Override
    public void draw(Canvas canvas) {
        if (parent == null) {
            super.draw(canvas);
        }
    }

    public void playAppear() {
        appearAnimation.start();
    }

    public void drawAfter(Canvas canvas) {
        super.draw(canvas);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
//        getBackground().setBounds(0,0,getCalculatedWidth(), height);
        canvas.save();

        canvas.translate(AndroidUtilities.dp(1), AndroidUtilities.dp(1));

        if (appearAnimation.isRunning()) {
            canvas.scale(appearAnimation.getAnimatedFraction(), appearAnimation.getAnimatedFraction(), getCalculatedWidth() / 2f, height / 2f);
        }

        rect.set(0,0, getCalculatedWidth() - AndroidUtilities.dp(2), height - 2);

        if (backgroundPaint != null) {
            canvas.drawRoundRect(rect, AndroidUtilities.dp(13), AndroidUtilities.dp(13), backgroundPaint);
            if (parent != null) {
//                Theme.applyServiceShaderMatrixForView(this, parent);
            }
            if (Theme.hasGradientService()) {
                canvas.drawRoundRect(rect, AndroidUtilities.dp(13), AndroidUtilities.dp(13), Theme.chat_actionBackgroundGradientDarkenPaint);
            }
            invalidate();
        } else {
            backPaint.setColor(backgroundColor);
            backPaint.setAlpha(backgroundAlpha);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(13), AndroidUtilities.dp(13), backPaint);
        }
        if (elevatedProgress != null) {
            float progress = (float) elevatedProgress.getAnimatedValue();
            if (progress != 0) {
                backPaint.setColor(Color.WHITE);
                backPaint.setAlpha((int) (255 * progress));
                canvas.drawRoundRect(rect, AndroidUtilities.dp(13), AndroidUtilities.dp(13), backPaint);
            }
        }

        if (isElevated() && backgroundPaint != null) {
            int transitionalColor = ColorUtils.blendARGB(color, realColor, (float) elevatedProgress.getAnimatedValue());
            textPaint.setColor(transitionalColor);
            backPaint.setColor(transitionalColor);
        } else {
            textPaint.setColor(color);
            backPaint.setColor(color);
        }

        float progress = (float) selectAnimator.getAnimatedValue();
        if (progress != 0) {
            backPaint.setStyle(Paint.Style.STROKE);
            backPaint.setStrokeWidth(progress * AndroidUtilities.dp(1.2f));
            backPaint.setAlpha(255);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(13), AndroidUtilities.dp(13), backPaint);
            backPaint.setStyle(Paint.Style.FILL);
            if (progress != 1) {
                invalidate();
            }
        }

        backPaint.setAlpha(120);
        if (infiniteProgress != null) {
            if (reactionWebp.hasImageDrawable()) {
                reactionWebp.setImageCoords(AndroidUtilities.dp(6), AndroidUtilities.dp(3), AndroidUtilities.dp(18), AndroidUtilities.dp(18));
                reactionWebp.draw(canvas);
            } else if (iconDrawable != null) {
                iconDrawable.setBounds(AndroidUtilities.dp(6), AndroidUtilities.dp(3), AndroidUtilities.dp(18 + 6), AndroidUtilities.dp(18 + 3));
                iconDrawable.draw(canvas);
            } else {
                infiniteProgress.draw(canvas, AndroidUtilities.dp(16), AndroidUtilities.dp(13), 1);
                invalidate();
            }
        }
        canvas.translate(AndroidUtilities.dp(8 + 20 + 3), AndroidUtilities.dp(6));

        countLayout.draw(canvas);
        canvas.restore();
    }
}
