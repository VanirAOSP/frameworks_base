package com.android.systemui.quicksettings;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;

import android.view.IWindowManager;
import android.view.WindowManagerGlobal;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;

public class NavbarTile extends QuickSettingsTile {

    public NavbarTile(Context context, QuickSettingsController qsc) {
        super(context, qsc);

        mOnClick = new OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean next = !getWantsNavbar();
                Settings.System.putInt(mContext.getContentResolver(),
                    Settings.System.ENABLE_NAVIGATION_BAR, next ? 1 : 0);
                if (isFlipTilesEnabled()) {
                    flipTile(0);
                }
            }
        };

        mOnLongClick = new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent intent = new Intent();
                intent.setClassName("com.android.settings",
                        "com.android.settings.Settings$NavbarSettingsActivity");
                startSettingsActivity(intent);
                return true;
            }
        };

        qsc.registerObservedContent(Settings.System.getUriFor(Settings.System.ENABLE_NAVIGATION_BAR)
                , this);
    }

    @Override
    public void updateResources() {
        updateTile();
        updateQuickSettings();
    }

    private synchronized void updateTile() {
        if(!getWantsNavbar()){
            mDrawable = R.drawable.ic_qs_navbar_off;
            mLabel = mContext.getString(R.string.quick_settings_navbar_off);
        }else{
            mDrawable = R.drawable.ic_qs_navbar_on;
            mLabel = mContext.getString(R.string.quick_settings_navbar_on);
        }
    }

    @Override
    void onPostCreate() {
        updateTile();
        super.onPostCreate();
    }

    private boolean getWantsNavbar() {
        try {
            final IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
            return wm.wantsNavigationBar();
        } catch (RemoteException e) {
        }
        return false;
    }

    @Override
    public void onChangeUri(ContentResolver resolver, Uri uri) {
        updateResources();
    }
}
