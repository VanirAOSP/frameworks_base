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

package com.android.systemui.statusbar.phone;

import android.animation.LayoutTransition;
import android.animation.LayoutTransition.TransitionListener;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.ActivityManagerNative;
import android.app.StatusBarManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.AlphaAnimation;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityManager.TouchExplorationStateChangeListener;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.Space;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.util.vanir.AwesomeConstants;
import com.android.internal.util.vanir.AwesomeConstants.AwesomeConstant;
import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.DelegateViewHelper;
import com.android.systemui.statusbar.policy.DeadZone;
import com.android.systemui.statusbar.policy.LayoutChangerButtonView;
import com.android.systemui.statusbar.policy.LayoutChangerButtonView.LayoutButtonInfo;
import com.android.systemui.statusbar.policy.KeyButtonView;
import com.android.systemui.statusbar.policy.KeyButtonView.AwesomeButtonInfo;
import com.android.systemui.statusbar.policy.NxButtonView;
import com.android.systemui.statusbar.policy.NxButtonView.NxButtonInfo;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;

public class NavigationBarView extends LinearLayout {
    final static boolean DEBUG = false;
    final static String TAG = "PhoneStatusBar/NavigationBarView";

    final static boolean NAVBAR_ALWAYS_AT_RIGHT = true;

    // slippery nav bar when everything is disabled, e.g. during setup
    final static boolean SLIPPERY_WHEN_DISABLED = true;

    final Display mDisplay;
    View mCurrentView = null;
    View[] mRotatedViews = new View[4];
    
    int mBarSize;
    boolean mVertical;
    boolean mScreenOn;

    boolean mShowMenu;
    int mDisabledFlags = 0;
    int mNavigationIconHints = 0;

    private float mButtonWidth, mNxButtonWidth, mMenuButtonWidth;
    private int mMenuButtonId;

    final boolean mTablet = isTablet(mContext);

    ArrayList<ArrayList<AwesomeButtonInfo>> mAllButtonContainers = new ArrayList<ArrayList<AwesomeButtonInfo>>();
    ArrayList<AwesomeButtonInfo> mIMEKeyArray = new ArrayList<AwesomeButtonInfo>();

    public static int mRightStopKey = 6;

    String mButtonContainerString_1;
    String mButtonContainerString_2;
    String mButtonContainerString_3;
    String mButtonContainerString_4;
    String mButtonContainerString_5;

    private ContentObserver mSettingsObserver;
    private ContentObserver mDisablePrefsObserver;

    private LockPatternUtils mLockPatternUtils;

    boolean mWasNotifsButtonVisible = false;

    private DelegateViewHelper mDelegateHelper;
    private DeadZone mDeadZone;
    private final NavigationBarTransitions mBarTransitions;

    private boolean mPrefNavring;
    private boolean mPrefLockscreen;
    private boolean mLegacyMenu;
    private boolean mArrows;
    private String mIMEKeyLayout;
    private boolean showingIME;
    private int mButtonLayouts;
    private int mCurrentLayout = 1; // Default

    // workaround for LayoutTransitions leaving the nav buttons in a weird state (bug 5549288)
    final static boolean WORKAROUND_INVALID_LAYOUT = true;
    final static int MSG_CHECK_INVALID_LAYOUT = 8686;

    // used to disable the camera icon in navbar when disabled by DPM
    private boolean mCameraDisabledByDpm;

    // performs manual animation in sync with layout transitions
    private final NavTransitionListener mTransitionListener = new NavTransitionListener();

    private Resources mThemedResources;

    private class NavTransitionListener implements TransitionListener {
        private boolean mBackTransitioning;
        private boolean mHomeAppearing;
        private long mStartDelay;
        private long mDuration;
        private TimeInterpolator mInterpolator;

        @Override
        public void startTransition(LayoutTransition transition, ViewGroup container,
                                    View view, int transitionType) {
            if (view.getTag() != null) {
                if (view.getTag().equals(AwesomeConstant.ACTION_BACK.value())) {
                    mBackTransitioning = true;
                } else if (view.getTag().equals(AwesomeConstant.ACTION_HOME.value())
                        && transitionType == LayoutTransition.APPEARING) {
                    mHomeAppearing = true;
                    mStartDelay = transition.getStartDelay(transitionType);
                    mDuration = transition.getDuration(transitionType);
                    mInterpolator = transition.getInterpolator(transitionType);
                }
            }
        }

        @Override
        public void endTransition(LayoutTransition transition, ViewGroup container,
                                  View view, int transitionType) {
            if (view.getTag() != null) {
                if (view.getTag().equals(AwesomeConstant.ACTION_BACK.value())) {
                    mBackTransitioning = false;
                } else if (view.getTag().equals(AwesomeConstant.ACTION_HOME.value())
                        && transitionType == LayoutTransition.APPEARING) {
                    mHomeAppearing = false;
                }
            }
        }

        public void onBackAltCleared() {
            if (getBackButton() == null) return;
            // When dismissing ime during unlock, force the back button to run the same appearance
            // animation as home (if we catch this condition early enough).
            if (!mBackTransitioning && getBackButton().getVisibility() == VISIBLE
                    && mHomeAppearing && getHomeButton().getAlpha() == 0) {
                getBackButton().setAlpha(0);
                ValueAnimator a = ObjectAnimator.ofFloat(getBackButton(), "alpha", 0, 1);
                a.setStartDelay(mStartDelay);
                a.setDuration(mDuration);
                a.setInterpolator(mInterpolator);
                a.start();
            }
        }
    }

