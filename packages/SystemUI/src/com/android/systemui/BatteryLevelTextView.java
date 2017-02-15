/*
 * Copyright (C) 2014-2016 The CyanogenMod Project
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

package com.android.systemui;

import android.content.Context;
import android.icu.text.NumberFormat;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.tuner.TunerService;

import cyanogenmod.providers.CMSettings;

import com.android.systemui.R;

public class BatteryLevelTextView extends TextView implements
        BatteryController.BatteryStateChangeCallback, TunerService.Tunable {

    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT =
            "cmsystem:" + CMSettings.System.STATUS_BAR_SHOW_BATTERY_PERCENT;
    private static final String STATUS_BAR_BATTERY_STYLE =
            "cmsystem:" + CMSettings.System.STATUS_BAR_BATTERY_STYLE;

    private BatteryController mBatteryController;

    private boolean mRequestedVisibility;

    private int mStyle = BatteryMeterDrawable.BATTERY_STYLE_PORTRAIT;

    public BatteryLevelTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onBatteryLevelChanged(int level, boolean pluggedIn, boolean charging) {
        String percentage = NumberFormat.getPercentInstance().format((double) level / 100.0);
        //if we're in text-only mode, AND we're in the system icons view
        if (mStyle == BatteryMeterDrawable.BATTERY_STYLE_TEXT) {
            //prepend a '+' if the phone is charging
            if (charging && level < 100) {
                percentage = getResources().getString(R.string.battery_level_template_charging, percentage);
            }
        }

        setText(percentage);
    }

    public void setBatteryController(BatteryController batteryController) {
        mBatteryController = batteryController;
        mBatteryController.addStateChangedCallback(this);
        TunerService.get(getContext()).addTunable(this,
                STATUS_BAR_SHOW_BATTERY_PERCENT, STATUS_BAR_BATTERY_STYLE);
    }

    @Override
    public void onPowerSaveChanged(boolean isPowerSave) {
        // Unused
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        TunerService.get(getContext()).removeTunable(this);
        if (mBatteryController != null) {
            mBatteryController.removeStateChangedCallback(this);
        }
    }

    @Override
    public void onTuningChanged(String key, String newValue) {
        switch (key) {
            case STATUS_BAR_SHOW_BATTERY_PERCENT:
                mRequestedVisibility = newValue != null && Integer.parseInt(newValue) == 2;
                setVisibility(mRequestedVisibility ? View.VISIBLE : View.GONE);
                break;
            case STATUS_BAR_BATTERY_STYLE:
                final int value = newValue == null ?
                        BatteryMeterDrawable.BATTERY_STYLE_PORTRAIT : Integer.parseInt(newValue);
                mStyle = value;
                switch (value) {
                    case BatteryMeterDrawable.BATTERY_STYLE_TEXT:
                        setVisibility(View.VISIBLE);
                        break;
                    case BatteryMeterDrawable.BATTERY_STYLE_HIDDEN:
                        setVisibility(View.GONE);
                        break;
                    default:
                        setVisibility(mRequestedVisibility ? View.VISIBLE : View.GONE);
                        break;
                }
                break;
            default:
                break;
        }
    }
}
