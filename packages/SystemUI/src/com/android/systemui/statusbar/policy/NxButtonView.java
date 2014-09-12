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
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.widget.ImageView;

import com.android.internal.util.aokp.AwesomeAction;
import com.android.internal.util.vanir.AwesomeConstants.AwesomeConstant;
import com.android.internal.util.aokp.NavBarHelpers;
import com.android.systemui.R;

public class NxButtonView extends KeyButtonView {
    private static final String TAG = "StatusBar.NxButtonView";
    private static final boolean DEBUG = true;

    public static final int SLOPPY_LONGPRESS_TIMEOUT = 500;
    public static final String NULL_ACTION = AwesomeConstant.ACTION_NULL.value();
    public static final String BLANK_ACTION = AwesomeConstant.ACTION_BLANK.value();

    long mDownTime;
    long mFlingTime;
    int mTouchSlop;

    // NX
    private GestureDetector mNxGestureDetector;
    View.OnTouchListener mNxGestureListener;
    Handler mHandler = new Handler();
    NxButtonInfo mActions;

    private boolean mHasSingleAction = true,
            mHasDoubleAction, mHasLongAction, mHasSwipeLeftAction,
            mHasSwipeRightAction, mHasSwipeRightShortAction, mHasSwipeLeftShortAction, mHasSwipeUpAction;

    Runnable mSingleTapTimeout = new Runnable() {
        @Override
        public void run() {
            AwesomeAction.launchAction(mContext, mActions.singleAction);
        }
    };
    Runnable mSloppyLongPress = new Runnable() {
        @Override
        public void run() {
            mHandler.removeCallbacks(mSingleTapTimeout);
            playSoundEffect(SoundEffectConstants.CLICK);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            AwesomeAction.launchAction(mContext, mActions.longPressAction);
            // Dispatch artificial touch event to cancel press after sloppy longpress action fires
            MotionEvent motionEvent = MotionEvent.obtain(
                    SystemClock.uptimeMillis(), SystemClock.uptimeMillis() + 250, MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);
            dispatchTouchEvent(motionEvent);
            motionEvent.recycle();
        }
    };

    public NxButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NxButtonView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        // mNxButtonWidth = res.getDimensionPixelSize(R.dimen.navigation_nx_key_width);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);
        setLongClickable(false);

