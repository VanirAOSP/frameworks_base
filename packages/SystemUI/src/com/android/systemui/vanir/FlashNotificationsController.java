/*
 * Copyright (C) 2014 EXODUS
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
import android.service.notification.NotificationListenerService;
import android.service.notification.NotificationListenerService.RankingMap;
import android.service.notification.StatusBarNotification;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

public class FlashNotificationsController {
    private static final String TAG = "FlashNotificationService";
    private static final boolean DEBUG = false;

    protected static final int HIDE_NOTIFICATIONS_BELOW_SCORE = Notification.PRIORITY_LOW;

    Context mContext;
    Handler mHandler = new Handler();

    TelephonyManager mTM;
    PowerManager mPm;

    // flash states
    static boolean mRegistered = false;
    volatile boolean mFlashing = false;
    volatile boolean mLightOn = false;

    // user customizable settings
    private SettingsObserver mSettingsObserver;
    int mFlashCount = 4;
    int mFlashOnTimeout = 50;
    int mFlashOffTimeout = 150;
    volatile boolean mScreenIsOn = true;
    boolean mAllowWithScreenOn = true;
    boolean mHideNonClearable = true;
    boolean mHideLowPriorityNotifications = false;

    private Set<String> mExcludedApps = new HashSet<String>();

    private static final String[] AUTO_BANNED_PACKAGES = new String[] {
        "android",
        "com.android.music",
        "com.andrew.apollo",
        "com.google.android.music",
        "com.android.providers.downloads"
    };

    private PowerManager getPowerManagerService(Context context) {
		if (mPm == null) mPm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
		return mPm;
	}

    /**
     * Listen to notification events here:
     */
    private final NotificationListenerService mNotificationListener =
            new NotificationListenerService() {

        @Override
        public void onNotificationPosted(final StatusBarNotification sbn,
                final RankingMap rankingMap) {
            if (isOnCall()) return;
            if ((mAllowWithScreenOn || !mScreenIsOn) && !mFlashing && isValidNotification(sbn)) {
				mPm.cpuBoost(650000);
                mFlashing = true;
                mHandler.removeCallbacks(mFlashRunnable);
                mHandler.post(mFlashRunnable);
            }
        }

        @Override
        public void onNotificationRemoved(final StatusBarNotification sbn,
                final RankingMap rankingMap) {
        }

        @Override
        public void onNotificationRankingUpdate(final RankingMap rankingMap) {
        }
    };

    final Runnable mFlashRunnable = new Runnable() {
        private final Intent torch = new Intent("com.exodus.flash.TOGGLE_FLASHLIGHT")
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

    final Runnable mDelayedReset = new Runnable() {
        public void run() {
            mFlashing = false;
            mLightOn = false;
        }
    };

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
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
                    Settings.System.FLASH_NOTIFICATIONS_NON_CLEARABLE), false, this);
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.FLASH_NOTIFICATIONS_LOW_PRIORITY), false, this);
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

            mHideNonClearable = Settings.System.getInt(
                    resolver, Settings.System.FLASH_NOTIFICATIONS_NON_CLEARABLE, 1) == 1;
            mHideLowPriorityNotifications = Settings.System.getInt(
                    resolver, Settings.System.FLASH_NOTIFICATIONS_LOW_PRIORITY, 1) == 1;
            String excludedApps = Settings.System.getString(
                    resolver, Settings.System.FLASH_NOTIFICATIONS_EXCLUDED_APPS);
			mAllowWithScreenOn = Settings.System.getInt(
                    resolver, Settings.System.FLASH_NOTIFICATIONS_ALLOW_WITH_SCREEN_ON, 1) == 1;

            if (!TextUtils.isEmpty(excludedApps)) {
                String[] appsToExclude = excludedApps.split("\\|");
                mExcludedApps = new HashSet<String>(Arrays.asList(appsToExclude));
            }
        }
    }

    public FlashNotificationsController(Context context) {
        mContext = context;

        mTM = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mPm = getPowerManagerService(context);

        mSettingsObserver = new SettingsObserver(mHandler);
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

            // Set up the initial notification state.
            try {
                mNotificationListener.registerAsSystemService(mContext,
                        new ComponentName(mContext.getPackageName(), getClass().getCanonicalName()),
                        UserHandle.USER_ALL);
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to register notification listener", e);
            }

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(Intent.ACTION_SCREEN_ON);
            mContext.registerReceiver(mBroadcastReceiver, filter);

            mSettingsObserver.observe();
        }
    }

    public void unregisterListenerService() {
        if (mRegistered) {
            if (mNotificationListener !=  null) {
                try {
                    mNotificationListener.unregisterAsSystemService();
                } catch (RemoteException e) {
                    // Ignore.
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
}

    
