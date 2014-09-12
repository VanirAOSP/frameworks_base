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

public class NxButtonView extends ImageView {
    private static final String TAG = "StatusBar.NxButtonView";
    private static final boolean DEBUG = true;

    final float GLOW_MAX_SCALE_FACTOR = 1.8f;
    public static final float DEFAULT_QUIESCENT_ALPHA = 0.70f;
    public static final int SLOPPY_LONGPRESS_TIMEOUT = 500;
    public static final String NULL_ACTION = AwesomeConstant.ACTION_NULL.value();
    public static final String BLANK_ACTION = AwesomeConstant.ACTION_BLANK.value();

    long mDownTime;
    long mFlingTime;
    int mTouchSlop;
    int mGlowBgId;
    Drawable mGlowBG;
    int mGlowWidth, mGlowHeight;
    int mNxButtonWidth;
    float mGlowAlpha = 0f, mGlowScale = 1f;
    float mDrawingAlpha = 1f;
    float mQuiescentAlpha = DEFAULT_QUIESCENT_ALPHA;
    RectF mRect = new RectF();
    AnimatorSet mPressedAnim;
    Animator mAnimateToQuiescent = new ObjectAnimator();
    AnimatorSet as = mPressedAnim = new AnimatorSet();

    // NX
    private GestureDetector mNxGestureDetector;
    View.OnTouchListener mNxGestureListener;
    Handler mHandler = new Handler();
    boolean isDoubleTapPending;
    boolean wasConsumed;
    NxButtonInfo mActions;

    private boolean mHasSingleAction = true,
            mHasDoubleAction, mHasLongAction, mHasSwipeLeftAction,
            mHasSwipeRightAction, mHasSwipeRightShortAction, mHasSwipeLeftShortAction;

    Runnable mDoubleTapTimeout = new Runnable() {
        @Override
        public void run() {
            wasConsumed = false;
            isDoubleTapPending = false;
            playSoundEffect(SoundEffectConstants.CLICK);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            AwesomeAction.launchAction(mContext, mActions.doubleTapAction);
        }
    };
    Runnable mSloppyLongPress = new Runnable() {
        @Override
        public void run() {
            MotionEvent motionEvent = MotionEvent.obtain(
                    SystemClock.uptimeMillis(), SystemClock.uptimeMillis() + 100, MotionEvent.ACTION_CANCEL, 0.0f, 0.0f, 0);

            playSoundEffect(SoundEffectConstants.CLICK);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            AwesomeAction.launchAction(mContext, mActions.longPressAction);
            // Dispatch artificial touch event to cancel press after sloppy longpress action fires
            dispatchTouchEvent(motionEvent);
        }
    };

    public NxButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NxButtonView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        setDrawingAlpha(mQuiescentAlpha);
        if (mGlowBG != null) {
            mGlowWidth = mGlowBG.getIntrinsicWidth();
            mGlowHeight = mGlowBG.getIntrinsicHeight();
        }

        // mNxButtonWidth = res.getDimensionPixelSize(R.dimen.navigation_nx_key_width);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

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

