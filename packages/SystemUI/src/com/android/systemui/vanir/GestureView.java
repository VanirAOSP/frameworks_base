/*
 * Copyright (C) 2014 VanirAOSP && the Android Open Source Project
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

package com.android.systemui.vanir;

import java.io.File;
import java.net.URISyntaxException;
import java.util.List;

import android.app.StatusBarManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.gesture.Gesture;
import android.gesture.GestureLibraries;
import android.gesture.GestureLibrary;
import android.gesture.GestureOverlayView;
import android.gesture.Prediction;
import android.os.Handler;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import com.android.systemui.R;
import com.android.systemui.vanir.TriggerOverlayView;
import com.android.systemui.statusbar.BaseStatusBar;

import static android.view.KeyEvent.ACTION_DOWN;
import static android.view.KeyEvent.KEYCODE_BACK;
import static android.view.KeyEvent.KEYCODE_HOME;

public class GestureView extends TriggerOverlayView implements GestureOverlayView.OnGestureListener {
    private static final String TAG = "GestureView";
    public static final String TOGGLE_GESTURE_ACTIONS = "vanir.TOGGLE_GESTURE_PANEL";
    public static final String UPDATE_GESTURE_ACTIONS = "vanir.UPDATE_GESTURE_ACTIONS";

    private final File mStoreFile = new File("/data/system", "ga_gestures");

    State mState = State.Collapsed;
    private View mContent;
    private GestureLibrary mStore;
    private long mGestureLoadedTime = 0;
    private TranslateAnimation mSlideIn;
    private TranslateAnimation mSlideOut;

    private BaseStatusBar mBar;
    private GestureOverlayView mGestureView;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (Intent.ACTION_SCREEN_OFF.equals(action) && mState != State.Collapsed) {
                switchToState(State.Closing);
            }

            if ((TOGGLE_GESTURE_ACTIONS).equals(action)) {
                if (isKeyguardEnabled()) return;

                if (mState == State.Collapsed) {
                    switchToState(State.Opening);
                } else if (mState == State.Expanded) {
                    switchToState(State.Closing);
                }

            } else if ((UPDATE_GESTURE_ACTIONS).equals(action)) {
                reloadGestures();
            }
        }
    };

    public GestureView(Context context) {
        this(context, null);
    }

    public GestureView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GestureView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        mStore = GestureLibraries.fromFile(mStoreFile);
    }

    public void reloadGestures() {
        if (mStore != null) {
            mStore.load();
        }
    }

    OnClickListener mCancelButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mState != State.Collapsed) {
                switchToState(State.Closing);
            }
        }
    };

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContent = findViewById(R.id.content);
        mGestureView = (GestureOverlayView) findViewById(R.id.gesture_overlay);
        mGestureView.setGestureVisible(true);
        mGestureView.addOnGestureListener(this);
        findViewById(R.id.cancel_gesturing).setOnClickListener(mCancelButtonListener);
        createAnimations();
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(TOGGLE_GESTURE_ACTIONS);
        filter.addAction(UPDATE_GESTURE_ACTIONS);
        mContext.registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mContext.unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mState == State.Expanded) {
            switchToState(State.Collapsed);
        }
    }

    @Override
    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        if (event.getAction() == ACTION_DOWN) {
            if ((event.getKeyCode() == KEYCODE_BACK) && mState != State.Collapsed) {
                switchToState(State.Closing);
            }
            return true;
        }
        return super.dispatchKeyEventPreIme(event);
    }

    public void setStatusBar(BaseStatusBar bar) {
        mBar = bar;
    }

    private void switchToState(State state) {
        switch (state) {
            case Collapsed:
                mGestureView.clear(false);
                mContent.setVisibility(View.GONE);
                mGestureView.setVisibility(View.GONE);
                break;
            case Expanded:
                mGestureView.setVisibility(View.VISIBLE);
                break;
            case Gesturing:
                break;
            case Opening:
                mContent.setVisibility(View.VISIBLE);
                mContent.startAnimation(mSlideIn);
                break;
            case Closing:
                mContent.startAnimation(mSlideOut);
                break;
        }
        mState = state;
    }

    private boolean launchShortcut(String uri) {
        try {
            Intent intent = Intent.parseUri(uri, 0);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            return true;
        } catch (URISyntaxException e) {
            Log.e(TAG, "URISyntaxException: [" + uri + "]");
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "ActivityNotFound: [" + uri + "]");
        }
        return false;
    }

    private void createAnimations() {
        mSlideIn = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 1.0f, Animation.RELATIVE_TO_PARENT, 0.0f);

        mSlideOut = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 0.0f,
                Animation.RELATIVE_TO_PARENT, 0.0f, Animation.RELATIVE_TO_PARENT, 1.0f);
        mSlideIn.setDuration(100);
        mSlideIn.setInterpolator(new DecelerateInterpolator());
        mSlideIn.setFillAfter(true);
        mSlideIn.setAnimationListener(mAnimListener);
        mSlideOut.setDuration(175);
        mSlideOut.setInterpolator(new AccelerateInterpolator());
        mSlideOut.setFillAfter(true);
        mSlideOut.setAnimationListener(mAnimListener);
    }

    private Animation.AnimationListener mAnimListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            animation.cancel();
            mContent.clearAnimation();
            switch (mState) {
                case Closing:
                    switchToState(State.Collapsed);
                    break;
                case Opening:
                    switchToState(State.Expanded);
                    break;
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }
    };

    @Override
    public void onGesture(GestureOverlayView overlay, MotionEvent event) {
    }

    @Override
    public void onGestureCancelled(GestureOverlayView overlay, MotionEvent event) {
    }

    @Override
    public void onGestureEnded(GestureOverlayView overlay, MotionEvent event) {
        Gesture gesture = overlay.getGesture();
        List<Prediction> predictions = mStore.recognize(gesture);
        for (Prediction prediction : predictions) {
            if (prediction.score >= 2.0) {
                switchToState(State.Closing);
                String uri = prediction.name.substring(prediction.name.indexOf('|') + 1);
                launchShortcut(uri);
                break;
            }
        }
    }

    @Override
    public void onGestureStarted(GestureOverlayView overlay, MotionEvent event) {
        if (mState == State.Expanded) {
            switchToState(State.Gesturing);
        }
    }

    private enum State {Collapsed, Expanded, Gesturing, Opening, Closing}
}
