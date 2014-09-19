/*
 * Copyright (C) 2008 The Android Open Source Project
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
import android.graphics.Canvas;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewDebug;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageView;

import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.util.aokp.AwesomeAction;
import com.android.internal.util.vanir.AwesomeConstants.AwesomeConstant;
import com.android.internal.util.aokp.NavBarHelpers;
import com.android.systemui.R;

import java.io.File;

public class KeyButtonView extends ImageView {
    private static final String TAG = "StatusBar.KeyButtonView";
    private static final boolean DEBUG = false;

    final float GLOW_MAX_SCALE_FACTOR = 1.8f;
    public static final float DEFAULT_QUIESCENT_ALPHA = 0.70f;
    private static final int DPAD_TIMEOUT_INTERVAL = 500;
    private static final int DPAD_REPEAT_INTERVAL = 75;

    private final int mLongPressTimeout;

    public static final String NULL_ACTION = AwesomeConstant.ACTION_NULL.value();
    public static final String BLANK_ACTION = AwesomeConstant.ACTION_BLANK.value();
    public static final String RECENTS_ACTION = AwesomeConstant.ACTION_RECENTS.value();
    public static final String ARROW_UP = AwesomeConstant.ACTION_ARROW_UP.value();
    public static final String ARROW_LEFT = AwesomeConstant.ACTION_ARROW_LEFT.value();
    public static final String ARROW_RIGHT = AwesomeConstant.ACTION_ARROW_RIGHT.value();
    public static final String ARROW_DOWN = AwesomeConstant.ACTION_ARROW_DOWN.value();

    long mDownTime;
    long mUpTime;
    int mTouchSlop;
    int mGlowBgId;
    Drawable mGlowBG;
    int mGlowWidth, mGlowHeight;
    float mGlowAlpha = 0f, mGlowScale = 1f;
    @ViewDebug.ExportedProperty(category = "drawing")
    float mDrawingAlpha = 1f;
    @ViewDebug.ExportedProperty(category = "drawing")
    float mQuiescentAlpha = DEFAULT_QUIESCENT_ALPHA;
    RectF mRect = new RectF();
    AnimatorSet mPressedAnim;
    Animator mAnimateToQuiescent = new ObjectAnimator();
    boolean mShouldClick = true;

    AwesomeButtonInfo mActions;

    protected static IStatusBarService mBarService;
    public static synchronized void getStatusBarInstance() {
        if (mBarService == null) {
            mBarService = IStatusBarService.Stub.asInterface(
                    ServiceManager.getService(Context.STATUS_BAR_SERVICE));
        }
    }

    private boolean mHasSingleAction = true, mHasDoubleAction, mHasLongAction;
    private boolean mIsRecentsAction = false, mIsRecentsSingleAction, mIsRecentsLongAction, mIsRecentsDoubleTapAction;
    private boolean mIsDPadAction;
    public boolean mHasBlankSingleAction = false;
    volatile boolean mRecentsPreloaded;

    Runnable mCheckLongPress = new Runnable() {
        public void run() {
            if (isPressed()) {
                removeCallbacks(mSingleTap);
                doLongPress();
            }
        }
    };
    private Runnable mSingleTap = new Runnable() {
        @Override
        public void run() {
            if (!isPressed()) {
                doSinglePress();
            }
        }
    };

    public KeyButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyButtonView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs);

        setDrawingAlpha(mQuiescentAlpha);
        if (mGlowBG != null) {
            mGlowWidth = mGlowBG.getIntrinsicWidth();
            mGlowHeight = mGlowBG.getIntrinsicHeight();
        }

        setClickable(true);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
        setLongClickable(false);
    }

    public void updateResources(Resources res) {
        if (mGlowBgId != 0) {
            mGlowBG = res.getDrawable(mGlowBgId);
        }
        setImage(res);
    }

    public void setButtonActions(AwesomeButtonInfo actions) {
        this.mActions = actions;

        setTag(mActions.singleAction); // should be OK even if it's null

        setImage();

        mHasSingleAction = mActions != null && (mActions.singleAction != null);
        mHasLongAction = mActions != null && mActions.longPressAction != null;
        mHasDoubleAction = mActions != null && mActions.doubleTapAction != null;
        mHasBlankSingleAction = mHasSingleAction && mActions.singleAction.equals(BLANK_ACTION);

        // TO DO: determine type of key prior to getting a keybuttonview instance to allow more specialized
        // and efficiently coded keybuttonview classes.
        mIsDPadAction = mHasSingleAction
                && (mActions.singleAction.equals(ARROW_LEFT)
                || mActions.singleAction.equals(ARROW_UP)
                || mActions.singleAction.equals(ARROW_DOWN)
                || mActions.singleAction.equals(ARROW_RIGHT));

        mIsRecentsSingleAction = (mHasSingleAction && mActions.singleAction.equals(RECENTS_ACTION));
        mIsRecentsLongAction = (mHasLongAction && mActions.longPressAction.equals(RECENTS_ACTION));
        mIsRecentsDoubleTapAction = (mHasDoubleAction && mActions.doubleTapAction.equals(RECENTS_ACTION));

        if (mIsRecentsSingleAction || mIsRecentsLongAction || mIsRecentsDoubleTapAction) {
            mIsRecentsAction = true;
            getStatusBarInstance();
        }

        setLongClickable(mHasLongAction);
        Log.e(TAG, "Adding a navbar button in landscape or portrait");
    }

    /* @hide */
    public void setImage() {
        setImage(getResources());
    }

    /* @hide */
    public void setImage(final Resources res) {
        // set image
        if (mActions.iconUri != null && mActions.iconUri.length() > 0) {
            // custom icon from the URI here
            File f = new File(Uri.parse(mActions.iconUri).getPath());
            if (f.exists()) {
                setImageDrawable(new BitmapDrawable(res, f.getAbsolutePath()));
            }
        } else if (mActions.singleAction != null) {
            setImageDrawable(NavBarHelpers.getIconImage(mContext, mActions.singleAction));
        } else {
            setImageResource(R.drawable.ic_sysbar_null);
        }
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
        // Calling setAlpha(int), which is an ImageView-specific
        // method that's different from setAlpha(float). This sets
        // the alpha on this ImageView's drawable directly
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

            // also invalidate our immediate parent to help avoid situations where nearby glows
            // interfere
            if (getParent() != null) ((View) getParent()).invalidate();
        }
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

    public boolean onTouchEvent(MotionEvent ev) {
        if (mHasBlankSingleAction) return true;

        final int action = ev.getAction();
        int x, y;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (mIsRecentsAction && mRecentsPreloaded == false) preloadRecentApps();
                mDownTime = SystemClock.uptimeMillis();
                setPressed(true);
                if (mHasSingleAction) {
                    removeCallbacks(mSingleTap);
                    if (mIsDPadAction) {
                        mShouldClick = true;
                        removeCallbacks(mDPadKeyRepeater);
                    }
                }
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                long diff = mDownTime - mUpTime; // difference between last up and now
                if (mHasDoubleAction && diff <= 200) {
                    doDoubleTap();
                } else {
                    if (mIsDPadAction) {
                        postDelayed(mDPadKeyRepeater, DPAD_TIMEOUT_INTERVAL);
                    } else {
                        if (mHasLongAction) {
                            removeCallbacks(mCheckLongPress);
                            postDelayed(mCheckLongPress, mLongPressTimeout);
                        }
                        if (mHasSingleAction) {
                            postDelayed(mSingleTap, 200);
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                x = (int) ev.getX();
                y = (int) ev.getY();
                setPressed(x >= -mTouchSlop
                        && x < getWidth() + mTouchSlop
                        && y >= -mTouchSlop
                        && y < getHeight() + mTouchSlop);
                break;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                if (mIsDPadAction) {
                    mShouldClick = true;
                    removeCallbacks(mDPadKeyRepeater);
                }
                if (mHasSingleAction) {
                    removeCallbacks(mSingleTap);
                }
                if (mHasLongAction) {
                    removeCallbacks(mCheckLongPress);
                }
                if (mRecentsPreloaded == true) cancelPreloadRecentApps();
                break;
            case MotionEvent.ACTION_UP:
                mUpTime = SystemClock.uptimeMillis();
                boolean playSound;

                if (mIsDPadAction) {
                    playSound = mShouldClick;
                    mShouldClick = true;
                    removeCallbacks(mDPadKeyRepeater);
                } else {
                    if (mHasLongAction) {
                        removeCallbacks(mCheckLongPress);
                    }
                    playSound = isPressed();
                }
                setPressed(false);

                if (playSound) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                }

                if (!mHasDoubleAction && !mHasLongAction) {
                    removeCallbacks(mSingleTap);
                    doSinglePress();
                }
                break;
        }
        return true;
    }

    private void doSinglePress() {
        if (callOnClick()) {
            // cool
            sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
        } else if (mIsRecentsSingleAction) {
            try {
                mBarService.toggleRecentApps();
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
                mRecentsPreloaded = false;
            } catch (RemoteException e) {
                Log.e(TAG, "RECENTS ACTION FAILED");
            }
            return;
        }

        if (mActions != null) {
            if (mActions.singleAction != null) {
                AwesomeAction.launchAction(mContext, mActions.singleAction);
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_CLICKED);
            }
        }
    }

    private void doDoubleTap() {
        if (mHasDoubleAction) {
            removeCallbacks(mSingleTap);
            if (mIsRecentsDoubleTapAction) {
                try {
                    mBarService.toggleRecentApps();
                    mRecentsPreloaded = false;
                } catch (RemoteException e) {
                    Log.e(TAG, "RECENTS ACTION FAILED");
                }
            } else {
                AwesomeAction.launchAction(mContext, mActions.doubleTapAction);
            }
        }
    }

    private void doLongPress() {
        if (mHasLongAction) {
            removeCallbacks(mSingleTap);
            if (mIsRecentsLongAction) {
                try {
                    mBarService.toggleRecentApps();
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
                    mRecentsPreloaded = false;
                } catch (RemoteException e) {
                    Log.e(TAG, "RECENTS ACTION FAILED");
                }
            } else {
                AwesomeAction.launchAction(mContext, mActions.longPressAction);
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED);
            }
        }
    }

    private void cancelPreloadRecentApps() {
        if (mRecentsPreloaded == false) return;
        try {
            mBarService.cancelPreloadRecentApps();
        } catch (RemoteException e) {
            // use previous state
            return;
        }
        mRecentsPreloaded = false;
    }

    private void preloadRecentApps() {
        try {
            mBarService.preloadRecentApps();
        } catch (RemoteException e) {
            mRecentsPreloaded = false;
            return;
        }
        mRecentsPreloaded = true;
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

    private Runnable mDPadKeyRepeater = new Runnable() {
        @Override
        public void run() {
            if (mActions != null) {
                if (mActions.singleAction != null) {
                    AwesomeAction.launchAction(mContext, mActions.singleAction);
                    // click on the first event since we're handling in MotionEvent.ACTION_DOWN
                    if (mShouldClick) {
                        mShouldClick = false;
                        playSoundEffect(SoundEffectConstants.CLICK);
                    }
                }
            }
            // repeat action
            postDelayed(this, DPAD_REPEAT_INTERVAL);
        }
    };

    public static class AwesomeButtonInfo {
        String singleAction, doubleTapAction, longPressAction, iconUri;

        public AwesomeButtonInfo(String singleTap, String doubleTap, String longPress, String uri) {
            this.singleAction = singleTap;
            this.doubleTapAction = doubleTap;
            this.longPressAction = longPress;
            this.iconUri = uri;
            
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
        }
    }
}
