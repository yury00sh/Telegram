package org.telegram.ui.Components;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import androidx.annotation.Keep;

import android.graphics.Shader;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class CheckBoxBase {

    private View parentView;
    private Rect bounds = new Rect();
    private RectF rect = new RectF();

    private static Paint paint;
    private static Paint eraser;
    private Paint checkPaint;
    private Paint backgroundPaint;
    private TextPaint textPaint;

    private Path path = new Path();

    private Bitmap drawBitmap;
    private Canvas bitmapCanvas;

    private boolean enabled = true;

    private boolean attachedToWindow;

    private float backgroundAlpha = 1.0f;

    private float progress;
    private ObjectAnimator checkAnimator;

    private boolean isChecked;

    private String checkColorKey = Theme.key_checkboxCheck;
    private String backgroundColorKey = Theme.key_chat_serviceBackground;
    private String background2ColorKey = Theme.key_chat_serviceBackground;

    private boolean useDefaultCheck;

    private boolean drawUnchecked = true;
    private int backgroundType;

    private float size;

    private String checkedText;

    private ProgressDelegate progressDelegate;

    private Theme.MessageDrawable messageDrawable;
    private final Theme.ResourcesProvider resourcesProvider;

    public interface ProgressDelegate {
        void setProgress(float progress);
    }

    public CheckBoxBase(View parent, int sz, Theme.ResourcesProvider resourcesProvider) {
        this.resourcesProvider = resourcesProvider;
        parentView = parent;
        size = sz;
        if (paint == null) {
            paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            eraser = new Paint(Paint.ANTI_ALIAS_FLAG);
            eraser.setColor(0);
            eraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        }
        checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
        checkPaint.setStrokeWidth(AndroidUtilities.dp(1.9f));

        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(AndroidUtilities.dp(1.2f));

        drawBitmap = Bitmap.createBitmap(AndroidUtilities.dp(size), AndroidUtilities.dp(size), Bitmap.Config.ARGB_4444);
        bitmapCanvas = new Canvas(drawBitmap);
    }

    private int duration = 200;

    public void setDuration(int mills) {
        duration = mills;
    }

    public void onAttachedToWindow() {
        attachedToWindow = true;
    }

    public void onDetachedFromWindow() {
        attachedToWindow = false;
    }

    public void setBounds(int x, int y, int width, int height) {
        bounds.left = x;
        bounds.top = y;
        bounds.right = x + width;
        bounds.bottom = y + height;
    }

    public void setDrawUnchecked(boolean value) {
        drawUnchecked = value;
    }

    @Keep
    public void setProgress(float value) {
        if (progress == value) {
            return;
        }
        progress = value;
        invalidate();
        if (progressDelegate != null) {
            progressDelegate.setProgress(value);
        }
    }

    private void invalidate() {
        if (parentView.getParent() != null) {
            View parent = (View) parentView.getParent();
            parent.invalidate();
        }
        parentView.invalidate();
    }

    public void setProgressDelegate(ProgressDelegate delegate) {
        progressDelegate = delegate;
    }

    @Keep
    public float getProgress() {
        return progress;
    }

    public boolean isChecked() {
        return isChecked;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public void setBackgroundType(int type) {
        backgroundType = type;
        if (type == 12 || type == 13) {
            backgroundPaint.setStrokeWidth(AndroidUtilities.dp(1));
        } else if (type == 4 || type == 5) {
            backgroundPaint.setStrokeWidth(AndroidUtilities.dp(1.9f));
            if (type == 5) {
                checkPaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
            }
        } else if (type == 3) {
            backgroundPaint.setStrokeWidth(AndroidUtilities.dp(1.2f));
        } else if (type != 0) {
            backgroundPaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        }
    }

    private void cancelCheckAnimator() {
        if (checkAnimator != null) {
            checkAnimator.cancel();
            checkAnimator = null;
        }
    }

    private void animateToCheckedState(boolean newCheckedState) {
        checkAnimator = ObjectAnimator.ofFloat(this, "progress", newCheckedState ? 1 : 0);
        checkAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(checkAnimator)) {
                    checkAnimator = null;
                }
                if (!isChecked) {
                    checkedText = null;
                }
            }
        });
        checkAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        checkAnimator.setDuration(duration);
        checkAnimator.start();
    }

    public void setColor(String background, String background2, String check) {
        backgroundColorKey = background;
        background2ColorKey = background2;
        checkColorKey = check;
    }

    public void setBackgroundDrawable(Theme.MessageDrawable drawable) {
        messageDrawable = drawable;
    }

    public void setUseDefaultCheck(boolean value) {
        useDefaultCheck = value;
    }

    public void setBackgroundAlpha(float alpha) {
        backgroundAlpha = alpha;
    }

    public void setNum(int num) {
        if (num >= 0) {
            checkedText = "" + (num + 1);
        } else if (checkAnimator == null) {
            checkedText = null;
        }
        invalidate();
    }

    public void setChecked(boolean checked, boolean animated) {
        setChecked(-1, checked, animated);
    }

    public void setChecked(int num, boolean checked, boolean animated) {
        if (num >= 0) {
            checkedText = "" + (num + 1);
            invalidate();
        }
        if (checked == isChecked) {
            return;
        }
        isChecked = checked;

        if (attachedToWindow && animated) {
            animateToCheckedState(checked);
        } else {
            cancelCheckAnimator();
            setProgress(checked ? 1.0f : 0.0f);
        }
    }

    public void draw(Canvas canvas) {
        if (drawBitmap == null) {
            return;
        }

        drawBitmap.eraseColor(0);
        float rad = AndroidUtilities.dp(size / 2);
        float outerRad = rad;
        if (backgroundType == 12 || backgroundType == 13) {
            rad = outerRad = AndroidUtilities.dp(10);
        } else {
            if (backgroundType != 0 && backgroundType != 11) {
                outerRad -= AndroidUtilities.dp(0.2f);
            }
        }

        float roundProgress = progress >= 0.5f ? 1.0f : progress / 0.5f;

        int cx = bounds.centerX();
        int cy = bounds.centerY();

        if (backgroundColorKey != null) {
            if (drawUnchecked) {
                if (backgroundType == 12 || backgroundType == 13) {
                    paint.setColor(getThemedColor(backgroundColorKey));
                    paint.setAlpha((int) (255 * backgroundAlpha));
                    backgroundPaint.setColor(getThemedColor(checkColorKey));
                } else if (backgroundType == 6 || backgroundType == 7) {
                    paint.setColor(getThemedColor(background2ColorKey));
                    backgroundPaint.setColor(getThemedColor(checkColorKey));
                } else if (backgroundType == 10) {
                    backgroundPaint.setColor(getThemedColor(background2ColorKey));
                } else {
                    paint.setColor((Theme.getServiceMessageColor() & 0x00ffffff) | 0x28000000);
                    backgroundPaint.setColor(getThemedColor(checkColorKey));
                }
            } else {
                backgroundPaint.setColor(AndroidUtilities.getOffsetColor(0x00ffffff, getThemedColor(background2ColorKey != null ? background2ColorKey : checkColorKey), progress, backgroundAlpha));
            }
        } else {
            if (drawUnchecked) {
                paint.setColor(Color.argb((int) (25 * backgroundAlpha), 0, 0, 0));
                if (backgroundType == 8) {
                    backgroundPaint.setColor(getThemedColor(background2ColorKey));
                } else {
                    backgroundPaint.setColor(AndroidUtilities.getOffsetColor(0xffffffff, getThemedColor(checkColorKey), progress, backgroundAlpha));
                }
            } else {
                backgroundPaint.setColor(AndroidUtilities.getOffsetColor(0x00ffffff, getThemedColor(background2ColorKey != null ? background2ColorKey : checkColorKey), progress, backgroundAlpha));
            }
        }

        if (drawUnchecked && backgroundType >= 0) {
            if (backgroundType == 12 || backgroundType == 13) {
                //draw nothing
            } else if (backgroundType == 8 || backgroundType == 10) {
                canvas.drawCircle(cx, cy, rad - AndroidUtilities.dp(1.5f), backgroundPaint);
            } else if (backgroundType == 6 || backgroundType == 7) {
                canvas.drawCircle(cx, cy, rad - AndroidUtilities.dp(1), paint);
                canvas.drawCircle(cx, cy, rad - AndroidUtilities.dp(1.5f), backgroundPaint);
            } else {
                canvas.drawCircle(cx, cy, rad, paint);
            }
        }
        paint.setColor(getThemedColor(checkColorKey));
        if (backgroundType != -1 && backgroundType != 7 && backgroundType != 8 && backgroundType != 9 && backgroundType != 10) {
            if (backgroundType == 12 || backgroundType == 13) {
                backgroundPaint.setStyle(Paint.Style.FILL);
                if (messageDrawable != null && messageDrawable.hasGradient()) {
                    Shader shader = messageDrawable.getGradientShader();
                    Matrix matrix = messageDrawable.getMatrix();
                    matrix.reset();
                    messageDrawable.applyMatrixScale();
                    matrix.postTranslate(0, -messageDrawable.getTopY() + bounds.top);
                    shader.setLocalMatrix(matrix);
                    backgroundPaint.setShader(shader);
                } else {
                    backgroundPaint.setShader(null);
                }
                canvas.drawCircle(cx, cy, (rad - AndroidUtilities.dp(1)) * backgroundAlpha, backgroundPaint);
                backgroundPaint.setStyle(Paint.Style.STROKE);
            } else if (backgroundType == 0 || backgroundType == 11) {
                canvas.drawCircle(cx, cy, rad, backgroundPaint);
            } else {
                rect.set(cx - outerRad, cy - outerRad, cx + outerRad, cy + outerRad);
                int startAngle;
                int sweepAngle;
                if (backgroundType == 6) {
                    startAngle = 0;
                    sweepAngle = (int) (-360 * progress);
                } else if (backgroundType == 1) {
                    startAngle = -90;
                    sweepAngle = (int) (-270 * progress);
                } else {
                    startAngle = 90;
                    sweepAngle = (int) (270 * progress);
                }

                if (backgroundType == 6) {
                    int color = getThemedColor(Theme.key_dialogBackground);
                    int alpha = Color.alpha(color);
                    backgroundPaint.setColor(color);
                    backgroundPaint.setAlpha((int) (alpha * progress));
                    canvas.drawArc(rect, startAngle, sweepAngle, false, backgroundPaint);
                    color = getThemedColor(Theme.key_chat_attachPhotoBackground);
                    alpha = Color.alpha(color);
                    backgroundPaint.setColor(color);
                    backgroundPaint.setAlpha((int) (alpha * progress));
                }
                canvas.drawArc(rect, startAngle, sweepAngle, false, backgroundPaint);
            }
        }

        if (roundProgress > 0) {
            float checkProgress = progress < 0.5f ? 0.0f : (progress - 0.5f) / 0.5f;

            if (backgroundType == 9) {
                paint.setColor(getThemedColor(background2ColorKey));
            } else if (backgroundType == 11 || backgroundType == 6 || backgroundType == 7 || backgroundType == 10 || !drawUnchecked && backgroundColorKey != null) {
                paint.setColor(getThemedColor(backgroundColorKey));
            } else {
                paint.setColor(getThemedColor(enabled ? Theme.key_checkbox : Theme.key_checkboxDisabled));
            }
            if (!useDefaultCheck && checkColorKey != null) {
                checkPaint.setColor(getThemedColor(checkColorKey));
            } else {
                checkPaint.setColor(getThemedColor(Theme.key_checkboxCheck));
            }

            if (backgroundType != -1) {
                if (backgroundType == 12 || backgroundType == 13) {
                    paint.setAlpha((int) (255 * roundProgress));
                    bitmapCanvas.drawCircle(drawBitmap.getWidth() / 2, drawBitmap.getHeight() / 2, rad * roundProgress, paint);
                } else {
                    rad -= AndroidUtilities.dp(0.5f);
                    bitmapCanvas.drawCircle(drawBitmap.getWidth() / 2, drawBitmap.getHeight() / 2, rad, paint);
                    bitmapCanvas.drawCircle(drawBitmap.getWidth() / 2, drawBitmap.getHeight() / 2, rad * (1.0f - roundProgress), eraser);
                }
                canvas.drawBitmap(drawBitmap, cx - drawBitmap.getWidth() / 2, cy - drawBitmap.getHeight() / 2, null);
            }
            if (checkProgress != 0) {
                if (checkedText != null) {
                    if (textPaint == null) {
                        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                        textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    }
                    final float textSize, y;
                    switch (checkedText.length()) {
                        case 0:
                        case 1:
                        case 2:
                            textSize = 14f;
                            y = 18f;
                            break;
                        case 3:
                            textSize = 10f;
                            y = 16.5f;
                            break;
                        default:
                            textSize = 8f;
                            y = 15.75f;
                    }
                    textPaint.setTextSize(AndroidUtilities.dp(textSize));
                    textPaint.setColor(getThemedColor(checkColorKey));
                    canvas.save();
                    canvas.scale(checkProgress, 1.0f, cx, cy);
                    canvas.drawText(checkedText, cx - textPaint.measureText(checkedText) / 2f, AndroidUtilities.dp(y), textPaint);
                    canvas.restore();
                } else {
                    path.reset();
                    float scale = 1f;
                    if (backgroundType == -1) {
                        scale = 1.4f;
                    } else if (backgroundType == 5) {
                        scale = 0.8f;
                    }
                    float checkSide = AndroidUtilities.dp(9 * scale) * checkProgress;
                    float smallCheckSide = AndroidUtilities.dp(4 * scale) * checkProgress;
                    int x = cx - AndroidUtilities.dp(1.5f);
                    int y = cy + AndroidUtilities.dp(4);
                    float side = (float) Math.sqrt(smallCheckSide * smallCheckSide / 2.0f);
                    path.moveTo(x - side, y - side);
                    path.lineTo(x, y);
                    side = (float) Math.sqrt(checkSide * checkSide / 2.0f);
                    path.lineTo(x + side, y - side);
                    canvas.drawPath(path, checkPaint);
                }
            }
        }
    }

    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }
}