    public void setPressed(boolean pressed) {
        if (mGlowBG != null) {
            if (pressed != isPressed()) {
                if (mPressedAnim != null && mPressedAnim.isRunning()) {
                    mPressedAnim.cancel();
                }
                final AnimatorSet as = mPressedAnim = new AnimatorSet();
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

    public void updateResources(Resources res) {
        if (mGlowBgId != 0) {
            mGlowBG = res.getDrawable(mGlowBgId);
        }
    }

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
        if (DEBUG) Log.d(TAG, "New quiescent alpha = " + mQuiescentAlpha);
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
            invalidate();
        }
    }

    public void setGlowBackground(int resId) {
        mGlowBgId = resId;
        mGlowBG = getResources().getDrawable(resId);
        if (mGlowBG != null) {
            setDrawingAlpha(mDrawingAlpha);
            mGlowWidth = mGlowBG.getIntrinsicWidth();
            mGlowHeight = mGlowBG.getIntrinsicHeight();
        }
    }

    class NxGestureDetector extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_LONG_DISTANCE = 100;
        private static final int SWIPE_MIN_DISTANCE = 30;
        private static final int SWIPE_TOUCH_SLOP = 75;
        private static final int SWIPE_MAX_OFF_PATH = 250;
        private static final int SWIPE_THRESHOLD_VELOCITY = 250;
        private final int DT_TIMEOUT = ViewConfiguration.getDoubleTapTimeout() - 100;

        @Override
        public boolean onDown(MotionEvent e) {
            if (DEBUG) Log.e(TAG, "NX GESTURE DETECTED in onDown: ");
            if (isDoubleTapPending) {
                isDoubleTapPending = false;
                wasConsumed = true;
                mHandler.removeCallbacks(mDoubleTapTimeout);
                playSoundEffect(SoundEffectConstants.CLICK);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                AwesomeAction.launchAction(mContext, mActions.doubleTapAction);
                return true;
            }
            return false;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (DEBUG) Log.e(TAG, "NX GESTURE DETECTED in onSingleTapUp: ");
            mHandler.removeCallbacks(mSloppyLongPress);
            if (mHasDoubleAction) {
                if (wasConsumed) {
                    wasConsumed = false;
                    return true;
                }
                isDoubleTapPending = true;
                mHandler.postDelayed(mDoubleTapTimeout, DT_TIMEOUT);
            } else {
                if (mHasSingleAction) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    AwesomeAction.launchAction(mContext, mActions.singleAction);
                }
            }
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (DEBUG) Log.e(TAG, "NX GESTURE DETECTED in onLongPress: ");
            mHandler.removeCallbacks(mSloppyLongPress);
            if (mHasLongAction) {
                playSoundEffect(SoundEffectConstants.CLICK);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                AwesomeAction.launchAction(mContext, mActions.longPressAction);
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (DEBUG) Log.e(TAG, "NX GESTURE DETECTED in onFling: scaled touch slop ");
            mFlingTime = SystemClock.uptimeMillis();
            long diff = mFlingTime - mDownTime;
            mHandler.removeCallbacks(mSloppyLongPress);
            if (DEBUG) Log.e(TAG, "mFlingTime - mDownTime = diff" + mFlingTime +" "+ mDownTime +" "+ diff);
            if (DEBUG) Log.e(TAG, "e1.getX() - e2.getX(): left " + (e1.getX() - e2.getX()));
            if (DEBUG) Log.e(TAG, "e2.getX() - e1.getX(): right " + (e2.getX() - e1.getX()));
            if (DEBUG) Log.e(TAG, "Math.abs(velocityX): positive value of velocity " + Math.abs(velocityX));

            if (!mHasSwipeLeftAction || !mHasSwipeRightAction) return false;

            // our swipe has gone arwy
            if (Math.abs(e1.getY() - e2.getY()) > SWIPE_MAX_OFF_PATH) {
                return false;
            }
            if (Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                // left long swipe
                if (e1.getX() - e2.getX() > SWIPE_LONG_DISTANCE) {
                    if (DEBUG) Log.e(TAG, "NX GESTURE DETECTED: LONG LEFT SWIPE FROM FLING");
                    playSoundEffect(SoundEffectConstants.CLICK);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    AwesomeAction.launchAction(mContext, mActions.swipeLeft);
                    return false;
                // right long swipe
                } else if (e2.getX() - e1.getX() > SWIPE_LONG_DISTANCE) {
                    if (DEBUG) Log.e(TAG, "NX GESTURE DETECTED: LONG RIGHT SWIPE FROM FLING");
                    playSoundEffect(SoundEffectConstants.CLICK);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    AwesomeAction.launchAction(mContext, mActions.swipeRight);
                    return false;
                }
            
                // left short swipe
                if (e2.getX() - e1.getX() < SWIPE_LONG_DISTANCE) {
                    if (e1.getX() - e2.getX() > SWIPE_MIN_DISTANCE) {
                        if (DEBUG) Log.e(TAG, "NX GESTURE DETECTED: SHORT LEFT SWIPE FROM FLING");
                        playSoundEffect(SoundEffectConstants.CLICK);
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        AwesomeAction.launchAction(mContext, mActions.swipeLeftShort);
                        return false;
                    // right short swipe
                    } else if (e2.getX() - e1.getX() > SWIPE_MIN_DISTANCE) {
                        if (DEBUG) Log.e(TAG, "NX GESTURE DETECTED: SHORT RIGHT SWIPE FROM FLING");
                        playSoundEffect(SoundEffectConstants.CLICK);
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        AwesomeAction.launchAction(mContext, mActions.swipeRightShort);
                        return false;
                    }
                }
            }
            // handle single press events on a sloppy touch
            if ((e1.getX() - e2.getX() < SWIPE_MIN_DISTANCE)
                        || (e2.getX() - e1.getX() < SWIPE_MIN_DISTANCE)) {
                if (mHasSingleAction) {
                    if (DEBUG) Log.e(TAG, "NX GESTURE DETECTED: SINGLE PRESS FROM FLING");
                    playSoundEffect(SoundEffectConstants.CLICK);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    AwesomeAction.launchAction(mContext, mActions.singleAction);
                }
                return false;
            }
            return false;
        }
    }

    public static class NxButtonInfo {
        String singleAction, doubleTapAction, longPressAction, swipeLeft, swipeRight, swipeLeftShort, swipeRightShort;

        public NxButtonInfo(String singleTap, String doubleTap,
                String longPress, String swipeLeft, String swipeRight, String swipeLeftShort, String swipeRightShort) {
            this.singleAction = singleTap;
            this.doubleTapAction = doubleTap;
            this.longPressAction = longPress;
            this.swipeLeft = swipeLeft;
            this.swipeRight = swipeRight;
            this.swipeLeftShort = swipeLeftShort;
            this.swipeRightShort = swipeRightShort;

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
        }
    }
}
