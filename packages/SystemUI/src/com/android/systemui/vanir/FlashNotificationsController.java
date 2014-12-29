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
import android.hardware.ITorchService;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
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

import com.android.systemui.R;

import java.io.FileWriter;
import java.io.IOException;
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

    ITorchService mTorchService;
    String mFlashDevice;

    TelephonyManager mTM;

    SensorManager mSensorManager;
    Sensor mProximitySensor;

    PowerManager mPm;
    WakeLock mWakeLock;

    // operation stats
    volatile boolean mFlashing = false;
    volatile boolean mLightOn = false;
    volatile boolean mDistanceFar = true;
    boolean mRegistered;
    boolean mAttached;

    // user customizable settings
    private SettingsObserver mSettingsObserver;
    volatile boolean mScreenIsOn = true;
    boolean mAllowWithScreenOn = true;
    boolean mAllowNonClearable;
    boolean mAllowLowPriorityNotifications;
    boolean mUseProximitySensor = false;

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

    public static class InitializationException extends RuntimeException {
        public InitializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Listen to notification events here:
     */
    private final NotificationListenerService mNotificationListener =
            new NotificationListenerService() {

        @Override
        public void onNotificationPosted(final StatusBarNotification sbn,
                final RankingMap rankingMap) {
            if (isOnCall() || (mUseProximitySensor && !mDistanceFar)) return;
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

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                mScreenIsOn = false;
                if (!mAllowWithScreenOn && mUseProximitySensor) {
                    enableProximitySensor();
                }
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                mScreenIsOn = true;
                if (!mAllowWithScreenOn && mUseProximitySensor) {
                    disableProximitySensor();
                }
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
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.FLASH_NOTIFICATIONS_POCKET_MODE), false, this);
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

            mAllowNonClearable = Settings.System.getInt(
                    resolver, Settings.System.FLASH_NOTIFICATIONS_NON_CLEARABLE, 0) == 1;
            mAllowLowPriorityNotifications = Settings.System.getInt(
                    resolver, Settings.System.FLASH_NOTIFICATIONS_LOW_PRIORITY, 0) == 1;
            String excludedApps = Settings.System.getString(
                    resolver, Settings.System.FLASH_NOTIFICATIONS_EXCLUDED_APPS);
            mAllowWithScreenOn = Settings.System.getInt(
                    resolver, Settings.System.FLASH_NOTIFICATIONS_ALLOW_WITH_SCREEN_ON, 1) == 1;
            mUseProximitySensor = Settings.System.getInt(
                    resolver, Settings.System.FLASH_NOTIFICATIONS_POCKET_MODE, 0) == 1;

            if (mUseProximitySensor) {
                mSensorManager = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
                mProximitySensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
                if (mAllowWithScreenOn) enableProximitySensor();
            } else {
                disableProximitySensor();
                mSensorManager = null;
                mProximitySensor = null;
            }

            if (!TextUtils.isEmpty(excludedApps)) {
                String[] appsToExclude = excludedApps.split("\\|");
                mExcludedApps = new HashSet<String>(Arrays.asList(appsToExclude));
            }
        }
    }

    public FlashNotificationsController(Context context) {
        mContext = context;

        IBinder torchBinder = ServiceManager.getService(Context.TORCH_SERVICE);
        mTorchService = ITorchService.Stub.asInterface(torchBinder);
        mFlashDevice = context.getResources().getString(R.string.flashDevice);

        mTM = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mPm = getPowerManagerService(context);
        this.mWakeLock = mPm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Flash");

        mSettingsObserver = new SettingsObserver(mHandler);
    }

    private SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            float value = event.values[0];
            if (event.sensor.equals(mProximitySensor)) {
                if (value >= mProximitySensor.getMaximumRange()) {
                    mDistanceFar = true;
                } else if (value <= 1.5) {
                    mDistanceFar = false;
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    final Runnable mFlashRunnable = new Runnable() {
        private static final int OFF = 0;
        private static final int ON = 1;
        private static final int FLASH_COUNT = 4;
        private static final int FLASH_ON_TIMEOUT = 50;
        private static final int FLASH_OFF_TIMEOUT = 150;

        private FileWriter mFlashDeviceWriter = null;
        private int mFlashTimeout;
        private int mFlashValue;

        public void run() {
            try {
                if (mFlashDeviceWriter == null) {
                    mFlashDeviceWriter = new FileWriter(mFlashDevice);
                }
            } catch (IOException e) {
                throw new InitializationException("Can't open flash device", e);
            }

            if (mFlashDeviceWriter != null) {
                try {
                    mWakeLock.acquire();
                    onStartTorch(-1);

                    for (int i = 0; i < FLASH_COUNT; i++) {
                        mLightOn = !mLightOn;
                        setFlashMode(mLightOn);
                        if (mLightOn) {
                            mFlashTimeout = FLASH_ON_TIMEOUT;
                        } else {
                            mFlashTimeout = FLASH_OFF_TIMEOUT;
                        }
                        if (i != FLASH_COUNT) {
                            try {
                                Thread.sleep(mFlashTimeout);
                            } catch (InterruptedException InternetExplorer) {
                                Log.e(TAG, "Flash timeout exception: ", InternetExplorer);
                            }
                        } else {
                            mFlashValue = OFF;
                        }
                    }
                } finally {
                    try {
                        if (mFlashDeviceWriter != null) {
                            mFlashDeviceWriter.close();
                            mFlashDeviceWriter = null;
                        }
                    } catch (IOException e) {
                        throw new InitializationException("Can't open flash device", e);
                    }
                    if (mWakeLock.isHeld()) {
                        mWakeLock.release();
                    }
                    onStopTorch();
                }
            } else {
                Log.e(TAG, "No flash writer present");
            }

            // Delay subsequent flashes slightly in case of spamming
            mHandler.removeCallbacks(mDelayedReset);
            mHandler.postDelayed(mDelayedReset, 450);
        }
            
        public synchronized void setFlashMode(boolean mode) {
            if (mode) {
                mFlashValue = 1;
            } else {
                mFlashValue = 0;
            }

            try {
                // Write to sysfs only if not already on
                mFlashDeviceWriter.write(String.valueOf(mFlashValue));
                mFlashDeviceWriter.flush();
            } catch (IOException e) {
                throw new InitializationException("Can't open flash device", e);
            }
        }

        private void onStartTorch(int cameraId) {
            boolean result = false;
            try {
                result = mTorchService.onStartingTorch(cameraId);
            } catch (RemoteException e) {
            }
            if (!result) {
                throw new InitializationException("Camera is busy", null);
            }
        }

        private void onStopTorch() {
            try {
                mTorchService.onStopTorch();
            } catch (RemoteException e) {
                // ignore
            }
        }
    };

    final Runnable mDelayedReset = new Runnable() {
        public void run() {
            mHandler.removeCallbacks(mFlashRunnable);
            mFlashing = false;
        }
    };

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

        return ((sbn.isClearable() || mAllowNonClearable)
                && (mAllowLowPriorityNotifications || sbn.getNotification().priority > HIDE_NOTIFICATIONS_BELOW_SCORE));
    }

    protected void enableProximitySensor() {
        if (mProximitySensor != null && !mAttached) {
            mSensorManager.registerListener(mSensorListener, mProximitySensor, SensorManager.SENSOR_DELAY_UI);
            mAttached = true;
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
            mRegistered = true;
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
            mRegistered = false;

            if (mUseProximitySensor) {
                disableProximitySensor();
                mSensorManager = null;
                mProximitySensor = null;
            }
            mAttached = false;

            if (mBroadcastReceiver != null) 
                    mContext.unregisterReceiver(mBroadcastReceiver);
            mSettingsObserver.unobserve();
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
