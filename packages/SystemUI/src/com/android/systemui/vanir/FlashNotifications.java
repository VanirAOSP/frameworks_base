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

import android.app.INotificationManager;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.INotificationListener;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import com.android.internal.util.cm.TorchConstants;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class FlashNotifications {
    private static final String TAG = "FlashNotifications";
    private static final boolean DEBUG = false;

    protected static final int HIDE_NOTIFICATIONS_BELOW_SCORE = Notification.PRIORITY_LOW;

    Context mContext;
    Handler mHandler = new Handler();

    TelephonyManager mTM;
    INotificationManager mNM;
    INotificationListenerWrapper mNotificationListener;

    // flash states
    static boolean mRegistered = false;
    volatile boolean mFlashing = false;
    volatile boolean mLightOn = false;
    boolean mGlobalTorchOn = false;

    // user customizable settings
    private SettingsObserver mSettingsObserver;
    boolean mEnabled = false;
    boolean mQuietTime = false;
    int mFlashCount = 4;
    int mFlashOnTimeout = 50;
    int mFlashOffTimeout = 150;
    volatile boolean mScreenIsOn = true;
    boolean mAllowWithScreenOn = true;
    boolean mHideNonClearable = true;
    boolean mHideLowPriorityNotifications = false;

    private Set<String> mExcludedApps = new HashSet<String>();

    private static final String[] AUTO_BANNED_PACKAGES = new String[] {
    //    "android",   unblock to use for for testing
        "com.android.music",
        "net.cactii.flash2",
        "com.andrew.apollo",
        "com.google.android.music",
        "com.android.providers.downloads"
    };

    /**
     * Listen to notification events here:
     */
    private class INotificationListenerWrapper extends INotificationListener.Stub {
        @Override
        public void onNotificationPosted(final StatusBarNotification sbn) {
            if (isOnCall() || !mEnabled || (mQuietTime && inQuietHours())) return;
            if ((mAllowWithScreenOn || !mScreenIsOn) && !mFlashing && !mGlobalTorchOn && isValidNotification(sbn)) {
                mFlashing = true;
                mHandler.removeCallbacks(mFlashRunnable);
                mHandler.post(mFlashRunnable);
            }
        }
        @Override public void onNotificationRemoved(final StatusBarNotification sbn) {
            // not used yet
        }
    }

    Runnable mFlashRunnable = new Runnable() {
        private final Intent torch = new Intent(TorchConstants.ACTION_TOGGLE_STATE)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra("flash_notification", true);

        public void run() {
            int flashTimeout;
            for (int i = 0; i < mFlashCount; i++) {
                mLightOn = !mLightOn;
                mContext.sendBroadcast(torch);
                if (mLightOn) {
                    flashTimeout = mFlashOnTimeout;
                } else {
                    flashTimeout = mFlashOffTimeout;
                }
                try {
                    Thread.sleep(flashTimeout);
                } catch (InterruptedException InternetExplorer) {
                    Log.e(TAG, "Flash timeout exception: ", InternetExplorer);
                }
            }
            // Delay subsequent flashes slightly in case of spamming
            mHandler.postDelayed(mDelayedReset, 350);
        }
    };

    Runnable mDelayedReset = new Runnable() {
        public void run() {
            mFlashing = false;
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TorchConstants.ACTION_STATE_CHANGED.equals(action)) {
                mGlobalTorchOn = intent.getIntExtra(TorchConstants.EXTRA_CURRENT_STATE, 0) != 0;
            } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenIsOn = false;
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenIsOn = true;
            }
        }
    };

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        private void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS_QUIET_HOURS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.LOCKSCREEN_NOTIFICATIONS_HIDE_NON_CLEARABLE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.ACTIVE_NOTIFICATIONS_HIDE_LOW_PRIORITY), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.FLASH_NOTIFICATIONS_EXCLUDED_APPS), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
					Settings.System.FLASH_NOTIFICATIONS_ALLOW_WITH_SCREEN_ON), false, this);
            update();
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            update();
        }

        private void update() {
            ContentResolver resolver = mContext.getContentResolver();

            mEnabled = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS, 0) == 1;
            mQuietTime = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS_QUIET_HOURS, 0) == 1;
            mHideNonClearable = Settings.System.getInt(
                    resolver, Settings.System.LOCKSCREEN_NOTIFICATIONS_HIDE_NON_CLEARABLE, 1) == 1;
            mHideLowPriorityNotifications = Settings.System.getInt(
                    resolver, Settings.System.ACTIVE_NOTIFICATIONS_HIDE_LOW_PRIORITY, 1) == 1;
            String excludedApps = Settings.System.getString(
                    resolver, Settings.System.FLASH_NOTIFICATIONS_EXCLUDED_APPS);
			mAllowWithScreenOn = Settings.System.getInt(
                    resolver, Settings.System.FLASH_NOTIFICATIONS_ALLOW_WITH_SCREEN_ON, 1) == 1;

        // not yet...
        //    mFlashCount = Settings.System.getInt(
        //            resolver, Settings.System.FLASH_NOTIFICATIONS_FLASH_COUNT, 4);
        //    mFlashOnTimeout = Settings.System.getInt(
        //            resolver, Settings.System.FLASH_NOTIFICATIONS_FLASH_ON_TIMEOUT, 50);
        //    mFlashOffTimeout = Settings.System.getInt(
        //            resolver, Settings.System.FLASH_NOTIFICATIONS_FLASH_OFF_TIMEOUT, 150);

            if (!TextUtils.isEmpty(excludedApps)) {
                String[] appsToExclude = excludedApps.split("\\|");
                mExcludedApps = new HashSet<String>(Arrays.asList(appsToExclude));
            }
        }
    }

    public FlashNotifications(Context context) {
        mContext = context;

        mNM = INotificationManager.Stub.asInterface(
                    ServiceManager.getService(Context.NOTIFICATION_SERVICE));
        mNotificationListener = new INotificationListenerWrapper();
        mTM = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
    }


    /**
     * Determine if a given notification should be used.
     * @param sbn StatusBarNotification to check.
     * @return True if it should be used, false otherwise.
     */
    protected boolean isValidNotification(StatusBarNotification sbn) {
        for (String packageName : AUTO_BANNED_PACKAGES) {
            if (packageName.equals(sbn.getPackageName())) {
                 Log.i(TAG, "Package name: " + sbn.getPackageName() + " is banned. Not a valid notification.");
                return false;
            }
        }
        if  (mExcludedApps.contains(sbn.getPackageName())) return false;

        return ((sbn.isClearable() || !mHideNonClearable)
                && (!mHideLowPriorityNotifications || sbn.getNotification().priority > HIDE_NOTIFICATIONS_BELOW_SCORE));
    }

    public void registerListenerService() {
        if (!mRegistered) {
            ComponentName cn = new ComponentName("android", "");
            try {
                mNM.registerListener(mNotificationListener, cn, UserHandle.USER_ALL);
            } catch (RemoteException e) {
                Log.e(TAG, "registerNotificationListener()", e);
            }

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(TorchConstants.ACTION_STATE_CHANGED);
            mContext.registerReceiver(mBroadcastReceiver, filter);

            mSettingsObserver = new SettingsObserver(mHandler);
            mSettingsObserver.observe();
        }
    }

    public void unregisterListenerService() {
        if (mRegistered) {
            if (mNotificationListener !=  null) {
                try {
                    mNM.unregisterListener(mNotificationListener, UserHandle.USER_ALL);
                } catch (RemoteException e) {
                    Log.e(TAG, "unregisterNotificationListener()", e);
                }
            }
            if (mBroadcastReceiver != null) mContext.unregisterReceiver(mBroadcastReceiver);
            mSettingsObserver.unobserve();
            mRegistered = false;
        }
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

    
