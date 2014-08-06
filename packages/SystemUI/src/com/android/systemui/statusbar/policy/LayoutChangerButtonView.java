/*
 * Copyright (C) 2014 VanirAOSP && The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;

import com.android.internal.util.aokp.AwesomeAction;
import com.android.internal.util.aokp.NavBarHelpers;
import com.android.internal.util.vanir.AwesomeConstants;
import com.android.internal.util.vanir.AwesomeConstants.AwesomeConstant;
import com.android.systemui.R;

public class LayoutChangerButtonView extends ImageView {
    private static final String TAG = "StatusBar.LayoutChangerButtonView";
    public static final String ACTION_MENU = AwesomeConstant.ACTION_MENU.value();
    public static final String LAYOUT_RIGHT = AwesomeConstant.ACTION_LAYOUT_RIGHT.value();

    final float GLOW_MAX_SCALE_FACTOR = 1.8f;
    public static final float DEFAULT_QUIESCENT_ALPHA = 0.25f;

    int mTouchSlop;
    Drawable mGlowBG;
    int mGlowWidth, mGlowHeight;
    float mGlowAlpha = 0f, mGlowScale = 1f;
    float mDrawingAlpha = 1f;
    float mQuiescentAlpha = DEFAULT_QUIESCENT_ALPHA;
    RectF mRect = new RectF();
    AnimatorSet mPressedAnim;
    Animator mAnimateToQuiescent = new ObjectAnimator();
    AnimatorSet as = mPressedAnim = new AnimatorSet();

    LayoutButtonInfo mActions;

    public LayoutChangerButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LayoutChangerButtonView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        setDrawingAlpha(mQuiescentAlpha);
        if (mGlowBG != null) {
            mGlowWidth = mGlowBG.getIntrinsicWidth();
            mGlowHeight = mGlowBG.getIntrinsicHeight();
        }

        setClickable(true);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setLongClickable(false);
    }

    public void setButtonActions(LayoutButtonInfo actions) {
        this.mActions = actions;
        setTag(mActions.singleAction); // should be OK even if it's null
        setImage();
    }

    public void setMenuAction(boolean show) {
        if (show) {
            mActions.singleAction = ACTION_MENU;
            setImageResource(R.drawable.ic_sysbar_menu);
        } else {
            mActions.singleAction = LAYOUT_RIGHT;
            setImageResource(R.drawable.ic_sysbar_layout_right);
        }
    }

    public void setImage() {
        setImageDrawable(NavBarHelpers.getIconImage(mContext, LAYOUT_RIGHT));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mGlowBG != null) {
            canvas.save();
            final int w = getWidth();
            final int h = getHeight();
            final float aspect = (float) mGlowWidth / mGlowHeight;
            final int drawW = (int) (h * aspect);
            final int drawH = h;
            final int margin = (drawW - w) / 2;
            canvas.scale(mGlowScale, mGlowScale, w * 0.5f, h * 0.5f);
            mGlowBG.setBounds(-margin, 0, drawW - margin, drawH);
            mGlowBG.setAlpha((int) (mDrawingAlpha * mGlowAlpha * 255));
            mGlowBG.draw(canvas);
            canvas.restore();
            mRect.right = w;
            mRect.bottom = h;
        }
        super.onDraw(canvas);
    }

    public void setQuiescentAlpha(float alpha, boolean animate) {
        mAnimateToQuiescent.cancel();
        alpha = Math.min(Math.max(alpha, 0), 1);
        if (alpha == mQuiescentAlpha && alpha == mDrawingAlpha) return;
        mQuiescentAlpha = alpha;
        if (mGlowBG != null && animate) {
            mAnimateToQuiescent = animateToQuiescent();
            mAnimateToQuiescent.start();
        } else {
            setDrawingAlpha(mQuiescentAlpha);
        }
    }

    private ObjectAnimator animateToQuiescent() {
        return ObjectAnimator.ofFloat(this, "drawingAlpha", mQuiescentAlpha);
    }

    public float getQuiescentAlpha() {
        return mQuiescentAlpha;
    }

    public float getDrawingAlpha() {
        return mDrawingAlpha;
    }

    public void setDrawingAlpha(float x) {
        setAlpha((int) (x * 255));
        mDrawingAlpha = x;
    }

    public float getGlowAlpha() {
        if (mGlowBG == null) return 0;
        return mGlowAlpha;
    }

    public void setGlowAlpha(float x) {
        if (mGlowBG == null) return;
        mGlowAlpha = x;
        invalidate();
    }

    public float getGlowScale() {
        if (mGlowBG == null) return 0;
        return mGlowScale;
    }

    public void setGlowScale(float x) {
        if (mGlowBG == null) return;
        mGlowScale = x;
        final float w = getWidth();
        final float h = getHeight();
        if (GLOW_MAX_SCALE_FACTOR <= 1.0f) {
            // this only works if we know the glow will never leave our bounds
            invalidate();
        } else {
            final float rx = (w * (GLOW_MAX_SCALE_FACTOR - 1.0f)) / 2.0f + 1.0f;
            final float ry = (h * (GLOW_MAX_SCALE_FACTOR - 1.0f)) / 2.0f + 1.0f;
            com.android.systemui.SwipeHelper.invalidateGlobalRegion(
                    this,
                    new RectF(getLeft() - rx,
                            getTop() - ry,
                            getRight() + rx,
                            getBottom() + ry));

            if (getParent() != null) ((View) getParent()).invalidate();
        }
    }

    public void setPressed(boolean pressed) {
        if (mGlowBG != null) {
            if (pressed != isPressed()) {
                if (mPressedAnim != null && mPressedAnim.isRunning()) {
                    mPressedAnim.cancel();
                }
                if (pressed) {
                    if (mGlowScale < GLOW_MAX_SCALE_FACTOR)
                        mGlowScale = GLOW_MAX_SCALE_FACTOR;
                    if (mGlowAlpha < mQuiescentAlpha)
                        mGlowAlpha = mQuiescentAlpha;
                    setDrawingAlpha(1f);
                    as.playTogether(
                            ObjectAnimator.ofFloat(this, "glowAlpha", 1f),
                            ObjectAnimator.ofFloat(this, "glowScale", GLOW_MAX_SCALE_FACTOR)
                    );
                    as.setDuration(50);
                } else {
                    mAnimateToQuiescent.cancel();
                    mAnimateToQuiescent = animateToQuiescent();
                    as.playTogether(
                            ObjectAnimator.ofFloat(this, "glowAlpha", 0f),
                            ObjectAnimator.ofFloat(this, "glowScale", 1f),
                            mAnimateToQuiescent
                    );
                    as.setDuration(500);
                }
                as.start();
            }
        }
        super.setPressed(pressed);
    }

    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                setPressed(true);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                break;
            case MotionEvent.ACTION_MOVE:
                int x = (int) ev.getX();
                int y = (int) ev.getY();
                setPressed(x >= -mTouchSlop
                        && x < getWidth() + mTouchSlop
                        && y >= -mTouchSlop
                        && y < getHeight() + mTouchSlop);
                break;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                break;
            case MotionEvent.ACTION_UP:
                boolean playSound = isPressed();
                setPressed(false);
                if (playSound) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                }
                doSinglePress();
                break;
        }
        return true;
    }

    private void doSinglePress() {
        if (callOnClick()) {
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
        }
        AwesomeAction.launchAction(mContext, mActions.singleAction);
        sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
    }

    public void setGlowBackground(int resId) {
        mGlowBG = getResources().getDrawable(resId);
        if (mGlowBG != null) {
            setDrawingAlpha(mDrawingAlpha);
            mGlowWidth = mGlowBG.getIntrinsicWidth();
            mGlowHeight = mGlowBG.getIntrinsicHeight();
        }
    }

    public static class LayoutButtonInfo {
        String singleAction;

        public LayoutButtonInfo(String singleTap) {
            this.singleAction = singleTap;
        }
    }
}
