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

    int mTouchSlop;
    boolean mIsRight;
    boolean mVertical;

    // primary NX guts
    private GestureDetector mNxGestureDetector;
    View.OnTouchListener mNxGestureListener;
    Handler mHandler = new Handler();
    KeyButtonInfo mLeftActions;
    KeyButtonInfo mRightActions;

    private boolean mHasSingleAction = true,
            mHasDoubleAction, mHasLongAction, mHasSwipeLeftAction,
            mHasSwipeRightAction, mHasSwipeRightShortAction, mHasSwipeLeftShortAction, mHasSwipeUpAction;
    private boolean mHasRightSingleAction = true,
            mHasRightDoubleAction, mHasRightLongAction, mHasRightSwipeLeftAction,
            mHasRightSwipeRightAction, mHasRightSwipeRightShortAction, mHasRightSwipeLeftShortAction, mHasRightSwipeUpAction;

    Runnable mSingleTapTimeout = new Runnable() {
        @Override
        public void run() {
            if (!mIsRight) {
                AwesomeAction.launchAction(mContext, mLeftActions.singleAction);
            } else {
                AwesomeAction.launchAction(mContext, mRightActions.singleAction);
            }
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

        mNxGestureDetector = new GestureDetector(context, new NxGestureDetector());
        this.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(final View view, final MotionEvent event) {
                mNxGestureDetector.onTouchEvent(event);
                return true;
            }
        });
    }

    @Override public void setPressed(boolean pressed) {
        super.setPressed(pressed);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (mNxGestureDetector != null) mNxGestureDetector.onTouchEvent(event);
        return true;
    }
    @Override public void setImage() { }
    @Override public void setImage(final Resources res) { }
    @Override public void setButtonActions(KeyButtonInfo actions) { }

    public void setLeftActions(KeyButtonInfo actions) {
        this.mLeftActions = actions;
        if (mLeftActions != null) {
            mHasSingleAction = mLeftActions.singleAction != null;
            mHasLongAction = mLeftActions.longPressAction != null;
            mHasDoubleAction = mLeftActions.doubleTapAction != null;
            mHasSwipeLeftAction = mLeftActions.swipeLeft != null;
            mHasSwipeRightAction = mLeftActions.swipeRight != null;
            mHasSwipeLeftShortAction = mLeftActions.swipeLeftShort != null;
            mHasSwipeRightShortAction = mLeftActions.swipeRightShort != null;
            mHasSwipeUpAction = mLeftActions.swipeUp != null;
        }
    }
    public void setRightActions(KeyButtonInfo actions) {
        this.mRightActions = actions;
        if (mRightActions != null) {
            mHasRightSingleAction = mRightActions.singleAction != null;
            mHasRightLongAction = mRightActions.longPressAction != null;
            mHasRightDoubleAction = mRightActions.doubleTapAction != null;
            mHasRightSwipeLeftAction = mRightActions.swipeLeft != null;
            mHasRightSwipeRightAction = mRightActions.swipeRight != null;
            mHasRightSwipeLeftShortAction = mRightActions.swipeLeftShort != null;
            mHasRightSwipeRightShortAction = mRightActions.swipeRightShort != null;
            mHasRightSwipeUpAction = mRightActions.swipeUp != null;
        }
    }

    public void setIsVertical(boolean isVertical) {
        mVertical = isVertical;
    }

    class NxGestureDetector extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_LONG_DISTANCE = 120;
        private static final int SWIPE_MIN_DISTANCE = 50;
        private static final int SWIPE_MAX_OFF_PATH = 200;
        private static final int SWIPE_THRESHOLD_VELOCITY = 250;
        private final int SINGLE_TAP_TIMEOUT = ViewConfiguration.getDoubleTapTimeout() - 150;

        @Override
        public boolean onDown(MotionEvent e) {
            Log.e(TAG, "onDown");
            float length = mVertical ? getHeight() : getWidth();
            float pos = mVertical ? e.getY() : e.getX();
            length /= 2;
            mIsRight = mVertical ? pos < length : pos > length;

            playSoundEffect(SoundEffectConstants.CLICK);
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            mHandler.removeCallbacks(mSingleTapTimeout);
            return false;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            Log.e(TAG, "onSingleTapUp");
            if (!mIsRight) {
                if (mHasSingleAction && !mHasDoubleAction) {
                    AwesomeAction.launchAction(mContext, mLeftActions.singleAction);
                    return true;
                }
            } else {
                if (mHasRightSingleAction && !mHasRightDoubleAction) {
                    AwesomeAction.launchAction(mContext, mRightActions.singleAction);
                    return true;
                }
            }
            if (mHasSingleAction || mHasRightSingleAction) {
                mHandler.postDelayed(mSingleTapTimeout, SINGLE_TAP_TIMEOUT);
            }
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            Log.e(TAG, "onDoubleTap");
            mHandler.removeCallbacks(mSingleTapTimeout);
            if (!mIsRight) {
                if (mHasDoubleAction) {
                    AwesomeAction.launchAction(mContext, mLeftActions.doubleTapAction);
                }
            } else {
                if (mHasRightDoubleAction) {
                    AwesomeAction.launchAction(mContext, mRightActions.doubleTapAction);
                }
            }
			return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            Log.e(TAG, "onLongPress");
            mHandler.removeCallbacks(mSingleTapTimeout);
            if (!mIsRight) {
                if (mHasLongAction) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    AwesomeAction.launchAction(mContext, mLeftActions.longPressAction);
                }
            } else {
                if (mHasRightLongAction) {
                    playSoundEffect(SoundEffectConstants.CLICK);
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    AwesomeAction.launchAction(mContext, mRightActions.longPressAction);
                }
            }
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {;
            mHandler.removeCallbacks(mSingleTapTimeout);
            Log.e(TAG, "onFling");

            /* X and Y need to be reversed for phone UI in landscape (mVertical = true) */
            float X1, X2, Y1, Y2;
            if (!mVertical) {
                X1 = e1.getX();
                X2 = e2.getX();
                Y1 = e1.getY();
                Y2 = e2.getY();
            } else {
                X1 = e2.getY();
                X2 = e1.getY();
                Y1 = e1.getX();
                Y2 = e2.getX();
            }

            if (Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                if (Math.abs(Y1 - Y2) > SWIPE_MIN_DISTANCE) {
                    // upwards swipe
                    if (!(X1 - X2 > SWIPE_MIN_DISTANCE)
                            && !(X2 - X1 > SWIPE_MIN_DISTANCE)) {
                        if (!mIsRight) {
                            if (mHasSwipeUpAction)
                                    AwesomeAction.launchAction(mContext, mLeftActions.swipeUp);
                        } else {
                            if (mHasRightSwipeUpAction)
                                    AwesomeAction.launchAction(mContext, mRightActions.swipeUp);
                        }
                        return true;
                    }
                } else {
                    // left long swipe
                    if (X1 - X2 > SWIPE_LONG_DISTANCE) {
                        if (!mIsRight) {
                            if (mHasSwipeLeftAction)
                                    AwesomeAction.launchAction(mContext, mLeftActions.swipeLeft);
                        } else {
                            if (mHasRightSwipeLeftAction)
                                    AwesomeAction.launchAction(mContext, mRightActions.swipeLeft);
                        }
                        return false;
                    // right long swipe
                    } else if (X2 - X1 > SWIPE_LONG_DISTANCE) {
                        if (!mIsRight) {
                            if (mHasSwipeRightAction)
                                    AwesomeAction.launchAction(mContext, mLeftActions.swipeRight);
                        } else {
                            if (mHasRightSwipeRightAction)
                                    AwesomeAction.launchAction(mContext, mRightActions.swipeRight);
                        }
                        return false;
                    }
                }
            
                // left short swipe
                if (X2 - X1 < SWIPE_LONG_DISTANCE) {
                    if (X1 - X2 > SWIPE_MIN_DISTANCE) {
                        if (!mIsRight) {
                            if (mHasSwipeLeftShortAction)
                                    AwesomeAction.launchAction(mContext, mLeftActions.swipeLeftShort);
                        } else {
                            if (mHasRightSwipeLeftShortAction)
                                    AwesomeAction.launchAction(mContext, mRightActions.swipeLeftShort);
                        }
                        return false;
                    // right short swipe
                    } else if (X2 - X1 > SWIPE_MIN_DISTANCE) {
                        if (!mIsRight) {
                            if (mHasSwipeRightShortAction)
                                    AwesomeAction.launchAction(mContext, mLeftActions.swipeRightShort);
                        } else {
                            if (mHasRightSwipeRightShortAction)
                                    AwesomeAction.launchAction(mContext, mRightActions.swipeRightShort);
                        }
                        return false;
                    }
                }
            }

            // handle single press events on a sloppy touch
            if ((X1 - X2 < SWIPE_MIN_DISTANCE)
                        || (X2 - X1 < SWIPE_MIN_DISTANCE)) {
                if (!mIsRight) {
                    if (mHasSingleAction) {
                        AwesomeAction.launchAction(mContext, mLeftActions.singleAction);
                    }
                } else {
                    if (mHasRightSingleAction) {
                        AwesomeAction.launchAction(mContext, mRightActions.singleAction);
                    }
                }
                return false;
            }
            return false;
        }
    }
}
