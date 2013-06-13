/*
 * Copyright (C) 2010 The Android Open Source Project
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

import java.util.ArrayList;

import android.bluetooth.BluetoothAdapter.BluetoothStateChangeCallback;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.BatteryManager;
import android.os.Handler;
import android.provider.Settings;
import android.util.Slog;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;

    public class BatteryController extends BroadcastReceiver {
    private static final String TAG = "StatusBar.BatteryController";

    private static Context mContext;
    private static ArrayList<ImageView> mIconViews = new ArrayList<ImageView>();
    private static ArrayList<TextView> mLabelViews = new ArrayList<TextView>();

    private static final int BATTERY_STYLE_NORMAL         = 0;
    private static final int BATTERY_STYLE_PERCENT        = 1;

    /***
     * BATTERY_STYLE_CIRCLE* cannot be handled in this controller, since we cannot get views from
     * statusbar here. Yet it is listed for completion and not to confuse at future updates
     * See CircleBattery.java for more info
     *
     * set to public to be reused by CircleBattery
     */
    public static final int BATTERY_STYLE_CIRCLE         = 2;
    public static final int BATTERY_STYLE_CIRCLE_PERCENT = 3;
    private static final int BATTERY_STYLE_GONE          = 4;
    private static final int BATTERY_STYLE_GEAR          = 5;

    private static final int BATTERY_ICON_STYLE_NORMAL      = R.drawable.stat_sys_battery;
    private static final int BATTERY_ICON_STYLE_CHARGE      = R.drawable.stat_sys_battery_charge;
    private static final int BATTERY_ICON_STYLE_NORMAL_MIN  = R.drawable.stat_sys_battery_min;
    private static final int BATTERY_ICON_STYLE_CHARGE_MIN  = R.drawable.stat_sys_battery_charge_min;
    private static final int BATTERY_ICON_STYLE_NORMAL_GEAR = R.drawable.stat_sys_battery_gear;
    private static final int BATTERY_ICON_STYLE_CHARGE_GEAR = R.drawable.stat_sys_battery_gear_charge;

    private static final int BATTERY_TEXT_STYLE_NORMAL  = R.string.status_bar_settings_battery_meter_format;
    private static final int BATTERY_TEXT_STYLE_MIN     = R.string.status_bar_settings_battery_meter_min_format;

    private static boolean mBatteryPlugged = false;
    private static int mBatteryStyle;
    private static int mBatteryLevel = 0;
    private static int mBatteryIcon = BATTERY_ICON_STYLE_NORMAL;
    private static SettingsObserver mSettingsObserver;

    private static Handler mHandler;

    private static class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(
                    Settings.System.STATUS_BAR_BATTERY), false, this);
        }

        @Override public void onChange(boolean selfChange) {
            updateSettings();
        }
    }

    private static ArrayList<BatteryStateChangeCallback> mChangeCallbacks =
            new ArrayList<BatteryStateChangeCallback>();

    public interface BatteryStateChangeCallback {
        public void onBatteryLevelChanged(int level, boolean pluggedIn);
    }

    public BatteryController(Context context) {
        mContext = context;
        mHandler = new Handler();

        if (mSettingsObserver == null) {
            mSettingsObserver = new SettingsObserver(mHandler);
            mSettingsObserver.observe();

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_BATTERY_CHANGED);
            context.registerReceiver(this, filter);
        }
        updateSettings();
    }

    public static void addIconView(ImageView v) {
        mIconViews.add(v);
    }

    public static void addLabelView(TextView v) {
        mLabelViews.add(v);
    }

    public static void addStateChangedCallback(BatteryStateChangeCallback cb) {		
        mChangeCallbacks.add(cb);
        // trigger initial update
        cb.onBatteryLevelChanged(mBatteryLevel, isBatteryStatusCharging());
    }

    public void onReceive(Context context, Intent intent) {
        if (mContext == null)
            mContext = context;
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
            mBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            mBatteryPlugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            updateViews(); 

    protected void updateViews() {
        if (mUiController) {
            int N = mIconViews.size();
            for (int i=0; i<N; i++) {
                ImageView v = mIconViews.get(i);
                v.setImageLevel(mBatteryLevel);
                v.setContentDescription(mContext.getString(R.string.accessibility_battery_level,
                        mBatteryLevel));
            }
            N = mLabelViews.size();
            for (int i=0; i<N; i++) {
                TextView v = mLabelViews.get(i);
                if (v != null)
                v.setText(mContext.getString(BATTERY_TEXT_STYLE_MIN,
                        mBatteryLevel));
            }
            for (BatteryStateChangeCallback cb : mChangeCallbacks) {
                cb.onBatteryLevelChanged(level, mBatteryPlugged);
            }
            for (BatteryStateChangeCallback cb : mChangeCallbacks) {
            	cb.onBatteryLevelChanged(mBatteryLevel, isBatteryStatusCharging());
        }
    }

    private static void updateBattery() {
        int mIcon = View.GONE;
        int mText = View.GONE;
        int mIconStyle = BATTERY_ICON_STYLE_NORMAL;

        if (mBatteryStyle == BATTERY_STYLE_NORMAL) {
            mIcon = (View.VISIBLE);
            mIconStyle = mBatteryPlugged ? BATTERY_ICON_STYLE_CHARGE
                    : BATTERY_ICON_STYLE_NORMAL;
        } else if (mBatteryStyle == BATTERY_STYLE_PERCENT) {
            mIcon = (View.VISIBLE);
            mText = (View.VISIBLE);
            mIconStyle = mBatteryPlugged ? BATTERY_ICON_STYLE_CHARGE_MIN
                    : BATTERY_ICON_STYLE_NORMAL_MIN;
        } else if (mBatteryStyle == 5) {
            mIcon = (View.VISIBLE);
            mIconStyle = mBatteryPlugged ? BATTERY_ICON_STYLE_CHARGE_GEAR
                    : BATTERY_ICON_STYLE_NORMAL_GEAR;
        }

        int N = mIconViews.size();
        for (int i=0; i<N; i++) {
            ImageView v = mIconViews.get(i);
            v.setVisibility(mIcon);
            v.setImageResource(mIconStyle);
        }
        N = mLabelViews.size();
        for (int i=0; i<N; i++) {
            TextView v = mLabelViews.get(i);
            if (v != null) v.setVisibility(mText);
        }
    }

    private static void updateSettings() {
        ContentResolver resolver = mContext.getContentResolver();

        mBatteryStyle = (Settings.System.getInt(resolver,
                Settings.System.STATUS_BAR_BATTERY, 0));
        updateBattery();
    }
}
