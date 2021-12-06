package org.telegram.ui.Components;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MessagesController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

public class ReactionPickerLayout extends FrameLayout {
    HorizontalScrollView reactionScrollList;

    RectF backgroundRect;
    Paint backgroundPaint;

    static public final int padding = AndroidUtilities.dp(4);

    int currentAccount;

    HashMap<String, BackupImageView> reactionImageViews = new HashMap<>(15);
    ArrayList<BackupImageView> reactionImageViewList = new ArrayList<>(15);
    ArrayList<TLRPC.TL_availableReaction> allowedReactions;

    ValueAnimator fadeOutAnimator;

    int compensatingXOffset = 0;

    Runnable triggerEffect = new Runnable() {
        @Override
        public void run() {
            triggerRandomReactionEffect();
            postDelayed(this, 500);
        }
    };

    public ReactionPickerLayout(Context context, int currentAccount, ArrayList<TLRPC.TL_availableReaction> allowedReactions) {
        super(context);

        this.currentAccount = currentAccount;
        this.allowedReactions = allowedReactions;

        if (allowedReactions.size() == 2) {
            compensatingXOffset = 17;
        } else if (allowedReactions.size() == 1) {
            compensatingXOffset = 34;
        }

        reactionScrollList = new HorizontalScrollView(context);
        reactionScrollList.setHorizontalScrollBarEnabled(false);

        LinearLayout linearLayout = new LinearLayout(context);
        reactionScrollList.addView(linearLayout);

        if (allowedReactions == null) {
            allowedReactions = this.allowedReactions = MessagesController.getInstance(currentAccount).getAvailableReactions();
        }

        for (int i = 0; i < allowedReactions.size(); i++) {
            final TLRPC.TL_availableReaction reaction = allowedReactions.get(i);
            BackupImageView imageView = new BackupImageView(context);
            imageView.setImage(ImageLocation.getForDocument(reaction.select_animation), "30_30", null, null,"tgs",0,1, null);
            imageView.getImageReceiver().setAllowStartLottieAnimation(false);
            imageView.getImageReceiver().setAutoRepeat(2);
            reactionImageViews.put(reaction.reaction, imageView);
            reactionImageViewList.add(imageView);
            linearLayout.addView(imageView, LayoutHelper.createLinear(32, 32, Gravity.CENTER_VERTICAL, 4 + (i == 0 ? 4 : 0), 0, 4 + (i == allowedReactions.size() - 1 ? 4 : 0), 0));
            imageView.setOnClickListener(v -> {
                if (delegate != null) {
                    delegate.onReactionClick(reaction);
                }
            });
        }

        postDelayed(triggerEffect, 250);

        addView(reactionScrollList, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 44, Gravity.TOP | Gravity.RIGHT, 4, 4, 4 + compensatingXOffset, 4 + 16 + 4));
        backgroundRect = new RectF(0, 0, getMeasuredWidth(), getMeasuredHeight());
        mainArea.addRoundRect(backgroundRect, AndroidUtilities.dp(22), AndroidUtilities.dp(22), Path.Direction.CW);

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuBackground));
        backgroundPaint.setShadowLayer(padding, 0, AndroidUtilities.dp(1), Color.argb(72, 0, 0, 0));

        setWillNotDraw(false);

        setLayerType(LAYER_TYPE_SOFTWARE, backgroundPaint);

        fadeOutAnimator = ValueAnimator.ofFloat(1, 0).setDuration(250);
        fadeOutAnimator.addUpdateListener(an -> {
            circle1.reset();
            radius1 = AndroidUtilities.dp(1 + 7 * an.getAnimatedFraction());
            circle1.addCircle(getMeasuredWidth() - AndroidUtilities.dp(56), AndroidUtilities.dp(44 - 2) + padding, radius1, Path.Direction.CW);
            if (Build.VERSION.SDK_INT >= 19) {
                backGroundShape.op(mainArea, circle1, Path.Op.UNION);
            }
            radius2 = AndroidUtilities.dp(3) * an.getAnimatedFraction();
            setAlpha(an.getAnimatedFraction());
            invalidate();
        });
    }

    ReactionPickerDelegate delegate;

    public interface ReactionPickerDelegate {
        void onReactionClick(TLRPC.TL_availableReaction reaction);
    }

    public void setDelegate(ReactionPickerDelegate delegate) {
        this.delegate = delegate;
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(triggerEffect);
        super.onDetachedFromWindow();
    }

    public void fade(boolean in) {
        if (in) {
            fadeOutAnimator.start();
        } else {
            fadeOutAnimator.reverse();
        }
    }

    Random random = new Random();

    private void triggerRandomReactionEffect() {
        for (int i = 0; i < reactionImageViewList.size(); i++) {
            ImageReceiver image = reactionImageViewList.get(i).getImageReceiver();
            if (image.isAnimationRunning() || image.getLottieAnimation() == null) {
                continue;
            }
            if (random.nextFloat() < 0.25f) {
                if (image.getLottieAnimation().autoRepeatPlayCount == 0) {
                    image.getLottieAnimation().start();
                } else {
                    image.getLottieAnimation().restart();
                }
//                image.startAnimation();
            }
        }
    }

    Path mainArea = new Path();
    Path circle1 = new Path();
    Path circle2 = new Path();
    Path backGroundShape = new Path();

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            backgroundRect.set(padding, padding, getMeasuredWidth() - padding - AndroidUtilities.dp(compensatingXOffset), AndroidUtilities.dp(44) + padding);
            mainArea.reset();
            circle1.reset();
            circle2.reset();
            mainArea.addRoundRect(backgroundRect, AndroidUtilities.dp(22), AndroidUtilities.dp(22), Path.Direction.CW);
            circle1.addCircle(getMeasuredWidth() - AndroidUtilities.dp(56), AndroidUtilities.dp(44 - 2) + padding, AndroidUtilities.dp(8), Path.Direction.CW);
            circle2.addCircle(getMeasuredWidth() - AndroidUtilities.dp(50), AndroidUtilities.dp(44 + 14) + padding, AndroidUtilities.dp(3), Path.Direction.CW);
            if (Build.VERSION.SDK_INT >= 19) {
                backGroundShape.op(mainArea, circle1, Path.Op.UNION);
            }
        }
    }

    float radius1 = AndroidUtilities.dp(8);
    float radius2 = AndroidUtilities.dp(3);

    @Override
    protected void onDraw(Canvas canvas) {
//        canvas.drawPath(mainArea, backgroundPaint);
//        canvas.drawCircle(getMeasuredWidth() - AndroidUtilities.dp(54), AndroidUtilities.dp(44) + padding, AndroidUtilities.dp(8), backgroundPaint);
        canvas.drawPath(backGroundShape, backgroundPaint);
        canvas.drawCircle(getMeasuredWidth() - AndroidUtilities.dp(50), AndroidUtilities.dp(44 + 14) + padding, radius2, backgroundPaint);

        super.onDraw(canvas);
    }

    @Override
    public void dispatchDraw(Canvas canvas) {
        canvas.clipPath(mainArea);
        super.dispatchDraw(canvas);
    }
}
