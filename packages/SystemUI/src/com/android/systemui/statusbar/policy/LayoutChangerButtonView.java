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

    private static final float ALPHA_SCALE = 0.5f; //the default alpha is 0.7... this view was intended to have 0.3 alpha. 0.3 ~= 0.35 == 0.5 * 0.7

    public LayoutChangerButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public LayoutChangerButtonView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mGlowBgId = 0;
    }

    @Override
    public void setButtonActions(KeyButtonInfo actions) {
        this.mActions = actions;
        setTag(mActions.singleAction);
        setImage();
    }

    public void setMenuAction(boolean show, int isVertical, boolean isTablet) {
        if (show) {
            mActions.singleAction = ACTION_MENU;
            setImageResource(R.drawable.ic_sysbar_menu);
        } else {
            mActions.singleAction = LAYOUT_RIGHT;
            if (isTablet) {
                setImageResource(R.drawable.ic_sysbar_layout_right);
            } else {
                setImageResource(isVertical != 1
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

    @Override
    public float getQuiescentAlphaScale() {
        return ALPHA_SCALE;
    }

    @Override
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

    @Override
    protected void doSinglePress() {
        if (callOnClick()) {
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
        }
        AwesomeAction.launchAction(mContext, mActions.singleAction);
    }
}
