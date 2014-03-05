package com.android.systemui.quicksettings;

import android.content.ComponentName;
import android.content.Context;
import android.view.LayoutInflater;
import android.content.Intent;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class PreferencesTile extends QuickSettingsTile {

    private Context mContext;

    public PreferencesTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mContext = context;
        mOnClick = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startSettingsActivity(android.provider.Settings.ACTION_SETTINGS);
            }
        };
    }

    @Override
    public void onFlingRight() {
        super.onFlingRight();
        Intent intent = new Intent();
                intent.setComponent(new ComponentName(
                        "com.android.settings",
                        "com.android.settings.Settings$SystemSettingsActivity"));
                startSettingsActivity(intent);
    }

    @Override
    public void onFlingLeft() {
        super.onFlingLeft();
        Intent intent = new Intent();
                intent.setComponent(new ComponentName(
                        "com.android.settings",
                        "com.android.settings.Settings$QuickSettingsConfigActivity"));
                startSettingsActivity(intent);
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    @Override
    public void updateResources() {
        updateTile();
        super.updateResources();
    }

    private synchronized void updateTile() {
        mDrawable = R.drawable.ic_qs_settings;
        mLabel = mContext.getString(R.string.quick_settings_settings_label);
    }
}
