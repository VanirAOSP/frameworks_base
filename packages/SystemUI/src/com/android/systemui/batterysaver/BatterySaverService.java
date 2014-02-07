/*
 * Copyright (C) 2014 The OmniRom Project
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
package com.android.systemui.batterysaver;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.IPowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.android.systemui.R;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;
import com.android.systemui.BatteryMeterView.BatteryMeterMode;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.NetworkController.NetworkSignalChangedCallback;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.telephony.Phone;

public class BatterySaverService extends Service implements NetworkSignalChangedCallback, BatteryStateChangeCallback {

    private final String TAG = "BatterySaverService";

    private Handler mHandler;

    private enum State { UNKNOWN, NORMAL, POWER_SAVING };

    private Context mContext;
    private Resources mResources;
    private int mDefaultMode;
    private int mNormalMode;
    private int mPowerSavingMode;
    private ConnectivityManager mCM;
    private TelephonyManager mTM;
    private IPowerManager mPM;
    private WifiManager mWM;
    private State mCurrentState = State.UNKNOWN;
    private boolean mBatterySaverEnabled;
    private boolean mSmartBatteryEnabled;
    private boolean mSmartWifiEnabled;
    private boolean mSmartDataEnabled;
    private boolean mIsScreenOff = false;
    private boolean mPowerSaveWhenScreenOff;
    private boolean mIgnoreWhileLocked;
    private NetworkModeChanger mNetworkModeChanger;
    private int mModeChangeDelay;
    private SettingsObserver mSettingsObserver;
    private long mTrafficBytes;
    private final long TRAFFIC_BYTES_THRESHOLD = 5 * 1024 * 1024; // 5mb

    private boolean mIsAirplaneMode = false;
    private boolean mIsWifiDisabledByService = false;
    private boolean mIsWifiEnabledByUser = false;
    private boolean mWasMobileDataEnabled;
    private boolean mWasWifiEnabled;

    private BatteryController mBatteryController;
    private NetworkController mNetworkController;

    private int mLowBatteryLevel;
    private boolean mBatteryLowEvent = false;

    private boolean mUsbTethered = false;
    private boolean mUsbConnected = false;
    private String[] mUsbRegexs;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        mResources = mContext.getResources();
        mHandler = new Handler();
        mCM = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        mTM = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        mWM = (WifiManager) getSystemService(WIFI_SERVICE);
        mPM = IPowerManager.Stub.asInterface(ServiceManager.getService(Context.POWER_SERVICE));

        mBatteryController = new BatteryController(this);
        mNetworkController = new NetworkController(this);
        mNetworkModeChanger = new NetworkModeChanger(this);

        updateSettings();
        mBatteryController.addStateChangedCallback(this);
        mNetworkController.addNetworkSignalChangedCallback(this);
        mDefaultMode = get2G3G();
        mWasMobileDataEnabled = isMobileDataEnabled();
        mWasWifiEnabled = isWifiEnabled();

        // Register settings observer and set initial preferences
        mSettingsObserver = new SettingsObserver(mHandler);
        mSettingsObserver.observe();

        // Register for Intent broadcasts for...
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.ACTION_TETHER_STATE_CHANGED);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(UsbManager.ACTION_USB_STATE);
        registerReceiver(mBroadcastReceiver, filter);

        Toast.makeText(mContext, mResources.getString(R.string.battery_saver_start), Toast.LENGTH_SHORT).show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.BATTERY_SAVER_OPTION), false, this);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.BATTERY_SAVER_NORMAL_MODE), false, this);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.BATTERY_SAVER_POWER_SAVING_MODE), false, this);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.BATTERY_SAVER_SCREEN_OFF), false, this);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.BATTERY_SAVER_IGNORE_LOCKED), false, this);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.BATTERY_SAVER_MODE_CHANGE_DELAY), false, this);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.BATTERY_SAVER_BATTERY_MODE), false, this);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.BATTERY_SAVER_BATTERY_LEVEL), false, this);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.BATTERY_SAVER_DATA_MODE), false, this);
            resolver.registerContentObserver(Settings.Global.getUriFor(
                    Settings.Global.BATTERY_SAVER_WIFI_MODE), false, this);
        }

        void unobserve() {
            mContext.getContentResolver().unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
            updateSettings();
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri != null && uri.equals(Settings.Global.getUriFor(
                         Settings.Global.BATTERY_SAVER_NORMAL_MODE))) {
                final ContentResolver resolver = mContext.getContentResolver();
                mNormalMode = Settings.Global.getInt(resolver,
                         Settings.Global.BATTERY_SAVER_NORMAL_MODE, get2G3G());
                setNewModeValue(State.NORMAL, mNormalMode);
            } else if (uri != null && uri.equals(Settings.Global.getUriFor(
                         Settings.Global.BATTERY_SAVER_POWER_SAVING_MODE))) {
                final ContentResolver resolver = mContext.getContentResolver();
                mPowerSavingMode = Settings.Global.getInt(resolver,
                         Settings.Global.BATTERY_SAVER_POWER_SAVING_MODE, get2G3G());
                setNewModeValue(State.POWER_SAVING, mPowerSavingMode);
            } else {
                final ContentResolver resolver = mContext.getContentResolver();
                mBatterySaverEnabled = Settings.Global.getInt(resolver,
                        Settings.Global.BATTERY_SAVER_OPTION, 0) != 0;
                mPowerSaveWhenScreenOff = Settings.Global.getInt(resolver,
                        Settings.Global.BATTERY_SAVER_SCREEN_OFF, 1) != 0;
                mIgnoreWhileLocked = Settings.Global.getInt(resolver,
                        Settings.Global.BATTERY_SAVER_IGNORE_LOCKED, 1) != 0;
                mModeChangeDelay = Settings.Global.getInt(resolver,
                        Settings.Global.BATTERY_SAVER_MODE_CHANGE_DELAY, 5);
                mSmartBatteryEnabled = Settings.Global.getInt(resolver,
                        Settings.Global.BATTERY_SAVER_BATTERY_MODE, 0) != 0;
                int lowBatteryLevels = mResources.getInteger(
                        com.android.internal.R.integer.config_lowBatteryWarningLevel);
                mLowBatteryLevel = Settings.Global.getInt(resolver,
                        Settings.Global.BATTERY_SAVER_BATTERY_LEVEL, lowBatteryLevels);
                mSmartDataEnabled = Settings.Global.getInt(resolver,
                        Settings.Global.BATTERY_SAVER_DATA_MODE, 1) != 0;
                mSmartWifiEnabled = Settings.Global.getInt(resolver,
                        Settings.Global.BATTERY_SAVER_WIFI_MODE, 0) != 0;
            }
        }
    }

    private void updateSettings() {
        final ContentResolver resolver = mContext.getContentResolver();
        mBatterySaverEnabled = Settings.Global.getInt(resolver,
                        Settings.Global.BATTERY_SAVER_OPTION, 0) != 0;
        mPowerSaveWhenScreenOff = Settings.Global.getInt(resolver,
                        Settings.Global.BATTERY_SAVER_SCREEN_OFF, 1) == 1;
        mIgnoreWhileLocked = Settings.Global.getInt(resolver,
                        Settings.Global.BATTERY_SAVER_IGNORE_LOCKED, 1) == 1;
        mModeChangeDelay = Settings.Global.getInt(resolver,
                        Settings.Global.BATTERY_SAVER_MODE_CHANGE_DELAY, 5);
        mNormalMode = Settings.Global.getInt(resolver,
                         Settings.Global.BATTERY_SAVER_NORMAL_MODE, get2G3G());
        mPowerSavingMode = Settings.Global.getInt(resolver,
                         Settings.Global.BATTERY_SAVER_POWER_SAVING_MODE, get2G3G());
        mSmartBatteryEnabled = Settings.Global.getInt(resolver,
                         Settings.Global.BATTERY_SAVER_BATTERY_MODE, 0) != 0;
        int lowBatteryLevels = mResources.getInteger(
                         com.android.internal.R.integer.config_lowBatteryWarningLevel);
        mLowBatteryLevel = Settings.Global.getInt(resolver,
                         Settings.Global.BATTERY_SAVER_BATTERY_LEVEL, lowBatteryLevels);
        mSmartDataEnabled = Settings.Global.getInt(resolver,
                         Settings.Global.BATTERY_SAVER_DATA_MODE, 1) != 0;
        mSmartWifiEnabled = Settings.Global.getInt(resolver,
                         Settings.Global.BATTERY_SAVER_WIFI_MODE, 0) != 0;
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                if (mPowerSaveWhenScreenOff) {
                    switchToState(State.POWER_SAVING, true);
                }
                mIsScreenOff = true;
            } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                if (!mIgnoreWhileLocked && !isWifiConnected() && !mBatteryLowEvent) {
                    switchToState(State.NORMAL);
                } else if (!mBatteryLowEvent && mPowerSaveWhenScreenOff && isLockScreenDisabled()) {
                    switchToState(State.NORMAL);
                }
                mIsScreenOff = false;
            } else if (action.equals(Intent.ACTION_USER_PRESENT)) {
                if (mIgnoreWhileLocked && !isWifiConnected() && !mBatteryLowEvent) {
                    switchToState(State.NORMAL);
                }
            } else if (action.equals(UsbManager.ACTION_USB_STATE)) {
                mUsbConnected = intent.getBooleanExtra(UsbManager.USB_CONNECTED, false);
            } else if (action.equals(ConnectivityManager.ACTION_TETHER_STATE_CHANGED)) {
                updateState();
            }
        }
    };

    @Override
    public void onAirplaneModeChanged(boolean enabled) {
        if (!mBatterySaverEnabled) return;
        mIsAirplaneMode = enabled;
    }

    @Override
    public void onBatteryMeterModeChanged(BatteryMeterMode mode) {/*Ignore*/}

    @Override
    public void onBatteryMeterShowPercent(boolean showPercent) {/*Ignore*/}

    @Override
    public void onBatteryLevelChanged(boolean present, int level, boolean pluggedIn, int status) {
        if (!mBatterySaverEnabled) return;
        if (!pluggedIn && (level < mLowBatteryLevel)) {
            mBatteryLowEvent = true;
            if (!mIsScreenOff && !isWifiConnected() && mSmartBatteryEnabled) {
                switchToState(State.POWER_SAVING);
            }
        } else if ((pluggedIn || (level > mLowBatteryLevel))) {
            mBatteryLowEvent = false;
            if (!mIsScreenOff && !isWifiConnected() && mSmartBatteryEnabled) {
                switchToState(State.NORMAL);
            }
        }
    }

    @Override
    public void onMobileDataSignalChanged(
            boolean enabled, int mobileSignalIconId, String signalContentDescription,
            int dataTypeIconId, boolean activityIn, boolean activityOut,
            String dataContentDescription,String enabledDesc) {
    }

    @Override
    public void onWifiSignalChanged(boolean enabled, int wifiSignalIconId,
            boolean activityIn, boolean activityOut,
            String wifiSignalContentDescription, String enabledDesc) {
        if (!mBatterySaverEnabled) return;
        if (mIsWifiDisabledByService) {
            mIsWifiEnabledByUser = isWifiEnabled();
        }
        if (!mBatteryLowEvent && isWifiConnected()) {
            switchToState(State.POWER_SAVING);
        } else if (!mBatteryLowEvent && !isWifiConnected() && !(mIsScreenOff && mPowerSaveWhenScreenOff)) {
            switchToState(State.NORMAL);
        }
    }

    private void updateWifiState() {
        if (mWM == null) return;

        final boolean enable = (mWM.getWifiState() != WifiManager.WIFI_STATE_ENABLED);
        new AsyncTask<Void, Void, Void>() {
               @Override
               protected Void doInBackground(Void... args) {
                   // Disable tethering if enabling Wifi
                   final int wifiApState = mWM.getWifiApState();
                   if (enable && ((wifiApState == WifiManager.WIFI_AP_STATE_ENABLING) ||
                                   (wifiApState == WifiManager.WIFI_AP_STATE_ENABLED))) {
                       mWM.setWifiApEnabled(null, false);
                   }
                   mWM.setWifiEnabled(enable);
                  return null;
               }
        }.execute();
    }

    private void restoreAllState() {
        boolean network = false;
        boolean wifi = false;
        if (!mUsbTethered && !isWifiApEnabled()) {
            if (deviceSupportsMobileData() && mWasMobileDataEnabled && mSmartDataEnabled && (mCM != null)) {
                mCM.setMobileDataEnabled(mWasMobileDataEnabled);
                network = true;
            }
            if (mTM != null) set2G3G(mDefaultMode);
            if (mWasWifiEnabled && mSmartWifiEnabled) {
                updateWifiState();
                wifi = true;
            }
        }
        showToast(network, wifi, mResources);
    }

    private boolean isLockScreenDisabled() {
        LockPatternUtils utils = new LockPatternUtils(mContext);
        utils.setCurrentUser(UserHandle.USER_OWNER);
        return utils.isLockScreenDisabled();
    }

    private boolean deviceSupportsMobileData() {
        return (mCM != null) ? mCM.isNetworkSupported(ConnectivityManager.TYPE_MOBILE) : false;
    }

    private boolean isMobileDataEnabled() {
        if (!deviceSupportsMobileData()) return false;
        return (mCM != null) ? mCM.getMobileDataEnabled() : false;
    }

    private boolean deviceSupportsWifiAp() {
        return (mCM != null) ? (mCM.getTetherableWifiRegexs().length != 0) : false;
    }

    private boolean isWifiConnected() {
        NetworkInfo network = (mCM != null) ? mCM.getNetworkInfo(ConnectivityManager.TYPE_WIFI) : null;
        return network != null && network.isConnected();
    }

    private void updateState() {
        if (mCM == null) return;

        mUsbRegexs = mCM.getTetherableUsbRegexs();

        String[] available = mCM.getTetherableIfaces();
        String[] tethered = mCM.getTetheredIfaces();
        String[] errored = mCM.getTetheringErroredIfaces();
        updateState(available, tethered, errored);
    }

    private void updateState(String[] available, String[] tethered,
            String[] errored) {
        updateUsbState(available, tethered, errored);
    }

    private void updateUsbState(String[] available, String[] tethered,
            String[] errored) {

        mUsbTethered = false;
        for (String s : tethered) {
            for (String regex : mUsbRegexs) {
                if (s.matches(regex)) mUsbTethered = true;
            }
        }

    }

    private boolean isWifiApEnabled() {
        if (mWM == null || !deviceSupportsWifiAp()) return false;

        int state = mWM.getWifiApState();
        switch (state) {
                case WifiManager.WIFI_AP_STATE_ENABLING:
                case WifiManager.WIFI_AP_STATE_ENABLED:
                     return true;
                case WifiManager.WIFI_AP_STATE_DISABLING:
                case WifiManager.WIFI_AP_STATE_DISABLED:
                     return false;
        }
        return false;
    }

    private boolean isWifiEnabled() {
        if (mWM == null) return false;

        int state = mWM.getWifiState();
        switch (state) {
                case WifiManager.WIFI_STATE_ENABLING:
                case WifiManager.WIFI_STATE_ENABLED:
                     return true;
                case WifiManager.WIFI_STATE_DISABLING:
                case WifiManager.WIFI_STATE_DISABLED:
                     return false;
        }
        return false;
    }

    private void switchToState(State newState) {
        switchToState(newState, false);
    }

    private void switchToState(State newState, boolean force) {
        if (mCurrentState == newState && !force) {
            return;
        } else if (!mBatterySaverEnabled || isOnCall()
                   || mUsbTethered || isWifiApEnabled() || mIsAirplaneMode) {
            return;
        }

        int networkMode = get2G3G();
        switch (newState) {
              case NORMAL:
                   networkMode = mNormalMode;
                   break;
              case POWER_SAVING:
                   networkMode = mPowerSavingMode;
                   break;
              default:
                   break;
        }
        mCurrentState = newState;
        mTrafficBytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes();
        mNetworkModeChanger.changeNetworkMode(networkMode);
    }

    private String getNetworkType(int state, Resources r) {
        switch (state) {
            case Phone.NT_MODE_GLOBAL:
                return r.getString(R.string.network_4G);
            case Phone.NT_MODE_GSM_UMTS:
                return r.getString(R.string.network_3G_auto);
            case Phone.NT_MODE_WCDMA_ONLY:
                return r.getString(R.string.network_3G_only);
            case Phone.NT_MODE_EVDO_NO_CDMA:
            case Phone.NT_MODE_CDMA_NO_EVDO:
            case Phone.NT_MODE_GSM_ONLY:
                return r.getString(R.string.network_2G);
            case Phone.NT_MODE_CDMA:
            case Phone.NT_MODE_WCDMA_PREF:
                return r.getString(R.string.network_3G);
        }
        return r.getString(R.string.quick_settings_network_unknown);
    }

    private int get2G3G() {
        if (!deviceSupportsMobileData()) return 0;
        return Settings.Global.getInt(mContext.getContentResolver(),
                    Settings.Global.PREFERRED_NETWORK_MODE, Phone.PREFERRED_NT_MODE);
    }

    private void set2G3G(int network) {
        if (!deviceSupportsMobileData()) return;
        Toast.makeText(mContext,
                  mResources.getString(R.string.battery_saver_change) + " "
                  + getNetworkType(network, mResources), Toast.LENGTH_SHORT).show();
        switch(network) {
            case Phone.NT_MODE_GLOBAL:
                mTM.toggleMobileNetwork(Phone.NT_MODE_GLOBAL);
                break;
            case Phone.NT_MODE_CDMA_NO_EVDO:
            case Phone.NT_MODE_EVDO_NO_CDMA:
                mTM.toggleMobileNetwork(Phone.NT_MODE_CDMA_NO_EVDO);
                break;
            case Phone.NT_MODE_CDMA:
                mTM.toggleMobileNetwork(Phone.NT_MODE_CDMA);
                break;
            case Phone.NT_MODE_GSM_UMTS:
                mTM.toggleMobileNetwork(Phone.NT_MODE_GSM_UMTS);
                break;
            case Phone.NT_MODE_WCDMA_ONLY:
                mTM.toggleMobileNetwork(Phone.NT_MODE_WCDMA_ONLY);
                break;
            case Phone.NT_MODE_GSM_ONLY:
                mTM.toggleMobileNetwork(Phone.NT_MODE_GSM_ONLY);
                break;
            case Phone.NT_MODE_WCDMA_PREF:
                mTM.toggleMobileNetwork(Phone.NT_MODE_WCDMA_PREF);
                break;
        }
    }

    private boolean isOnCall() {
        return mTM.getCallState() != TelephonyManager.CALL_STATE_IDLE;
    }

    private void setNewModeValue(State state, int mode) {
        int currentMode = state == State.NORMAL ? mNormalMode : mPowerSavingMode;
        if (mode != currentMode) {
            if (state == State.NORMAL) {
                mNormalMode = mode;
            } else {
                mPowerSavingMode = mode;
            }
            if (mCurrentState == state) {
                switchToState(state, true);
            }
        }
    }

    private void showToast(boolean network, boolean wifi, Resources r) {
        String what = r.getString(R.string.battery_saver_all);
        if (network && !wifi) {
            what = r.getString(R.string.battery_saver_network);
        } else if (!network && wifi) {
            what = r.getString(R.string.battery_saver_wifi);
        }
        Toast.makeText(mContext, what, Toast.LENGTH_SHORT).show();
    }

    private class NetworkModeChanger implements Runnable {
        private Context mContext;
        private Handler mHandler;
        private int mNextNetworkMode;
        private int mCurrentNetworkMode;

        public NetworkModeChanger(Context context) {
            mContext = context;
            mHandler = new Handler();
            mNextNetworkMode = get2G3G();
            mCurrentNetworkMode = get2G3G();
        }

        @Override
        public void run() {
            if (mNextNetworkMode == get2G3G() && deviceSupportsMobileData()) return;
            final long traffic = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes();
            if ((traffic - mTrafficBytes) > TRAFFIC_BYTES_THRESHOLD) {
                changeNetworkMode(mNextNetworkMode);
                return;
            }

            if (mCurrentState == State.POWER_SAVING) {
                if (deviceSupportsMobileData() && mWasMobileDataEnabled && mSmartDataEnabled && (mCM != null)) {
                    mCM.setMobileDataEnabled(!mWasMobileDataEnabled);
                }
                if (mWasWifiEnabled && mSmartWifiEnabled && !mIsWifiEnabledByUser) {
                    mIsWifiDisabledByService = true;
                    updateWifiState();
                }
            } else if (mCurrentState == State.NORMAL) {
                if (deviceSupportsMobileData() && mWasMobileDataEnabled && mSmartDataEnabled && (mCM != null)) {
                    mCM.setMobileDataEnabled(mWasMobileDataEnabled);
                }
                if (mWasWifiEnabled && mSmartWifiEnabled) {
                    mIsWifiDisabledByService = false;
                    updateWifiState();
                }
            }
            set2G3G(mNextNetworkMode);
            mCurrentNetworkMode = mNextNetworkMode;
        }

        public void changeNetworkMode(int networkMode) {
            mHandler.removeCallbacks(this);
            if ((networkMode == get2G3G() || networkMode == mCurrentNetworkMode) && deviceSupportsMobileData()) return;
            mNextNetworkMode = networkMode;
            if (mModeChangeDelay == 0) {
                run();
            } else {
                mHandler.postDelayed(this, mModeChangeDelay * 1000);
            }
        }
    }

    @Override
    public void onDestroy() {
        Toast.makeText(mContext, mResources.getString(R.string.battery_saver_stop), Toast.LENGTH_SHORT).show();
        restoreAllState();
        if (mSettingsObserver != null) {
            mSettingsObserver.unobserve();
        }
        if (mBroadcastReceiver != null) {
            unregisterReceiver(mBroadcastReceiver);
        }
        if (mNetworkController != null) {
            mNetworkController.unregisterController(mContext);
        }
        if (mBatteryController != null) {
            mBatteryController.unregisterController(mContext);
        }
        super.onDestroy();
    }
}
