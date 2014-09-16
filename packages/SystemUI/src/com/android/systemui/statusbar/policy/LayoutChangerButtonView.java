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
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;

import com.android.internal.util.aokp.AwesomeAction;
import com.android.internal.util.aokp.NavBarHelpers;
import com.android.internal.util.vanir.AwesomeConstants;
import com.android.internal.util.vanir.AwesomeConstants.AwesomeConstant;
import com.android.systemui.R;

public class LayoutChangerButtonView extends KeyButtonView {
    private static final String TAG = "StatusBar.LayoutChangerButtonView";
    public static final String ACTION_MENU = AwesomeConstant.ACTION_MENU.value();
    public static final String LAYOUT_RIGHT = AwesomeConstant.ACTION_LAYOUT_RIGHT.value();

    final float GLOW_MAX_SCALE_FACTOR = 1.8f;
    public static final float LAYOUT_CHANGER_QUIESCENT_ALPHA = 0.30f;

    int mTouchSlop;
    final float mQuiescentAlpha = LAYOUT_CHANGER_QUIESCENT_ALPHA;
    float mDrawingAlpha = 1f;
    AnimatorSet mPressedAnim;
    Animator mAnimateToQuiescent = new ObjectAnimator();
    AnimatorSet as = mPressedAnim = new AnimatorSet();

    KeyButtonInfo mActions;

    public LayoutChangerButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LayoutChangerButtonView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        setDrawingAlpha(mQuiescentAlpha);

        setClickable(true);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setLongClickable(false);
    }

    @Override
    public void setButtonActions(KeyButtonInfo actions) {
        this.mActions = actions;
        setTag(mActions.singleAction);
        setImage();
    }

    public void setMenuAction(boolean show, boolean isVertical, boolean isTablet) {
        if (show) {
            mActions.singleAction = ACTION_MENU;
            setImageResource(R.drawable.ic_sysbar_menu);
        } else {
            mActions.singleAction = LAYOUT_RIGHT;
            if (isTablet) {
                setImageResource(R.drawable.ic_sysbar_layout_right);
            } else {
                setImageResource(!isVertical
                        ? R.drawable.ic_sysbar_layout_right_landscape
                        : R.drawable.ic_sysbar_layout_right);
            }
        }
    }

    @Override
    public void setImage() {
        setImageDrawable(NavBarHelpers.getIconImage(mContext, LAYOUT_RIGHT));
    }

    @Override
    public void setImage(final Resources res) { }

    public void setQuiescentAlpha(float alpha, boolean animate) {
        setDrawingAlpha(mQuiescentAlpha);
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
                if (isPressed()) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                }
                setPressed(false);
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
}