    // simplified click handler to be used when device is in accessibility mode
    private final OnClickListener mAccessibilityClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.camera_button) {
                KeyguardTouchDelegate.getInstance(getContext()).launchCamera();
            } else if (v.getId() == R.id.search_light) {
                KeyguardTouchDelegate.getInstance(getContext()).showAssistant();
            }
        }
    };

    private final OnTouchListener mCameraTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // disable search gesture while interacting with additional navbar button
                    mDelegateHelper.setDisabled(true);
                    mBarTransitions.setContentVisible(false);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mDelegateHelper.setDisabled(false);
                    mBarTransitions.setContentVisible(true);
                    break;
            }
            return KeyguardTouchDelegate.getInstance(getContext()).dispatch(event);
        }
    };

    private final OnClickListener mNavBarClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            KeyguardTouchDelegate.getInstance(getContext()).dispatchButtonClick(0);
        }
    };

    private class H extends Handler {
        public void handleMessage(Message m) {
            switch (m.what) {
                case MSG_CHECK_INVALID_LAYOUT:
                    final String how = "" + m.obj;
                    final int w = getWidth();
                    final int h = getHeight();
                    final int vw = mCurrentView.getWidth();
                    final int vh = mCurrentView.getHeight();

                    if (h != vh || w != vw) {
                        Log.w(TAG, String.format(
                                "*** Invalid layout in navigation bar (%s this=%dx%d cur=%dx%d)",
                                how, w, h, vw, vh));
                        if (WORKAROUND_INVALID_LAYOUT) {
                            requestLayout();
                        }
                    }
                    break;
            }
        }
    }

    public NavigationBarView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mDisplay = ((WindowManager) context.getSystemService(
                Context.WINDOW_SERVICE)).getDefaultDisplay();

        final Resources res = mContext.getResources();
        final ContentResolver cr = mContext.getContentResolver();

        mBarSize = res.getDimensionPixelSize(R.dimen.navigation_bar_size);
        mVertical = false;
        mShowMenu = false;
        mDelegateHelper = new DelegateViewHelper(this);
        mButtonWidth = res.getDimensionPixelSize(R.dimen.navigation_key_width);
        mNxButtonWidth = res.getDimensionPixelSize(R.dimen.navigation_nx_key_width);
        mMenuButtonWidth = res.getDimensionPixelSize(R.dimen.navigation_menu_key_width);

        mBarTransitions = new NavigationBarTransitions(this);
        mBarTransitions.updateResources(res);

        mLegacyMenu = Settings.System.getInt(cr, Settings.System.NAVIGATION_BAR_SIDEKEYS, 1) == 1;
        mArrows = Settings.System.getInt(cr, Settings.System.NAVIGATION_BAR_ARROWS, 0) == 1;
        mButtonContainerString_1 = Settings.System.getString(cr, Settings.System.NAVIGATION_BAR_BUTTONS);
        mButtonLayouts = Settings.System.getInt(cr, Settings.System.NAVIGATION_BAR_ALTERNATE_LAYOUTS, 1);
        if (mButtonLayouts > 1) {
            mRightStopKey = mButtonLayouts + 1;
            mButtonContainerString_2 = Settings.System.getString(cr, Settings.System.NAVIGATION_BAR_BUTTONS_TWO);
            mButtonContainerString_3 = Settings.System.getString(cr, Settings.System.NAVIGATION_BAR_BUTTONS_THREE);
            mButtonContainerString_4 = Settings.System.getString(cr, Settings.System.NAVIGATION_BAR_BUTTONS_FOUR);
            mButtonContainerString_5 = Settings.System.getString(cr, Settings.System.NAVIGATION_BAR_BUTTONS_FIVE);
        } else {
            mCurrentLayout = 1;
        }
        mIMEKeyLayout = Settings.System.getString(cr, Settings.System.NAVIGATION_IME_LAYOUT);

        mCameraDisabledByDpm = isCameraDisabledByDpm();
        watchForDevicePolicyChanges();
        mLockPatternUtils = new LockPatternUtils(context);
    }

    private void watchForDevicePolicyChanges() {
        final IntentFilter filter = new IntentFilter();
        filter.addAction(DevicePolicyManager.ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED);
        mContext.registerReceiver(new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        mCameraDisabledByDpm = isCameraDisabledByDpm();
                    }
                });
            }
        }, filter);
    }

    public BarTransitions getBarTransitions() {
        return mBarTransitions;
    }

    public void setDelegateView(View view) {
        mDelegateHelper.setDelegateView(view);
    }

    public void setBar(BaseStatusBar phoneStatusBar) {
        mDelegateHelper.setBar(phoneStatusBar);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mDeadZone != null && event.getAction() == MotionEvent.ACTION_OUTSIDE) {
            mDeadZone.poke(event);
        }
        if (mDelegateHelper != null) {
            boolean ret = mDelegateHelper.onInterceptTouchEvent(event);
            if (ret) return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return mDelegateHelper.onInterceptTouchEvent(event);
    }

    private H mHandler = new H();

    public View getCurrentView() {
        return mCurrentView;
    }

    public View getRecentsButton() {
        return mCurrentView.findViewWithTag(AwesomeConstant.ACTION_RECENTS.value());
    }

    public View getLeftLayoutButton() {
        return mCurrentView.findViewWithTag(AwesomeConstant.ACTION_LAYOUT_LEFT.value());
    }

    public View getRightLayoutButton() {
        return mCurrentView.findViewWithTag(AwesomeConstant.ACTION_LAYOUT_RIGHT.value());
    }

    public View getMenuButton() {
        return mCurrentView.findViewById(mMenuButtonId);
    }

    public View getBackButton() {
        return mCurrentView.findViewWithTag(AwesomeConstant.ACTION_BACK.value());
    }

    public View getEmptySpace() {
        return mCurrentView.findViewWithTag(AwesomeConstant.ACTION_BLANK.value());
    }

    public View getHomeButton() {
        return mCurrentView.findViewWithTag(AwesomeConstant.ACTION_HOME.value());
    }

    // for when home is disabled, but search isn't
    public View getSearchLight() {
        return mCurrentView.findViewById(R.id.search_light);
    }

    // shown when keyguard is visible and camera is available
    public View getCameraButton() {
        return mCurrentView.findViewById(R.id.camera_button);
    }

    // used for lockscreen notifications
    public View getNotifsButton() {
        return mCurrentView.findViewById(R.id.show_notifs);
    }

    public void updateResources(Resources res) {
        mThemedResources = res;
        for (int i = 0; i < mRotatedViews.length; i++) {
            ViewGroup container = (ViewGroup) mRotatedViews[i];
            if (container != null) {
                updateKeyButtonViewResources(container);
                updateLightsOutResources(container);
            }
        }
    }

    private void updateKeyButtonViewResources(ViewGroup container) {
        if (mCurrentView == null) return;
        for (final AwesomeConstant k : AwesomeConstant.values()) {
            final View child = mCurrentView.findViewWithTag(k.value());

            if (child instanceof KeyButtonView) {
                ((KeyButtonView) child).updateResources(mThemedResources);
            }
        }
    }

    private void updateLightsOutResources(ViewGroup container) {
        ViewGroup lightsOut = (ViewGroup) container.findViewById(R.id.lights_out);
        if (lightsOut != null) {
            final int nChildren = lightsOut.getChildCount();
            for (int i = 0; i < nChildren; i++) {
                final View child = lightsOut.getChildAt(i);
                if (child instanceof ImageView) {
                    final ImageView iv = (ImageView) child;
                    // clear out the existing drawable, this is required since the
                    // ImageView keeps track of the resource ID and if it is the same
                    // it will not update the drawable.
                    iv.setImageDrawable(null);
                    iv.setImageDrawable(mThemedResources.getDrawable(
                            R.drawable.ic_sysbar_lights_out_dot_large));
                }
            }
        }
    }

    @Override
    public void setLayoutDirection(int layoutDirection) {
        super.setLayoutDirection(layoutDirection);
    }

    public void notifyScreenOn(boolean screenOn) {
        mScreenOn = screenOn;
        setDisabledFlags(mDisabledFlags, true);
    }

    public void setNavigationIconHints(int hints) {
        setNavigationIconHints(hints, false);
    }

    public void setNavigationIconHints(int hints, boolean force) {
        if (!force && hints == mNavigationIconHints) return;
        showingIME = (hints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0;

        if ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) != 0 && !showingIME) {
            mTransitionListener.onBackAltCleared();
        }
        if (DEBUG) {
            android.widget.Toast.makeText(mContext,
                    "Navigation icon hints = " + hints,
                    500).show();
        }

        mNavigationIconHints = hints;

        if (mArrows) {
            // add a slight delay to allow animations to finish
            removeCallbacks(mSetCustomBarLayout);
            mHandler.postDelayed(mSetCustomBarLayout, 10);
        } else {
            if (getBackButton() != null) {
                if (showingIME) {
                    ((ImageView) getBackButton()).setImageResource(R.drawable.ic_sysbar_back_ime);
                } else {
                    ((KeyButtonView) getBackButton()).setImage();
                }
            }
            setDisabledFlags(mDisabledFlags, true);
        }
    }

    final Runnable mSetCustomBarLayout = new Runnable() {
        @Override
        public void run() {
            setupNavigationButtons(getCurrentButtonArray());
            if (getBackButton() != null) {
                if (showingIME) {
                    ((ImageView) getBackButton()).setImageResource(R.drawable.ic_sysbar_back_ime);
                } else {
                    ((KeyButtonView) getBackButton()).setImage();
                }
            }
            setDisabledFlags(mDisabledFlags, true);
        }
    };

    public void notifyLayoutChange(int direction) {
        mCurrentLayout = (mCurrentLayout + direction);
        mHandler.post(mNotifyLayoutChanged);
    }

    final Runnable mNotifyLayoutChanged = new Runnable() {
        @Override
        public void run() {
            if (mCurrentLayout <= 0) {
                mCurrentLayout = mButtonLayouts;
            } else if (mCurrentLayout >= mRightStopKey) {
                mCurrentLayout = 1;
            }
            setupNavigationButtons();
        }
    };

    public void setButtonDrawable(int buttonId, final int iconId) {
        final ImageView iv = (ImageView)getNotifsButton();
        mHandler.post(new Runnable() {
            public void run() {
                if (iconId == 1) iv.setImageResource(R.drawable.search_light_land);
                mWasNotifsButtonVisible = iconId != 0 && ((mDisabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
                setVisibleOrGone(getNotifsButton(), mWasNotifsButtonVisible);
            }
        });
    }

    public void setDisabledFlags(int disabledFlags) {
        setDisabledFlags(disabledFlags, false);
    }

    public void setDisabledFlags(int disabledFlags, boolean force) {
        if (!force && mDisabledFlags == disabledFlags) return;

        mDisabledFlags = disabledFlags;

        if (getCurrentButtonArray().isEmpty()) return; // no buttons yet!

        final boolean disableHome = ((disabledFlags & View.STATUS_BAR_DISABLE_HOME) != 0);
        final boolean disableRecent = ((disabledFlags & View.STATUS_BAR_DISABLE_RECENT) != 0);
        final boolean disableBack = ((disabledFlags & View.STATUS_BAR_DISABLE_BACK) != 0)
                && ((mNavigationIconHints & StatusBarManager.NAVIGATION_HINT_BACK_ALT) == 0);
        final boolean disableSearch = ((disabledFlags & View.STATUS_BAR_DISABLE_SEARCH) != 0);

        if (SLIPPERY_WHEN_DISABLED) {
            setSlippery(disableHome && disableRecent && disableBack && disableSearch);
        }

        ViewGroup navButtons = (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
        if (navButtons != null) {
            LayoutTransition lt = navButtons.getLayoutTransition();
            if (lt != null) {
                if (!lt.getTransitionListeners().contains(mTransitionListener)) {
                    lt.addTransitionListener(mTransitionListener);
                }
                if (!mScreenOn && mCurrentView != null) {
                    lt.disableTransitionType(
                            LayoutTransition.CHANGE_APPEARING |
                                    LayoutTransition.CHANGE_DISAPPEARING |
                                    LayoutTransition.APPEARING |
                                    LayoutTransition.DISAPPEARING);
                }
            }
        }

        KeyButtonView[] allButtons = getAllButtons();
        for (KeyButtonView button : allButtons) {

            if (button != null) {
                Object tag = button.getTag();
                if (tag == null) {
                    setVisibleOrInvisible(button, !disableHome);
                } else if (AwesomeConstant.ACTION_HOME.value().equals(tag)) {
                    setVisibleOrInvisible(button, !disableHome);
                } else if (AwesomeConstant.ACTION_BACK.value().equals(tag)) {
                    setVisibleOrInvisible(button, !disableBack);
                } else if (AwesomeConstant.ACTION_RECENTS.value().equals(tag)) {
                    setVisibleOrInvisible(button, !disableRecent);
                } else {
                    // fall back to the recents flag for the other keys
                    setVisibleOrInvisible(button, !disableRecent);
                }
            }
        }

        final boolean showSearch = disableHome && !disableSearch && mPrefNavring;
        final boolean showCamera = disableHome && !disableSearch && !mCameraDisabledByDpm && mLockPatternUtils.getCameraEnabled();
        final boolean showNotifs = disableHome && !disableSearch && mPrefLockscreen;

        setVisibleOrGone(getSearchLight(), showSearch);
        setVisibleOrGone(getCameraButton(), showCamera);
        setVisibleOrGone(getNotifsButton(), showNotifs && mWasNotifsButtonVisible);

        if (mButtonLayouts > 1) {
            final boolean allowLayoutArrows = !disableHome && !showingIME;
            setVisibleOrInvisible(getLeftLayoutButton(), allowLayoutArrows);
            setVisibleOrInvisible(getRightLayoutButton(), allowLayoutArrows);
        }

        mBarTransitions.applyBackButtonQuiescentAlpha(mBarTransitions.getMode(), true /*animate*/);
    }

    private void setVisibleOrInvisible(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : INVISIBLE);
        }
    }

    private void setVisibleOrGone(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    private boolean isCameraDisabledByDpm() {
        final DevicePolicyManager dpm =
                (DevicePolicyManager) mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm != null) {
            try {
                final int userId = ActivityManagerNative.getDefault().getCurrentUser().id;
                final int disabledFlags = dpm.getKeyguardDisabledFeatures(null, userId);
                final  boolean disabledBecauseKeyguardSecure =
                        (disabledFlags & DevicePolicyManager.KEYGUARD_DISABLE_SECURE_CAMERA) != 0
                        && KeyguardTouchDelegate.getInstance(getContext()).isSecure();
                return dpm.getCameraDisabled(null) || disabledBecauseKeyguardSecure;
            } catch (RemoteException e) {
                Log.e(TAG, "Can't get userId", e);
            }
        }
        return false;
    }

    public void setSlippery(boolean newSlippery) {
        WindowManager.LayoutParams lp = (WindowManager.LayoutParams) getLayoutParams();
        if (lp != null) {
            boolean oldSlippery = (lp.flags & WindowManager.LayoutParams.FLAG_SLIPPERY) != 0;
            if (!oldSlippery && newSlippery) {
                lp.flags |= WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else if (oldSlippery && !newSlippery) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_SLIPPERY;
            } else {
                return;
            }
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            wm.updateViewLayout(this, lp);
        }
    }

    public void setMenuVisibility(final boolean show) {
        setMenuVisibility(show, false);
    }

    public void setMenuVisibility(final boolean show, final boolean force) {
        if (!force && mShowMenu == show) return;

        mShowMenu = show;

        if (mButtonLayouts > 1 && mLegacyMenu) {
            if (getRightLayoutButton() != null) {
                ((LayoutChangerButtonView) getRightLayoutButton()).setMenuAction(mShowMenu);
            }
        } else {
            if (getMenuButton() != null) {
                getMenuButton().setVisibility(mShowMenu ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    @Override
    public void onFinishInflate() {
        mRotatedViews[Surface.ROTATION_0] =
                mRotatedViews[Surface.ROTATION_180] = findViewById(R.id.rot0);

        mRotatedViews[Surface.ROTATION_90] = findViewById(R.id.rot90);

        mRotatedViews[Surface.ROTATION_270] = NAVBAR_ALWAYS_AT_RIGHT
                ? findViewById(R.id.rot90)
                : findViewById(R.id.rot270);

        mCurrentView = mRotatedViews[Surface.ROTATION_0];

        watchForAccessibilityChanges();
        loadButtonArrays();
        setDisabledFlags(mDisabledFlags);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        final ContentResolver r = mContext.getContentResolver();

        if (mSettingsObserver == null) {
            mSettingsObserver = new ContentObserver(new Handler()) {
                @Override
                public void onChange(boolean selfChange) {
                    mButtonContainerString_1 = Settings.System.getString(r, Settings.System.NAVIGATION_BAR_BUTTONS);
                    mArrows = Settings.System.getInt(r, Settings.System.NAVIGATION_BAR_ARROWS, 0) == 1;
                    mLegacyMenu = Settings.System.getInt(r, Settings.System.NAVIGATION_BAR_SIDEKEYS, 1) == 1;
                    mIMEKeyLayout = Settings.System.getString(r, Settings.System.NAVIGATION_IME_LAYOUT);
                    mButtonLayouts = Settings.System.getInt(r, Settings.System.NAVIGATION_BAR_ALTERNATE_LAYOUTS, 1);
                    if (mButtonLayouts > 1) {
                        mRightStopKey = mButtonLayouts + 1;
                        mButtonContainerString_2 = Settings.System.getString(r, Settings.System.NAVIGATION_BAR_BUTTONS_TWO);
                        mButtonContainerString_3 = Settings.System.getString(r, Settings.System.NAVIGATION_BAR_BUTTONS_THREE);
                        mButtonContainerString_4 = Settings.System.getString(r, Settings.System.NAVIGATION_BAR_BUTTONS_FOUR);
                        mButtonContainerString_5 = Settings.System.getString(r, Settings.System.NAVIGATION_BAR_BUTTONS_FIVE);
                    }
                    if (mCurrentLayout > mButtonLayouts) {
                        mCurrentLayout = mButtonLayouts;
                    } else {
                        mCurrentLayout = 1;
                    }
                    loadButtonArrays();
                }};

            r.registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_BUTTONS),
                    false, mSettingsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_BUTTONS_TWO),
                    false, mSettingsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_BUTTONS_THREE),
                    false, mSettingsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_BUTTONS_FOUR),
                    false, mSettingsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_BUTTONS_FIVE),
                    false, mSettingsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_SIDEKEYS),
                    false, mSettingsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_ARROWS),
                    false, mSettingsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_IME_LAYOUT),
                    false, mSettingsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.NAVIGATION_BAR_ALTERNATE_LAYOUTS),
                    false, mSettingsObserver);
        }

        if (mDisablePrefsObserver == null) {
            mDisablePrefsObserver = new ContentObserver(new Handler()) {
                @Override
                public void onChange(boolean selfChange) {
                    mPrefLockscreen = Settings.System.getInt(r,
                                    Settings.System.LOCKSCREEN_NOTIFICATIONS, 0) == 1
                            && Settings.System.getInt(r,
                                    Settings.System.ACTIVE_NOTIFICATIONS, 0) == 1
                            && Settings.System.getInt(r,
                                    Settings.System.ACTIVE_NOTIFICATIONS_PRIVACY_MODE, 0) == 0;
                    mPrefNavring = Settings.System.getInt(r,
                            Settings.System.ENABLE_NAVIGATION_RING, 1) == 1;
                }};

            r.registerContentObserver(Settings.System.getUriFor(Settings.System.LOCKSCREEN_NOTIFICATIONS),
                    false, mDisablePrefsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.ENABLE_NAVIGATION_RING),
                    false, mDisablePrefsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.ACTIVE_NOTIFICATIONS),
                    false, mDisablePrefsObserver);
            r.registerContentObserver(Settings.System.getUriFor(Settings.System.ACTIVE_NOTIFICATIONS_PRIVACY_MODE),
                    false, mDisablePrefsObserver);

            // pop goes the weasel
            mDisablePrefsObserver.onChange(true);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        final ContentResolver r = mContext.getContentResolver();

        if (mSettingsObserver != null) {
            r.unregisterContentObserver(mSettingsObserver);
            mSettingsObserver = null;
        }
        if (mDisablePrefsObserver != null) {
            r.unregisterContentObserver(mDisablePrefsObserver);
            mDisablePrefsObserver = null;
        }
    }

    private void loadButtonArrays() {
        String[] userButtons = {};
        String newButtons = "";
        // load active navbar layouts
        mAllButtonContainers.clear();

        for (int j = 0; j < mButtonLayouts; j++) {
            switch (j) {
                case 0:
                    newButtons = mButtonContainerString_1;
                    break;
                case 1:
                    newButtons = mButtonContainerString_2;
                    break;
                case 2:
                    newButtons = mButtonContainerString_3;
                    break;
                case 3:
                    newButtons = mButtonContainerString_4;
                    break;
                case 4:
                    newButtons = mButtonContainerString_5;
                    break;
            }
            if (newButtons == null) {
                newButtons = AwesomeConstants.defaultNavbarLayout(mContext);
            }
            userButtons = newButtons.split("\\|");;
            mAllButtonContainers.add(getButtonsArray(userButtons));
        }
        // load special case IME layout
        if (mArrows) {
            mIMEKeyArray.clear();
            if (userButtons == null) {
                userButtons = AwesomeConstants.defaultIMEKeyLayout(mContext).split("\\|");
            }
            userButtons = mIMEKeyLayout.split("\\|");
            for (String button : userButtons) {
                String[] actions = button.split(",", 4);
                mIMEKeyArray.add(new AwesomeButtonInfo(actions[0], actions[1], actions[2], actions[3]));
            }
        }
        setupNavigationButtons();
    }

    private ArrayList<AwesomeButtonInfo> getButtonsArray(String[] userButtons) {
        ArrayList<AwesomeButtonInfo> mButtonsContainer = new ArrayList<AwesomeButtonInfo>();
        for (String button : userButtons) {
            String[] actions = button.split(",", 4);
            mButtonsContainer.add(new AwesomeButtonInfo(actions[0], actions[1], actions[2], actions[3]));
        }
        return mButtonsContainer;
    }

    private ArrayList<AwesomeButtonInfo> getCurrentButtonArray() {
        if (mArrows && showingIME) return mIMEKeyArray;
        return mAllButtonContainers.get(mCurrentLayout - 1);
    }

    private void setupNavigationButtons() {
        setupNavigationButtons(getCurrentButtonArray());
    }

    private void setupNavigationButtons(ArrayList<AwesomeButtonInfo> buttonsArray) {
        final boolean stockThreeButtonLayout = buttonsArray.size() == 3;
        final int separatorSize = (int) mMenuButtonWidth;
        final int length = buttonsArray.size();
        LinearLayout navButtons;
        LinearLayout lightsOut;
        boolean landscape;

        for (int i = 0; i <= 1; i++) {
            landscape = (i == 1);

            navButtons = (LinearLayout) (landscape ? mRotatedViews[Surface.ROTATION_90]
                    .findViewById(R.id.nav_buttons) : mRotatedViews[Surface.ROTATION_0]
                    .findViewById(R.id.nav_buttons));
            lightsOut = (LinearLayout) (landscape ? mRotatedViews[Surface.ROTATION_90]
                    .findViewById(R.id.lights_out) : mRotatedViews[Surface.ROTATION_0]
                    .findViewById(R.id.lights_out));

            navButtons.removeAllViews();
            lightsOut.removeAllViews();

            if (mButtonLayouts > 1) {
                if (!mArrows || !showingIME) {
                    // left-side layout changer
                    LayoutButtonInfo leftButtonInfo = new LayoutButtonInfo(AwesomeConstant.ACTION_LAYOUT_LEFT.value());
                    LayoutChangerButtonView leftButton = new LayoutChangerButtonView(mContext, null);
                    leftButton.setButtonActions(leftButtonInfo);
                    leftButton.setImageResource(R.drawable.ic_sysbar_layout_left);
                    leftButton.setLayoutParams(getLayoutParams(landscape, mMenuButtonWidth, 0f));
                    leftButton.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                            : R.drawable.ic_sysbar_highlight);
                    // add the button and lights out views
                    addButton(navButtons, leftButton, landscape);
                    addLightsOutButton(lightsOut, leftButton, landscape, false);
                }
            }
            if (mLegacyMenu && mButtonLayouts == 1) {
                if (mTablet) {
                    // offset menu button
                    addSeparator(navButtons, landscape, (int) mMenuButtonWidth, 0f);
                    addSeparator(lightsOut, landscape, (int) mMenuButtonWidth, 0f);

                    // eats up that extra mTablet space
                    addSeparator(navButtons, landscape, 0, stockThreeButtonLayout ? 1f : 0.5f);
                    addSeparator(lightsOut, landscape, 0, stockThreeButtonLayout ? 1f : 0.5f);
                } else {
                    // on phone ui this offsets the right side menu button
                    addSeparator(navButtons, landscape, separatorSize, 0f);
                    addSeparator(lightsOut, landscape, separatorSize, 0f);
                }
            }

            // TEST: HARDCODED NX BUTTON VIEW
            NxButtonInfo nxButtonInfo = new NxButtonInfo(
                    AwesomeConstant.ACTION_MENU.value(),
                    AwesomeConstant.ACTION_RECENTS.value(),
                    AwesomeConstant.ACTION_BACK.value(),
                    AwesomeConstant.ACTION_HOME.value(),
                    AwesomeConstant.ACTION_NOTIFICATIONS.value());
            NxButtonView nxButton = new NxButtonView(mContext, null);
            nxButton.setButtonActions(nxButtonInfo);
            nxButton.setImageResource(R.drawable.ic_sysbar_nx);
            nxButton.setScaleType(ScaleType.FIT_XY);
            nxButton.setLayoutParams(getLayoutParams(landscape, mNxButtonWidth, 0f));
            nxButton.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                    : R.drawable.ic_sysbar_highlight);
            // add the button and lights out views
            addButton(navButtons, nxButton, landscape);
            addLightsOutButton(lightsOut, nxButton, landscape, false);

            for (int j = 0; j < length; j++) {
                // create the button
                AwesomeButtonInfo info = buttonsArray.get(j);
                KeyButtonView button = new KeyButtonView(mContext, null);
                button.setButtonActions(info);
                if (mTablet) {
                    button.setLayoutParams(getLayoutParams(landscape, mButtonWidth, 1f));
                    button.setGlowBackground(R.drawable.ic_sysbar_highlight);
                } else {
                    button.setLayoutParams(getLayoutParams(landscape, mButtonWidth, 0.1f));
                    button.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                            : R.drawable.ic_sysbar_highlight);
                }

                // add the button
                addButton(navButtons, button, landscape);

                if (!button.mHasBlankSingleAction) {
                    addLightsOutButton(lightsOut, button, landscape, false);
                } else {
                    addSeparator(lightsOut, landscape, (int) mButtonWidth, 0.5f);
                }
            }

            if (mLegacyMenu && mButtonLayouts == 1) {
                // legacy menu button
                AwesomeButtonInfo menuButtonInfo = new AwesomeButtonInfo(AwesomeConstant.ACTION_MENU.value(),
                        null, null, null);
                KeyButtonView menuButton = new KeyButtonView(mContext, null);
                menuButton.setButtonActions(menuButtonInfo);
                menuButton.setImageResource(R.drawable.ic_sysbar_menu);
                menuButton.setLayoutParams(getLayoutParams(landscape, mMenuButtonWidth, 0f));
                menuButton.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                        : R.drawable.ic_sysbar_highlight);
                    menuButton.setVisibility(mShowMenu ? View.VISIBLE : View.INVISIBLE);
                if (mMenuButtonId == 0) {
                    // assign the same id for layout and horizontal buttons
                    mMenuButtonId = View.generateViewId();
                }
                menuButton.setId(mMenuButtonId);
                // MENU BUTTON NOT YET ADDED ANYWHERE!

                if (mTablet) {
                    // om nom
                    addSeparator(navButtons, landscape, 0,  stockThreeButtonLayout ? 1f : 0.5f);
                    addSeparator(lightsOut, landscape, 0,  stockThreeButtonLayout ? 1f : 0.5f);

                    // add menu button last so it hangs on the edge
                    addButton(navButtons, menuButton, landscape);
                    addLightsOutButton(lightsOut, menuButton, landscape, true);
                } else {
                    addButton(navButtons, menuButton, landscape);
                    addLightsOutButton(lightsOut, menuButton, landscape, true);
                }
            }
            if (mButtonLayouts > 1) {
                if (!mArrows || !showingIME) {
                    // right-side layout changer button
                    LayoutButtonInfo rightButtonInfo = new LayoutButtonInfo(mShowMenu
                            ? AwesomeConstant.ACTION_MENU.value()
                            : AwesomeConstant.ACTION_LAYOUT_RIGHT.value());
                    LayoutChangerButtonView rightButton = new LayoutChangerButtonView(mContext, null);
                    rightButton.setButtonActions(rightButtonInfo);
                    rightButton.setImageResource(mShowMenu
                            ? R.drawable.ic_sysbar_menu
                            : R.drawable.ic_sysbar_layout_right);
                    rightButton.setLayoutParams(getLayoutParams(landscape, mMenuButtonWidth, 0f));
                    rightButton.setGlowBackground(landscape ? R.drawable.ic_sysbar_highlight_land
                            : R.drawable.ic_sysbar_highlight);
                    // add the button and lights out views
                    addButton(navButtons, rightButton, landscape);
                    addLightsOutButton(lightsOut, rightButton, landscape, false);
                }
            }
        }
        invalidate();
    }

    private void watchForAccessibilityChanges() {
        final AccessibilityManager am =
                (AccessibilityManager) mContext.getSystemService(Context.ACCESSIBILITY_SERVICE);

        // Set the initial state
        enableAccessibility(am.isTouchExplorationEnabled());

        // Watch for changes
        am.addTouchExplorationStateChangeListener(new TouchExplorationStateChangeListener() {
            @Override
            public void onTouchExplorationStateChanged(boolean enabled) {
                enableAccessibility(enabled);
            }
        });
    }

    private void enableAccessibility(boolean touchEnabled) {
        Log.v(TAG, "touchEnabled:" + touchEnabled);

        // Add a touch handler or accessibility click listener for camera and search buttons
        // for all view orientations.
        final OnClickListener onClickListener = touchEnabled ? mAccessibilityClickListener : null;
        final OnTouchListener onTouchListener = touchEnabled ? null : mCameraTouchListener;
        boolean hasCamera = false;
        for (int i = 0; i < mRotatedViews.length; i++) {
            final View cameraButton = mRotatedViews[i].findViewById(R.id.camera_button);
            final View notifsButton = mRotatedViews[i].findViewById(R.id.show_notifs);
            final View searchLight = mRotatedViews[i].findViewById(R.id.search_light);
            if (cameraButton != null) {
                hasCamera = true;
                cameraButton.setOnTouchListener(onTouchListener);
                cameraButton.setOnClickListener(onClickListener);
            }
            if (notifsButton != null) {
                notifsButton.setOnClickListener(mNavBarClickListener);
            }
            if (searchLight != null) {
                searchLight.setOnClickListener(onClickListener);
            }
        }
        if (hasCamera) {
            // Warm up KeyguardTouchDelegate so it's ready by the time the camera button is touched.
            // This will connect to KeyguardService so that touch events are processed.
            KeyguardTouchDelegate.getInstance(mContext);
        }
    }

    public boolean isVertical() {
        return mVertical;
    }

    public void reorient() {
        final int rot = mDisplay.getRotation();
        for (int i = 0; i < 4; i++) {
            mRotatedViews[i].setVisibility(View.GONE);
        }

        mCurrentView = mRotatedViews[rot];
        mCurrentView.setVisibility(View.VISIBLE);

        mDeadZone = (DeadZone) mCurrentView.findViewById(R.id.deadzone);

        // force the low profile & disabled states into compliance
        mBarTransitions.init(mVertical);

        setMenuVisibility(mShowMenu, true /* force */);

        if (DEBUG) {
            Log.d(TAG, "reorient(): rot=" + mDisplay.getRotation());
        }

        if (mArrows && showingIME) {
            setNavigationIconHints(mNavigationIconHints, true);
        } else {
            setDisabledFlags(mDisabledFlags, true /* force */);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mDelegateHelper.setInitialTouchRegion(getAllButtons());
    }

    public KeyButtonView[] getAllButtons() {
        ViewGroup view = (ViewGroup) mCurrentView.findViewById(R.id.nav_buttons);
        int N = view.getChildCount();
        KeyButtonView[] views = new KeyButtonView[N];

        int workingIdx = 0;
        for (int i = 0; i < N; i++) {
            View child = view.getChildAt(i);
            if (child.getId() == mMenuButtonId) {
                // included in container but not in buttons array
                continue;
            }
            if (child instanceof KeyButtonView) {
                views[workingIdx++] = (KeyButtonView) child;
            }
        }
        return views;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if (DEBUG) Log.d(TAG, String.format(
                "onSizeChanged: (%dx%d) old: (%dx%d)", w, h, oldw, oldh));

        final boolean newVertical = w > 0 && h > w;
        if (newVertical != mVertical) {
            mVertical = newVertical;
            //Log.v(TAG, String.format("onSizeChanged: h=%d, w=%d, vert=%s", h, w, mVertical?"y":"n"));
            reorient();
        }

        postCheckForInvalidLayout("sizeChanged");
        super.onSizeChanged(w, h, oldw, oldh);
    }

    /*
    @Override
    protected void onLayout (boolean changed, int left, int top, int right, int bottom) {
        if (DEBUG) Log.d(TAG, String.format(
                    "onLayout: %s (%d,%d,%d,%d)",
                    changed?"changed":"notchanged", left, top, right, bottom));
        super.onLayout(changed, left, top, right, bottom);
    }

    // uncomment this for extra defensiveness in WORKAROUND_INVALID_LAYOUT situations: if all else
    // fails, any touch on the display will fix the layout.
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (DEBUG) Log.d(TAG, "onInterceptTouchEvent: " + ev.toString());
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            postCheckForInvalidLayout("touch");
        }
        return super.onInterceptTouchEvent(ev);
    }
    */


    private String getResourceName(int resId) {
        if (resId != 0) {
            final android.content.res.Resources res = mContext.getResources();
            try {
                return res.getResourceName(resId);
            } catch (android.content.res.Resources.NotFoundException ex) {
                return "(unknown)";
            }
        } else {
            return "(null)";
        }
    }

    private void postCheckForInvalidLayout(final String how) {
        mHandler.obtainMessage(MSG_CHECK_INVALID_LAYOUT, 0, 0, how).sendToTarget();
    }

    private static String visibilityToString(int vis) {
        switch (vis) {
            case View.INVISIBLE:
                return "INVISIBLE";
            case View.GONE:
                return "GONE";
            default:
                return "VISIBLE";
        }
    }

    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("NavigationBarView {");
        final Rect r = new Rect();
        final Point size = new Point();
        mDisplay.getRealSize(size);

        pw.println(String.format("      this: " + PhoneStatusBar.viewInfo(this)
                + " " + visibilityToString(getVisibility())));

        getWindowVisibleDisplayFrame(r);
        final boolean offscreen = r.right > size.x || r.bottom > size.y;
        pw.println("      window: "
                + r.toShortString()
                + " " + visibilityToString(getWindowVisibility())
                + (offscreen ? " OFFSCREEN!" : ""));

        pw.println(String.format("      mCurrentView: id=%s (%dx%d) %s",
                getResourceName(mCurrentView.getId()),
                mCurrentView.getWidth(), mCurrentView.getHeight(),
                visibilityToString(mCurrentView.getVisibility())));

        pw.println(String.format("      disabled=0x%08x vertical=%s menu=%s",
                mDisabledFlags,
                mVertical ? "true" : "false",
                mShowMenu ? "true" : "false"));

        dumpButton(pw, "back", getBackButton());
        dumpButton(pw, "home", getHomeButton());
        dumpButton(pw, "rcnt", getRecentsButton());
        dumpButton(pw, "menu", getMenuButton());
        dumpButton(pw, "srch", getSearchLight());
        dumpButton(pw, "cmra", getCameraButton());

        pw.println("    }");
    }

    private static void dumpButton(PrintWriter pw, String caption, View button) {
        pw.print("      " + caption + ": ");
        if (button == null) {
            pw.print("null");
        } else {
            pw.print(PhoneStatusBar.viewInfo(button)
                    + " " + visibilityToString(button.getVisibility())
                    + " alpha=" + button.getAlpha()
            );
            if (button instanceof KeyButtonView) {
                pw.print(" drawingAlpha=" + ((KeyButtonView) button).getDrawingAlpha());
                pw.print(" quiescentAlpha=" + ((KeyButtonView) button).getQuiescentAlpha());
            }
        }
        pw.println();
    }

    private void addSeparator(LinearLayout layout, boolean landscape, int size, float weight) {
        Space separator = new Space(mContext);
        separator.setLayoutParams(getLayoutParams(landscape, size, weight));
        if (landscape && !mTablet) {
            layout.addView(separator, 0);
        } else {
            layout.addView(separator);
        }
    }

    private void addButton(ViewGroup root, View v, boolean landscape) {
        if (landscape && !mTablet)
            root.addView(v, 0);
        else
            root.addView(v);
    }

    private void addLightsOutButton(LinearLayout root, View v, boolean landscape, boolean empty) {
        ImageView addMe = new ImageView(mContext);
        addMe.setLayoutParams(v.getLayoutParams());
        addMe.setImageResource(empty ? R.drawable.ic_sysbar_lights_out_dot_large
                : R.drawable.ic_sysbar_lights_out_dot_small);
        addMe.setScaleType(ImageView.ScaleType.CENTER);
        addMe.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        if (landscape && !mTablet)
            root.addView(addMe, 0);
        else
            root.addView(addMe);
    }

    public LinearLayout.LayoutParams getLayoutParams(boolean landscape, float px, float weight) {
        if (weight != 0) {
            px = 0;
        }
        return landscape && !mTablet ?
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) px, weight) :
                new LinearLayout.LayoutParams((int) px, LinearLayout.LayoutParams.MATCH_PARENT, weight);
    }

    public static boolean isTablet(Context context) {
        boolean xlarge = ((context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == 4);
        boolean large = ((context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_LARGE);
        return (xlarge || large);
    }
}