        // C4N'7 70UCH 7H15 list3n3r!!!1!1!
        mNxGestureDetector = new GestureDetector(context, new NxGestureDetector());
        this.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(final View view, final MotionEvent event) {
                mNxGestureDetector.onTouchEvent(event);
                final int action = event.getAction();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        setPressed(true);
                        mDownTime = SystemClock.uptimeMillis();
                        mHandler.removeCallbacks(mSloppyLongPress);
                        mHandler.postDelayed(mSloppyLongPress, SLOPPY_LONGPRESS_TIMEOUT);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int x = (int) event.getX();
                        int y = (int) event.getY();
                        setPressed(x >= -mTouchSlop
                                && x < getWidth() + mTouchSlop
                                && y >= -mTouchSlop
                                && y < getHeight() + mTouchSlop);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        setPressed(false);
                        mHandler.removeCallbacks(mSloppyLongPress);
                        break;
                    case MotionEvent.ACTION_UP:
                        setPressed(false);
                        mHandler.removeCallbacks(mSloppyLongPress);
                        break;
                }
                return true;
            }
        });
    }

    @Override public void setImage() { }
    @Override public void setImage(final Resources res) { }

    public void setNxActions(NxButtonInfo actions) {
        this.mActions = actions;
        setTag(mActions.singleAction);
        mHasSingleAction = mActions != null && (mActions.singleAction != null);
        mHasLongAction = mActions != null && mActions.longPressAction != null;
        mHasDoubleAction = mActions != null && mActions.doubleTapAction != null;
        mHasSwipeLeftAction = mActions != null && mActions.swipeLeft != null;
        mHasSwipeRightAction = mActions != null && mActions.swipeRight != null;
        mHasSwipeLeftShortAction = mActions != null && mActions.swipeLeftShort != null;
        mHasSwipeRightShortAction = mActions != null && mActions.swipeRightShort != null;
        mHasSwipeUpAction = mActions != null && mActions.swipeUp != null;
    }

    class NxGestureDetector extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_LONG_DISTANCE = 100;
        private static final int SWIPE_MIN_DISTANCE = 45;
        private static final int SWIPE_MAX_OFF_PATH = 200;
        private static final int SWIPE_THRESHOLD_VELOCITY = 250;
        private final int SINGLE_TAP_TIMEOUT = ViewConfiguration.getDoubleTapTimeout() - 150;

        @Override
        public boolean onDown(MotionEvent e) {
            if (DEBUG) Log.e(TAG, "NX GESTURE DETECTED: onDown");
            playSoundEffect(SoundEffectConstants.CLICK);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            mHandler.removeCallbacks(mSingleTapTimeout);
            return false;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (DEBUG) Log.e(TAG, "NX GESTURE DETECTED: onSingleTapUp");
            mHandler.removeCallbacks(mSloppyLongPress);
            if (mHasSingleAction && !mHasDoubleAction) {
                AwesomeAction.launchAction(mContext, mActions.singleAction);
            } else {
                if (mHasSingleAction) {
                    mHandler.postDelayed(mSingleTapTimeout, SINGLE_TAP_TIMEOUT);
                }
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (DEBUG) Log.e(TAG, "NX GESTURE DETECTED: DOUBLE TAP");
            mHandler.removeCallbacks(mSingleTapTimeout);
			if (mHasDoubleAction) {
                AwesomeAction.launchAction(mContext, mActions.doubleTapAction);
			}
			return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            mHandler.removeCallbacks(mSloppyLongPress);
            mHandler.removeCallbacks(mSingleTapTimeout);
            if (mHasLongAction) {
                playSoundEffect(SoundEffectConstants.CLICK);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                AwesomeAction.launchAction(mContext, mActions.longPressAction);
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            mFlingTime = SystemClock.uptimeMillis();
            long diff = mFlingTime - mDownTime;
            mHandler.removeCallbacks(mSloppyLongPress);
            mHandler.removeCallbacks(mSingleTapTimeout);

            if (!mHasSwipeLeftAction && !mHasSwipeRightAction && !mHasSwipeLeftShortAction
                    && !mHasSwipeRightShortAction && !mHasSwipeUpAction) return false;

            // our swipe has gone arwy or was too long
            if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH) {
                if (DEBUG) Log.e(TAG, "NX GESTURE DETECTED: SWIPE OFF PATH");
                return false;
            }

            if (Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                if (DEBUG) Log.e(TAG, "NX GESTURE DETECTED: UPWARDS SWIPE");
                // upwards swipe
                if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MIN_DISTANCE) {
                    AwesomeAction.launchAction(mContext, mActions.swipeUp);
                    return false;
                }
                // left long swipe
                if (e1.getX() - e2.getX() > SWIPE_LONG_DISTANCE) {
                    AwesomeAction.launchAction(mContext, mActions.swipeLeft);
                    return false;
                // right long swipe
                } else if (e2.getX() - e1.getX() > SWIPE_LONG_DISTANCE) {
                    AwesomeAction.launchAction(mContext, mActions.swipeRight);
                    return false;
                }
            
                // left short swipe
                if (e2.getX() - e1.getX() < SWIPE_LONG_DISTANCE) {
                    if (e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE) {
                        AwesomeAction.launchAction(mContext, mActions.swipeLeftShort);
                        return false;
                    // right short swipe
                    } else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE) {
                        AwesomeAction.launchAction(mContext, mActions.swipeRightShort);
                        return false;
                    }
                }
            }
            // handle single press events on a sloppy touch
            if ((e1.getX() - e2.getX() < SWIPE_MIN_DISTANCE)
                        || (e2.getX() - e1.getX() < SWIPE_MIN_DISTANCE)) {
                if (mHasSingleAction) {
                    AwesomeAction.launchAction(mContext, mActions.singleAction);
                }
                return false;
            }
            return false;
        }
    }

    public static class NxButtonInfo {
        String singleAction, doubleTapAction, longPressAction, swipeLeft, swipeRight, swipeLeftShort, swipeRightShort, swipeUp;

        public NxButtonInfo(String singleTap, String doubleTap,
                String longPress, String swipeLeft, String swipeRight, String swipeLeftShort, String swipeRightShort, String swipeUp) {
            this.singleAction = singleTap;
            this.doubleTapAction = doubleTap;
            this.longPressAction = longPress;
            this.swipeLeft = swipeLeft;
            this.swipeRight = swipeRight;
            this.swipeLeftShort = swipeLeftShort;
            this.swipeRightShort = swipeRightShort;
            this.swipeUp = swipeUp;

            if (singleAction != null) {
                if ((singleAction.isEmpty()
                        || singleAction.equals(NULL_ACTION))) {
                    singleAction = null;
                }
            }

            if (doubleTapAction != null) {
                if ((doubleTapAction.isEmpty()
                        || doubleTapAction.equals(NULL_ACTION))) {
                    doubleTapAction = null;
                }
            }

            if (longPressAction != null) {
                if ((longPressAction.isEmpty()
                        || longPressAction.equals(NULL_ACTION))) {
                    longPressAction = null;
                }
            }

            if (swipeLeft != null) {
                if ((swipeLeft.isEmpty()
                        || swipeLeft.equals(NULL_ACTION))) {
                    swipeLeft = null;
                }
            }

            if (swipeRight != null) {
                if ((swipeRight.isEmpty()
                        || swipeRight.equals(NULL_ACTION))) {
                    swipeRight = null;
                }
            }
            if (swipeLeftShort != null) {
                if ((swipeLeftShort.isEmpty()
                        || swipeLeftShort.equals(NULL_ACTION))) {
                    swipeLeftShort = null;
                }
            }

            if (swipeRightShort != null) {
                if ((swipeRightShort.isEmpty()
                        || swipeRightShort.equals(NULL_ACTION))) {
                    swipeRightShort = null;
                }
            }

            if (swipeUp != null) {
                if ((swipeUp.isEmpty()
                        || swipeUp.equals(NULL_ACTION))) {
                    swipeUp = null;
                }
            }
        }
    }
}
