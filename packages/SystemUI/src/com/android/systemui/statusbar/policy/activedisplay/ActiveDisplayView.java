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
package com.android.systemui.statusbar.policy.activedisplay;

import android.animation.ObjectAnimator;
import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.INotificationManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Canvas;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.INotificationListener;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RemoteViews;

import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.GlowPadView.OnTriggerListener;
import com.android.internal.widget.multiwaveview.TargetDrawable;
import com.android.internal.widget.LockPatternUtils;

import com.android.systemui.R;
import com.android.systemui.statusbar.BaseStatusBar;
import com.android.systemui.statusbar.phone.KeyguardTouchDelegate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class ActiveDisplayView extends FrameLayout {
    private static final boolean DEBUG = false;
    private static final String TAG = "ActiveDisplayView";

    private static final String ACTION_REDISPLAY_NOTIFICATION
            = "com.android.systemui.action.REDISPLAY_NOTIFICATION";

    private static final String DISMISS_KEYGUARD_SECURELY_ACTION
            = "com.android.keyguard.action.DISMISS_KEYGUARD_SECURELY";

    private static final String ACTION_DISPLAY_TIMEOUT
            = "com.android.systemui.action.DISPLAY_TIMEOUT";

    private static final int MAX_OVERFLOW_ICONS = 8;

    private static final int HIDE_NOTIFICATIONS_BELOW_SCORE = Notification.PRIORITY_LOW;

    // the different pocket mode options
    private static final int POCKET_MODE_OFF = 0;
    private static final int POCKET_MODE_NOTIFICATIONS_ONLY = 1;
    private static final int POCKET_MODE_ACTIVE_DISPLAY = 2;

    // messages sent to the handler for processing
    private static final int MSG_SHOW_NOTIFICATION_VIEW = 1000;
    private static final int MSG_HIDE_NOTIFICATION_VIEW = 1001;
    private static final int MSG_SHOW_NOTIFICATION      = 1002;
    private static final int MSG_DISMISS_NOTIFICATION   = 1004;

    INotificationManager mNM;
    INotificationListenerWrapper mNotificationListener;
    StatusBarNotification mNotification;
    PowerManager mPM;

    private TelephonyManager mTM;
    private StatusBarManager mStatusBarManager;
    private SensorManager mSensorManager;
    Sensor mProximitySensor;
    long mPocketTime = 0;

    GlowPadView mGlowPadView;
    View mRemoteView;
    View mClock;
    private FrameLayout mRemoteViewLayout;
    private FrameLayout mContents;
    private ObjectAnimator mAnim;
    private Drawable mNotificationDrawable;
    private int mCreationOrientation;
    volatile boolean mScreenOnState = true;
    LinearLayout mOverflowNotifications;
    private LayoutParams mRemoteViewLayoutParams;
    private int mIconSize;
    private int mIconMargin;
    private int mIconPadding;
    private LinearLayout.LayoutParams mOverflowLayoutParams;
    private boolean mCallbacksRegistered = false;
    private boolean mAttached;

    // user customizable settings
    private SettingsObserver mSettingsObserver;
    boolean mNotOverridden;
    boolean mDisplayNotificationText = false;
    boolean hideNonClearable = true;
    boolean mHideLowPriorityNotifications = false;
    int mPocketMode = POCKET_MODE_OFF;
    boolean privacyMode = false;
    boolean mQuietTime = false;
    long mRedisplayTimeout = 0;
    Set<String> mExcludedApps = new HashSet<String>();
    long mDisplayTimeout = 8000L;
    long mProximityThreshold = 5000L;
    boolean mDistanceFar = true;

    private static final String[] AUTO_BANNED_PACKAGES = new String[] {
        "android",
        "com.android.music",
        "com.andrew.apollo",
        "com.google.android.music",
        "com.android.providers.downloads"
    };

    /**
     * Simple class that listens to changes in notifications
     */
    private class INotificationListenerWrapper extends INotificationListener.Stub {
        @Override
        public void onNotificationPosted(final StatusBarNotification sbn) {
            if (mQuietTime) {
                if (inQuietHours()) return;
            }
            synchronized (this) {
                if (shouldShowNotificationForPocketMode() && isValidNotification(sbn)) {
                    // need to make sure either the screen is off or the user is currently
                    // viewing the notifications
                    if (ActiveDisplayView.this.getVisibility() == View.VISIBLE
                            || !mScreenOnState) {
                        showNotification(sbn, true);
                        turnScreenOn();
                    }

                }
            }
        }
        @Override
        public void onNotificationRemoved(final StatusBarNotification sbn) {
            if (mNotification != null && sbn.getPackageName().equals(mNotification.getPackageName())) {
                if (getVisibility() == View.VISIBLE) {
                    mNotification = getNextAvailableNotification();
                    if (mNotification != null) {
                        setActiveNotification(mNotification, true);
                        updateTimeoutTimer();
                        return;
                    }
                } else {
                    mNotification = null;
                }
            }
        }
    }

    private OnTriggerListener mOnTriggerListener = new OnTriggerListener() {

        // Targets
        private static final int UNLOCK_TARGET = 0;
        private static final int OPEN_APP_TARGET = 4;
        private static final int DISMISS_TARGET = 6;

        public void onTrigger(final View v, final int target) {

            if (target == UNLOCK_TARGET) {
                disableProximitySensor();
                mNotification = null;
                mPocketTime = 0;

                // hide the notification view
                mHandler.removeMessages(MSG_HIDE_NOTIFICATION_VIEW);
                mHandler.sendEmptyMessage(MSG_HIDE_NOTIFICATION_VIEW);

                // unlock the keyguard
                Intent intent = new Intent();
                intent.setAction(DISMISS_KEYGUARD_SECURELY_ACTION);
                mContext.sendBroadcast(intent);

            } else if (target == OPEN_APP_TARGET) {
                disableProximitySensor();
                cancelTimeoutTimer();

                // hide the notification view
                mHandler.removeMessages(MSG_HIDE_NOTIFICATION_VIEW);
                mHandler.sendEmptyMessage(MSG_HIDE_NOTIFICATION_VIEW);

                try {
                    ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
                    ActivityManagerNative.getDefault().resumeAppSwitches();
                } catch (RemoteException e) {
                }

                if (mNotification != null) {
                    PendingIntent i = mNotification.getNotification().contentIntent;
                    if (i != null) {
                        try {
                            Intent intent = i.getIntent();
                            intent.setFlags(
                                intent.getFlags()
                                | Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            if (i.isActivity()) ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
                            i.send();
                            KeyguardTouchDelegate.getInstance(mContext).dismiss();
                        } catch (CanceledException ex) {
                        } catch (RemoteException ex) {
                        }
                    }
                    if (mNotification.isClearable()) {
                        try {
                            mNM.cancelNotificationFromSystemListener(mNotificationListener,
                                    mNotification.getPackageName(), mNotification.getTag(),
                                    mNotification.getId());
                        } catch (RemoteException e) {
                        } catch (NullPointerException npe) {
                        }
                    }
                    mNotification = null;
                }

                mHandler.removeCallbacks(runSystemUiVisibilty);
                setVisibility(View.GONE);
                adjustStatusBarLocked(false);

            } else if (target == DISMISS_TARGET) {
                mHandler.removeMessages(MSG_DISMISS_NOTIFICATION);
                mHandler.sendEmptyMessage(MSG_DISMISS_NOTIFICATION);
                updateTimeoutTimer();
            }
        }

        public void onReleased(final View v, final int handle) {
            doTransition(mOverflowNotifications, 1.0f, 100);
            if (!privacyMode) {
                if (mRemoteView != null) {
                    ObjectAnimator.ofFloat(mRemoteView, "alpha", 0f).start();
                    ObjectAnimator.ofFloat(mClock, "alpha", 1f).start();
                }
            }
            // user stopped interacting so kick off the timeout timer
            updateTimeoutTimer();
        }

        public void onGrabbed(final View v, final int handle) {
            // prevent the ActiveDisplayView from turning off while user is interacting with it
            if (mPM == null)
                mPM = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mPM.userActivity(SystemClock.uptimeMillis(), true);
            cancelTimeoutTimer();
            doTransition(mOverflowNotifications, 0.0f, 100);
            if (!privacyMode) {
                if (mRemoteView != null) {
                    ObjectAnimator.ofFloat(mRemoteView, "alpha", 1f).start();
                    ObjectAnimator.ofFloat(mClock, "alpha", 0f).start();
                }
            }
        }

        public void onGrabbedStateChange(final View v, final int handle) {
        }

        public void onTargetChange(View v, int target) {
        }

        public void onFinishFinalAnimation() {
        }
    };

    /**
     * Class used to listen for changes to active display related settings
     */
    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver =
                    ActiveDisplayView.this.mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS_PRIVACY_MODE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS_QUIET_HOURS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_TEXT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_ALL_NOTIFICATIONS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS_HIDE_LOW_PRIORITY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS_POCKET_MODE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_REDISPLAY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_EXCLUDED_APPS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_TIMEOUT), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_DISPLAY_THRESHOLD), false, this);
            update();
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
            unregisterCallbacks();
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        public void update() {
            ContentResolver resolver = mContext.getContentResolver();

            mNotOverridden = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS, 0) == 1;
            mQuietTime = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS_QUIET_HOURS, 0) == 1;
            privacyMode = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.ACTIVE_NOTIFICATIONS_PRIVACY_MODE, 0) == 1;
            mDisplayNotificationText = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_DISPLAY_TEXT, 0) == 1;
            hideNonClearable = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.LOCKSCREEN_NOTIFICATIONS_HIDE_NON_CLEARABLE, 1) == 1;
            mHideLowPriorityNotifications = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS_HIDE_LOW_PRIORITY, 1) == 1;
            mPocketMode = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS_POCKET_MODE, POCKET_MODE_OFF);
            mRedisplayTimeout = Settings.System.getLong(
                    resolver, Settings.System.ACTIVE_DISPLAY_REDISPLAY, 0L);
            String excludedApps = Settings.System.getString(resolver,
                    Settings.System.ACTIVE_DISPLAY_EXCLUDED_APPS);
            mDisplayTimeout = Settings.System.getLong(
                    resolver, Settings.System.ACTIVE_DISPLAY_TIMEOUT, 8000L);
            mProximityThreshold = Settings.System.getLong(
                    resolver, Settings.System.ACTIVE_DISPLAY_THRESHOLD, 8000L);


            if (!TextUtils.isEmpty(excludedApps)) {
                String[] appsToExclude = excludedApps.split("\\|");
                mExcludedApps = new HashSet<String>(Arrays.asList(appsToExclude));
            }

            if (mRedisplayTimeout <= 0) {
                cancelRedisplayTimer();
            }

            registerCallbacks();

            if (!mNotOverridden) {
                unregisterCallbacks();
            }
        }
    }

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(final Message msg) {
            switch (msg.what) {
                case MSG_SHOW_NOTIFICATION_VIEW:
                    handleShowNotificationView();
                    break;
                case MSG_HIDE_NOTIFICATION_VIEW:
                    handleHideNotificationView();
                    break;
                case MSG_SHOW_NOTIFICATION:
                    boolean ping = msg.arg1 == 1;
                    handleShowNotification(ping);
                    break;
                case MSG_DISMISS_NOTIFICATION:
                    handleDismissNotification();
                    break;
                default:
                    break;
            }
        }

        private void handleShowNotificationView() {
            setVisibility(View.VISIBLE);
            setSystemUIVisibility();
            mHandler.postDelayed(runSystemUiVisibilty, 100);
        }

        private void handleHideNotificationView() {
            mHandler.removeCallbacks(runSystemUiVisibilty);
            setVisibility(View.GONE);
            cancelTimeoutTimer();
            adjustStatusBarLocked(false);
        }

        private void handleShowNotification(boolean ping) {
            if (!mNotOverridden) return;
            handleShowNotificationView();
            setActiveNotification(mNotification, true);
            inflateRemoteView(mNotification);
            if (ping) mGlowPadView.ping();
        }

        private void handleDismissNotification() {
            if (mNotification.isClearable()) {
                try {
                    mNM.cancelNotificationFromSystemListener(mNotificationListener,
                            mNotification.getPackageName(), mNotification.getTag(),
                            mNotification.getId());
                } catch (RemoteException e) {
                } catch (NullPointerException npe) {
                } finally {
                    if (mRemoteView != null) mRemoteViewLayout.removeView(mRemoteView);
                }
                // get the next one
                mNotification = getNextAvailableNotification();
                if (mNotification != null) {
                    setActiveNotification(mNotification, true);
                    inflateRemoteView(mNotification);
                    invalidate();
                    mGlowPadView.ping();
                    updateTimeoutTimer();
                    return;
                } else {
                    // no more notifications
                    disableProximitySensor();
                    mPocketTime = 0;
                    mHandler.removeMessages(MSG_HIDE_NOTIFICATION_VIEW);
                    mHandler.sendEmptyMessage(MSG_HIDE_NOTIFICATION_VIEW);
                    return;
                }
            } else {
                // no clearable notifications to display so just turn screen off
                disableProximitySensor();
                mPocketTime = 0;
                turnScreenOff();
            }
        }
    };

    public ActiveDisplayView(Context context) {
        this(context, null);
    }

    public ActiveDisplayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mNM = INotificationManager.Stub.asInterface(
                    ServiceManager.getService(Context.NOTIFICATION_SERVICE));
        mNotificationListener = new INotificationListenerWrapper();
        mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
        mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mPM = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mTM = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);

        mIconSize = getResources().getDimensionPixelSize(R.dimen.overflow_icon_size);
        mIconMargin = getResources().getDimensionPixelSize(R.dimen.ad_notification_margin);
        mIconPadding = getResources().getDimensionPixelSize(R.dimen.overflow_icon_padding);

        mSettingsObserver = new SettingsObserver(mHandler);
        mCreationOrientation = Resources.getSystem().getConfiguration().orientation;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mContents = (FrameLayout) findViewById(R.id.active_view_contents);
        makeActiveDisplayView(mCreationOrientation, false);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mSettingsObserver.observe();
        if (mRedisplayTimeout > 0 && !mScreenOnState) updateRedisplayTimer();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mSettingsObserver.unobserve();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        makeActiveDisplayView(newConfig.orientation, true);
    }

    private void makeActiveDisplayView(int orientation, boolean recreate) {
        mContents.removeAllViews();
        View contents = View.inflate(mContext, R.layout.active_display_content, mContents);
        mGlowPadView = (GlowPadView) contents.findViewById(R.id.glow_pad_view);
        mGlowPadView.setOnTriggerListener(mOnTriggerListener);
        mGlowPadView.setDrawOuterRing(false);
        TargetDrawable nDrawable = new TargetDrawable(getResources(),
                R.drawable.ic_handle_notification_normal);
        mGlowPadView.setHandleDrawable(nDrawable);

        mRemoteViewLayout = (FrameLayout) contents.findViewById(R.id.remote_content_parent);
        mClock = contents.findViewById(R.id.clock_view);

        mOverflowNotifications = (LinearLayout) contents.findViewById(R.id.keyguard_other_notifications);
        mOverflowNotifications.setOnTouchListener(mOverflowTouchListener);

        mRemoteViewLayoutParams = getRemoteViewLayoutParams(orientation);
        mOverflowLayoutParams = getOverflowLayoutParams();
        updateResources();
        if (recreate) {
            updateTimeoutTimer();
            if (mNotification == null) {
                mNotification = getNextAvailableNotification();
            }
            if (shouldShowNotificationForPocketMode()) {
                showNotification(mNotification, true);
                if (!mScreenOnState) turnScreenOn();
            }
        }
    }

    private FrameLayout.LayoutParams getRemoteViewLayoutParams(int orientation) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                getResources().getDimensionPixelSize(R.dimen.notification_min_height),
                orientation == Configuration.ORIENTATION_LANDSCAPE ? Gravity.CENTER : Gravity.TOP);
        return lp;
    }

    private LinearLayout.LayoutParams getOverflowLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                mIconSize,
                mIconSize);
        lp.setMargins(mIconMargin, 0, mIconMargin, 0);
        return lp;
    }

    private StateListDrawable getLayeredDrawable(Drawable back, Drawable front, int inset, boolean frontBlank) {
        Resources res = getResources();
        InsetDrawable[] inactivelayer = new InsetDrawable[2];
        InsetDrawable[] activelayer = new InsetDrawable[2];
        inactivelayer[0] = new InsetDrawable(
                res.getDrawable(R.drawable.ic_ad_lock_pressed), 0, 0, 0, 0);
        inactivelayer[1] = new InsetDrawable(front, inset, inset, inset, inset);
        activelayer[0] = new InsetDrawable(back, 0, 0, 0, 0);
        activelayer[1] = new InsetDrawable(
                frontBlank ? res.getDrawable(android.R.color.transparent) : front, inset, inset, inset, inset);
        StateListDrawable states = new StateListDrawable();
        LayerDrawable inactiveLayerDrawable = new LayerDrawable(inactivelayer);
        inactiveLayerDrawable.setId(0, 0);
        inactiveLayerDrawable.setId(1, 1);
        LayerDrawable activeLayerDrawable = new LayerDrawable(activelayer);
        activeLayerDrawable.setId(0, 0);
        activeLayerDrawable.setId(1, 1);
        states.addState(TargetDrawable.STATE_INACTIVE, inactiveLayerDrawable);
        states.addState(TargetDrawable.STATE_ACTIVE, activeLayerDrawable);
        states.addState(TargetDrawable.STATE_FOCUSED, activeLayerDrawable);
        return states;
    }

    public void updateResources() {
        ArrayList<TargetDrawable> storedDraw = new ArrayList<TargetDrawable>();
        final Resources res = getResources();
        final int targetInset = res.getDimensionPixelSize(com.android.internal.R.dimen.lockscreen_target_inset);
        final Drawable blankActiveDrawable =
                res.getDrawable(R.drawable.ic_lockscreen_target_activated);
        final InsetDrawable activeBack = new InsetDrawable(blankActiveDrawable, 0, 0, 0, 0);

        // Add unlock target
        storedDraw.add(new TargetDrawable(res, res.getDrawable(R.drawable.ic_ad_target_unlock)));
        if (mNotificationDrawable != null) {
            storedDraw.add(new TargetDrawable(res, null));
            storedDraw.add(new TargetDrawable(res, null));
            storedDraw.add(new TargetDrawable(res, null));
            storedDraw.add(new TargetDrawable(res, getLayeredDrawable(activeBack,
                    mNotificationDrawable, targetInset, false)));
            storedDraw.add(new TargetDrawable(res, null));
            if (mNotification != null && mNotification.isClearable()) {
                storedDraw.add(new TargetDrawable(res, res.getDrawable(R.drawable.ic_ad_dismiss_notification)));
            } else {
                storedDraw.add(new TargetDrawable(res, res.getDrawable(R.drawable.ic_qs_power)));
            }
        }
        storedDraw.add(new TargetDrawable(res, null));
        mGlowPadView.setTargetResources(storedDraw);
    }

    private void doTransition(View view, float to, long duration) {
        if (mAnim != null) {
            mAnim.cancel();
        }
        mAnim = ObjectAnimator.ofFloat(view, "alpha", to);
        if (duration > 0) mAnim.setDuration(duration);
        mAnim.start();
    }

    protected void showNotificationView() {
        mHandler.removeMessages(MSG_SHOW_NOTIFICATION_VIEW);
        mHandler.sendEmptyMessage(MSG_SHOW_NOTIFICATION_VIEW);
    }

    protected void showNotification(StatusBarNotification sbn, boolean ping) {
        mNotification = sbn;
        Message msg = new Message();
        msg.what = MSG_SHOW_NOTIFICATION;
        msg.arg1 = ping ? 1 : 0;
        mHandler.removeMessages(MSG_SHOW_NOTIFICATION);
        mHandler.sendMessage(msg);
    }

    private final Runnable runSystemUiVisibilty = new Runnable() {
        public void run() {
            adjustStatusBarLocked(true);
        }
    };

    private void adjustStatusBarLocked(boolean hiding) {
        if (mStatusBarManager == null) {
			mStatusBarManager = (StatusBarManager)
			mContext.getSystemService(Context.STATUS_BAR_SERVICE);
        }
        if (mStatusBarManager == null) {
			Log.w(TAG, "Could not get status bar manager");
        } else {
			// Disable aspects of the system/status/navigation bars that must not be re-enabled by 
			// windows that appear on top, ever
			int flags = StatusBarManager.DISABLE_NONE;
			if (hiding) {
                flags |= StatusBarManager.DISABLE_BACK | StatusBarManager.DISABLE_HOME
                | StatusBarManager.DISABLE_RECENT | StatusBarManager.DISABLE_SEARCH;
			}
			mStatusBarManager.disable(flags);
        }
    }

    protected void setSystemUIVisibility() {
        int newVis = SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | SYSTEM_UI_FLAG_LAYOUT_STABLE;
        setSystemUiVisibility(newVis);
    }

    protected void onScreenTurnedOff() {
        cancelTimeoutTimer();
        if (mRedisplayTimeout > 0) updateRedisplayTimer();

        // hide the notification view
        mHandler.removeMessages(MSG_HIDE_NOTIFICATION_VIEW);
        mHandler.sendEmptyMessage(MSG_HIDE_NOTIFICATION_VIEW);

        if (mPocketMode == POCKET_MODE_ACTIVE_DISPLAY) {
            // delay initial proximity sample here
            mPocketTime = System.currentTimeMillis();
            enableProximitySensor();
        }
    }

    protected void turnScreenOff() {
        mHandler.removeCallbacks(runWakeDevice);
        if (mPM == null)
                mPM = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        mPM.goToSleep(SystemClock.uptimeMillis());
    }

    protected void turnScreenOn() {
        // to avoid flicker and showing any other screen than the ActiveDisplayView
        // we use a runnable posted with a 250ms delay to turn wake the device
        mHandler.removeCallbacks(runWakeDevice);
        mHandler.postDelayed(runWakeDevice, 250);
    }

    final Runnable runWakeDevice = new Runnable() {
        public void run() {
            if (!mDistanceFar) return;
            if (mPM == null)
                    mPM = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
            mPM.wakeUp(SystemClock.uptimeMillis());
            updateTimeoutTimer();
            doTransition(ActiveDisplayView.this, 1f, 1000);
        }
    };

    protected void enableProximitySensor() {
        if (mNotOverridden) {
            if (mProximitySensor != null && !mAttached) {
                mSensorManager.registerListener(mSensorListener, mProximitySensor, SensorManager.SENSOR_DELAY_UI);
                mAttached = true;
            }
        }
    }

    protected void disableProximitySensor() {
        if (mProximitySensor != null) {
            if (mProximitySensor != null) {
                mSensorManager.unregisterListener(mSensorListener, mProximitySensor);
                mAttached = false;
            }
        }
    }

    private void registerCallbacks() {
        if (!mCallbacksRegistered) {
            IntentFilter filter = new IntentFilter(ACTION_REDISPLAY_NOTIFICATION);
            filter.addAction(ACTION_DISPLAY_TIMEOUT);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_KEYGUARD_TARGET);
            mContext.registerReceiver(mBroadcastReceiver, filter);

            ComponentName cn = new ComponentName(mContext, getClass().getName());
            try {
                mNM.registerListener(mNotificationListener, cn, UserHandle.USER_ALL);
            } catch (RemoteException e) {
            }
            mCallbacksRegistered = true;
        }
    }

    private void unregisterCallbacks() {
        if (mPocketMode == POCKET_MODE_ACTIVE_DISPLAY) disableProximitySensor();
        if (mCallbacksRegistered) {
            mContext.unregisterReceiver(mBroadcastReceiver);

            if (mNotificationListener != null) {
                try {
                    mNM.unregisterListener(mNotificationListener, UserHandle.USER_ALL);
                } catch (RemoteException e) {
                }
            }
            mCallbacksRegistered = false;
        }
    }

    protected StatusBarNotification getNextAvailableNotification() {
        try {
            // check if other notifications exist and if so display the next one
            StatusBarNotification[] sbns = mNM
                    .getActiveNotificationsFromSystemListener(mNotificationListener);
            if (sbns == null) return null;
            for (int i = sbns.length - 1; i >= 0; i--) {
                if (sbns[i] == null)
                    continue;
                if (isValidNotification(sbns[i])) {
                    return sbns[i];
                }
            }
        } catch (RemoteException e) {
        }
        return null;
    }

    protected void updateOtherNotifications() {
        mOverflowNotifications.post(new Runnable() {
            @Override
            public void run() {
                try {
                    // check if other clearable notifications exist and if so display the next one
                    StatusBarNotification[] sbns = mNM
                            .getActiveNotificationsFromSystemListener(mNotificationListener);
                    mOverflowNotifications.removeAllViews();
                    for (int i = sbns.length - 1; i >= 0; i--) {
                        if (isValidNotification(sbns[i])
                                && mOverflowNotifications.getChildCount() < MAX_OVERFLOW_ICONS) {
                            ImageView iv = new ImageView(mContext);
                            if (mOverflowNotifications.getChildCount() < (MAX_OVERFLOW_ICONS - 1)) {
                                Drawable iconDrawable = null;
                                try {
                                    Context pkgContext = mContext.createPackageContext(
                                            sbns[i].getPackageName(), Context.CONTEXT_RESTRICTED);
                                    iconDrawable = pkgContext.getResources()
                                            .getDrawable(sbns[i].getNotification().icon);
                                } catch (NameNotFoundException nnfe) {
                                    iconDrawable = mContext.getResources()
                                            .getDrawable(R.drawable.ic_ad_unknown_icon);
                                } catch (Resources.NotFoundException nfe) {
                                    iconDrawable = mContext.getResources()
                                            .getDrawable(R.drawable.ic_ad_unknown_icon);
                                }
                                iv.setImageDrawable(iconDrawable);
                                iv.setTag(sbns[i]);
                                if (sbns[i].getPackageName().equals(mNotification.getPackageName())
                                        && sbns[i].getId() == mNotification.getId()) {
                                    iv.setBackgroundResource(R.drawable.ad_active_notification_background);
                                } else {
                                    iv.setBackgroundResource(0);
                                }
                            } else {
                                iv.setImageResource(R.drawable.ic_ad_morenotifications);
                            }
                            iv.setPadding(mIconPadding, mIconPadding, mIconPadding, mIconPadding);
                            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                            mOverflowNotifications.addView(iv, mOverflowLayoutParams);
                        }
                    }
                } catch (RemoteException re) {
                } catch (NullPointerException npe) {
                }
            }
        });
    }

    private OnTouchListener mOverflowTouchListener = new OnTouchListener() {
        int mLastChildPosition = -1;
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    mLastChildPosition = -1;
                case MotionEvent.ACTION_MOVE:
                    float x = event.getX();
                    float y = event.getY();
                    final int childCount = mOverflowNotifications.getChildCount();
                    Rect hitRect = new Rect();
                    for (int i = 0; i < childCount; i++) {
                        final ImageView iv = (ImageView) mOverflowNotifications.getChildAt(i);
                        final StatusBarNotification sbn = (StatusBarNotification) iv.getTag();
                        iv.getHitRect(hitRect);
                        if (i != mLastChildPosition ) {
                            if (hitRect.contains((int)x, (int)y)) {
                                mLastChildPosition = i;
                                if (sbn != null) {
                                    // swap the notification
                                    mNotification = sbn;
                                    setActiveNotification(sbn, false);
                                    iv.setBackgroundResource(R.drawable.ad_active_notification_background);
                                }
                            } else {
                                iv.setBackgroundResource(0);
                            }
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    inflateRemoteView(mNotification);
                    break;
            }
            updateTimeoutTimer();
            return true;
        }
    };

    /**
     * Determine if a given notification should be used.
     * @param sbn StatusBarNotification to check.
     * @return True if it should be used, false otherwise.
     */
    protected boolean isValidNotification(StatusBarNotification sbn) {
        if (isOnCall() || mExcludedApps.contains(sbn.getPackageName())) return false;
        for (String packageName : AUTO_BANNED_PACKAGES) {
            if (packageName.equals(sbn.getPackageName())) {
                 Log.i(TAG, "Package name: " + sbn.getPackageName() + " is banned. Not a valid notification.");
                return false;
            }
        }

        return ((sbn.isClearable() || !hideNonClearable)
                && (!mHideLowPriorityNotifications || sbn.getNotification().priority > HIDE_NOTIFICATIONS_BELOW_SCORE)
                && sbn.getNotification().icon != 0);
    }

    /**
     * Determine if we should show notifications or not.
     * @return True if we should show this view.
     */
    protected boolean shouldShowNotificationForPocketMode() {
        if (mPocketMode != POCKET_MODE_OFF) {
            if (mDistanceFar) {
                return true;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * Determine i a call is currently in progress.
     * @return True if a call is in progress.
     */
    protected boolean isOnCall() {
        if (mTM == null)
            mTM = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        return mTM.getCallState() != TelephonyManager.CALL_STATE_IDLE;
    }

    /**
     * Sets {@code sbn} as the current notification inside the ring.
     * @param sbn StatusBarNotification to be placed as the current one.
     * @param updateOthers Set to true to update the overflow notifications.
     */
    protected void setActiveNotification(final StatusBarNotification sbn, final boolean updateOthers) {
        try {
            Context pkgContext = mContext.createPackageContext(sbn.getPackageName(), Context.CONTEXT_RESTRICTED);
            mNotificationDrawable = pkgContext.getResources().getDrawable(sbn.getNotification().icon);
        } catch (NameNotFoundException nnfe) {
            mNotificationDrawable = mContext.getResources().getDrawable(R.drawable.ic_ad_unknown_icon);
        } catch (Resources.NotFoundException nfe) {
            mNotificationDrawable = mContext.getResources().getDrawable(R.drawable.ic_ad_unknown_icon);
        }
        post(new Runnable() {
            @Override
            public void run() {
                StateListDrawable stateListDrawable = new StateListDrawable();
                stateListDrawable.addState(TargetDrawable.STATE_INACTIVE, mNotificationDrawable);
                TargetDrawable centerDrawable = new TargetDrawable(getResources(),stateListDrawable);
                centerDrawable.setScaleX(0.9f);
                centerDrawable.setScaleY(0.9f);
                mGlowPadView.setCenterDrawable(centerDrawable);
                setHandleText(sbn);
                mNotification = sbn;
                updateResources();
                mGlowPadView.invalidate();
                if (updateOthers) updateOtherNotifications();
            }
        });
    }

    /**
     * Inflates the RemoteViews specified by {@code sbn}.  If bigContentView is available it will be
     * used otherwise the standard contentView will be inflated.
     * @param sbn The StatusBarNotification to inflate content from.
     */
    protected void inflateRemoteView(StatusBarNotification sbn) {
        final Notification notification = sbn.getNotification();
        boolean useBigContent = notification.bigContentView != null;
        RemoteViews rv = useBigContent ? notification.bigContentView : notification.contentView;
        if (rv != null) {
            if (mRemoteView != null) mRemoteViewLayout.removeView(mRemoteView);
            if (useBigContent) {
                rv.removeAllViews(com.android.internal.R.id.actions);
                rv.setViewVisibility(com.android.internal.R.id.action_divider, View.GONE);
                mRemoteViewLayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            } else {
                mRemoteViewLayoutParams.height = getResources().getDimensionPixelSize(R.dimen.notification_min_height);
            }
            mRemoteView = rv.apply(mContext, null);
            mRemoteView.setAlpha(0f);
            mRemoteViewLayout.addView(mRemoteView, mRemoteViewLayoutParams);
        }
    }

    /**
     * Sets the text to be displayed around the outside of the ring.
     * @param sbn The StatusBarNotification to get the text from.
     */
    private void setHandleText(StatusBarNotification sbn) {
        if (mDisplayNotificationText) {
            if (!privacyMode) {
                final Notification notificiation = sbn.getNotification();
                CharSequence tickerText = mDisplayNotificationText ? notificiation.tickerText
                        : "";
                if (tickerText == null) {
                    Bundle extras = notificiation.extras;
                    if (extras != null)
                        tickerText = extras.getCharSequence(Notification.EXTRA_TITLE, null);
                }
                mGlowPadView.setHandleText(tickerText != null ? tickerText.toString() : "");
            } else {
                mGlowPadView.setHandleText("Security: notification text disabled");
            }
        } else {
            mGlowPadView.setHandleText("");
        }
    }

    /**
     * Creates a drawable with the required states for the center ring handle
     * @param handle Drawable to use as the base image
     * @return A StateListDrawable with the appropriate states defined.
     */
    private Drawable createLockHandle(Drawable handle) {
        StateListDrawable stateListDrawable = new StateListDrawable();
        stateListDrawable.addState(TargetDrawable.STATE_INACTIVE, handle);
        stateListDrawable.addState(TargetDrawable.STATE_ACTIVE, handle);
        stateListDrawable.addState(TargetDrawable.STATE_FOCUSED, handle);
        return stateListDrawable;
    }

    private SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mPocketMode == POCKET_MODE_OFF) {
                return;
            }
            if (isOnCall()) return;

            long checkTime = System.currentTimeMillis();
            float value = event.values[0];
            if (event.sensor.equals(mProximitySensor)) {
                if (value >= mProximitySensor.getMaximumRange()) {
                    mDistanceFar = true;
                    if (mQuietTime) {
                        if (inQuietHours()) return;
                    }
                    synchronized (this) {
                        if (!mScreenOnState) {
                            if (checkTime >= (mPocketTime + mProximityThreshold)){
                                if (mNotification == null) {
                                    mNotification = getNextAvailableNotification();
                                }
                                if (shouldShowNotificationForPocketMode()) {
                                    showNotification(mNotification, true);
                                    turnScreenOn();
                                }
                            }
                        }
                    }
                } else if (value <= 1.5) {
                    mDistanceFar = false;
                    mPocketTime = System.currentTimeMillis();
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_REDISPLAY_NOTIFICATION.equals(action)) {
                if (mQuietTime) {
                    if (inQuietHours()) return;
                }
                synchronized (this) {
                    if (!mScreenOnState) {
                        if (mNotification == null) {
                            mNotification = getNextAvailableNotification();
                        }
                        if (shouldShowNotificationForPocketMode()) {
                            showNotification(mNotification, true);
                            turnScreenOn();
                        }
                    }
                }
            } else if (ACTION_DISPLAY_TIMEOUT.equals(action)) {
                turnScreenOff();
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenOnState = false;
                onScreenTurnedOff();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenOnState = true;
                cancelRedisplayTimer();
            } else if (Intent.ACTION_KEYGUARD_TARGET.equals(action)) {
                disableProximitySensor();
            }
        }
    };

    /**
     * Restarts the timer for re-displaying notifications.
     */
    private void updateRedisplayTimer() {
        AlarmManager am = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ACTION_REDISPLAY_NOTIFICATION);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.cancel(pi);
        } catch (Exception e) {
        }
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(System.currentTimeMillis() + mRedisplayTimeout);
        am.set(AlarmManager.RTC, time.getTimeInMillis(), pi);
    }

    /**
     * Cancels the timer for re-displaying notifications.
     */
    protected void cancelRedisplayTimer() {
        AlarmManager am = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ACTION_REDISPLAY_NOTIFICATION);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.cancel(pi);
        } catch (Exception e) {
        }
    }

    /**
     * Restarts the timeout timer used to turn the screen off.
     */
    protected void updateTimeoutTimer() {
        AlarmManager am = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ACTION_DISPLAY_TIMEOUT);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.cancel(pi);
        } catch (Exception e) {
        }
        Calendar time = Calendar.getInstance();
        time.setTimeInMillis(System.currentTimeMillis() + mDisplayTimeout);
        am.set(AlarmManager.RTC, time.getTimeInMillis(), pi);
    }

    /**
     * Cancels the timeout timer used to turn the screen off.
     */
    protected void cancelTimeoutTimer() {
        AlarmManager am = (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(ACTION_DISPLAY_TIMEOUT);
        PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            am.cancel(pi);
        } catch (Exception e) {
        }
    }

    /**
     * Check if device is in Quiet Hours in the moment.
     */
    protected boolean inQuietHours() {
        boolean quietHoursEnabled = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.QUIET_HOURS_ENABLED, 0, UserHandle.USER_CURRENT_OR_SELF) != 0;
        if (quietHoursEnabled) {
            int quietHoursStart = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.QUIET_HOURS_START, 0, UserHandle.USER_CURRENT_OR_SELF);
            int quietHoursEnd = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.QUIET_HOURS_END, 0, UserHandle.USER_CURRENT_OR_SELF);
            boolean quietHoursDim = Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.QUIET_HOURS_DIM, 0, UserHandle.USER_CURRENT_OR_SELF) != 0;

            if (quietHoursDim && (quietHoursStart != quietHoursEnd)) {
                Calendar calendar = Calendar.getInstance();
                int minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
                if (quietHoursEnd < quietHoursStart) {
                    return (minutes > quietHoursStart) || (minutes < quietHoursEnd);
                } else {
                    return (minutes > quietHoursStart) && (minutes < quietHoursEnd);
                }
            }
        }
        return false;
    }
}
