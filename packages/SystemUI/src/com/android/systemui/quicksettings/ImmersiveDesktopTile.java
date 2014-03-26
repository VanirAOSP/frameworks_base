package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class ImmersiveDesktopTile extends QuickSettingsTile {
    private boolean mEnabled = false;

    public ImmersiveDesktopTile(Context context,
            QuickSettingsController qsc, Handler handler) {
        super(context, qsc);

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Change the system setting
                Settings.System.putIntForUser(mContext.getContentResolver(),
                        Settings.System.GLOBAL_IMMERSIVE_MODE_STATE, mEnabled ? 0 : 1,
                        UserHandle.USER_CURRENT);
                mStatusbarService.animateCollapsePanels();
            }
        };

        Uri stateUri = Settings.System.getUriFor(Settings.System.GLOBAL_IMMERSIVE_MODE_STATE);
        qsc.registerObservedContent(stateUri, this);
        Uri styleUri = Settings.System.getUriFor(Settings.System.EXPANDED_DESKTOP);
        qsc.registerObservedContent(styleUri, this);
    }

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
        final ContentResolver resolver = mContext.getContentResolver();

        mEnabled = Settings.System.getIntForUser(resolver,
                Settings.System.GLOBAL_IMMERSIVE_MODE_STATE, 0, UserHandle.USER_CURRENT) == 1;
        boolean expanded = Settings.System.getIntForUser(resolver,
                Settings.System.EXPANDED_DESKTOP, 0, UserHandle.USER_CURRENT) == 1;

        if (!expanded) {
            if (mEnabled) {
                mDrawable = R.drawable.ic_qs_immersive_mode_on;
                mLabel = mContext.getString(R.string.quick_settings_immersive_desktop);
            } else {
                mDrawable = R.drawable.ic_qs_immersive_mode_off;
                mLabel = mContext.getString(R.string.quick_settings_immersive_desktop_off);
            }
        } else {
            if (mEnabled) {
                mDrawable = R.drawable.ic_qs_expanded_desktop_on;
                mLabel = mContext.getString(R.string.quick_settings_expanded_desktop);
            } else {
                mDrawable = R.drawable.ic_qs_expanded_desktop_off;
                mLabel = mContext.getString(R.string.quick_settings_expanded_desktop_off);
            }
        }
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }
}
