package com.android.systemui.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.android.systemui.BatteryMeterView;
import com.android.systemui.R;
import com.android.systemui.BatteryMeterView.BatteryMeterMode;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.policy.BatteryController;
import com.android.systemui.statusbar.policy.BatteryController.BatteryStateChangeCallback;

public class BatteryTile extends QuickSettingsTile implements BatteryStateChangeCallback {
    private BatteryController mController;

    private int mBatteryLevel = 0;
    private boolean mPluggedIn;
    private boolean mPresent;
    private BatteryMeterView mBatteryView;

    public BatteryTile(Context context, QuickSettingsController qsc, BatteryController controller) {
        this(context, qsc, controller, R.layout.quick_settings_tile_battery);
    }

    protected BatteryTile(Context context, QuickSettingsController qsc,
            BatteryController controller, int resourceId) {
        super(context, qsc, resourceId);

        mController = controller;

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startSettingsActivity(Intent.ACTION_POWER_USAGE_SUMMARY);
            }
        };
    }

    @Override
    void onPostCreate() {
        updateTile();
        mBatteryView = getBatteryMeterView();
        mBatteryView.setMode(BatteryMeterMode.BATTERY_METER_ICON_PORTRAIT);
        if (mQsc.isRibbonMode()) {
            boolean showPercent = Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.STATUS_BAR_BATTERY_SHOW_PERCENT, 0) == 1;
            mBatteryView.setShowPercent(showPercent);
        } else {
            mBatteryView.setShowPercent(false);
        }
        mController.addStateChangedCallback(this);
        super.onPostCreate();
    }

    @Override
    public void onDestroy() {
        mController.removeStateChangedCallback(this);
        super.onDestroy();
    }

    @Override
    public void onBatteryLevelChanged(boolean present, int level, boolean pluggedIn, int status) {
        mPresent = present;
        mBatteryLevel = level;
        mPluggedIn = pluggedIn;
        updateResources();
    }

    @Override
    public void onBatteryMeterModeChanged(BatteryMeterMode mode) {
        int batteryStyle = Settings.System.getIntForUser(mContext.getContentResolver(),
                Settings.System.STATUS_BAR_BATTERY, 0, UserHandle.USER_CURRENT);
        switch (batteryStyle) {
            case 2:
                mode = BatteryMeterMode.BATTERY_METER_CIRCLE;
                break;

            case 4: // use default for hidden in statusbar 
                mode = BatteryMeterMode.BATTERY_METER_CIRCLE;
                break;

            case 5:
                mode = BatteryMeterMode.BATTERY_METER_ICON_LANDSCAPE;
                break;

            case 6:
                mode = BatteryMeterMode.BATTERY_METER_TEXT;
                break;

            default:
                break;
        }
        mBatteryView.setMode(mode);
    }

    @Override
    public void onBatteryMeterShowPercent(boolean showPercent) {
        // PowerWidget tile uses the same settings that status bar
        if (mQsc.isRibbonMode()) {
            mBatteryView.setShowPercent(showPercent);
        }
    }

    protected BatteryMeterView getBatteryMeterView() {
        return (BatteryMeterView) mTile.findViewById(R.id.battery);
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        mTile.setVisibility(mPresent ? View.VISIBLE : View.GONE);
        if (mBatteryLevel == 100) {
            mLabel = mContext.getString(R.string.quick_settings_battery_charged_label);
        } else {
            mLabel = mPluggedIn
                ? mContext.getString(R.string.quick_settings_battery_charging_label,
                        mBatteryLevel)
                : mContext.getString(R.string.status_bar_settings_battery_meter_format,
                        mBatteryLevel);
        }
    }

    @Override
    void updateQuickSettings() {
        TextView tv = (TextView) mTile.findViewById(R.id.text);
        if (tv != null) {
            tv.setText(mLabel);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTileTextSize);
            tv.setPadding(0, mTileTextPadding, 0, 0);
        }
    }

}
