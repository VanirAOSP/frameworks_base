package com.android.systemui.quicksettings;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;

import com.android.internal.statusbar.IStatusBarService;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsController;

public class UpdateTile extends QuickSettingsTile {

    public UpdateTile(Context context, 
            QuickSettingsController qsc) {
        super(context, qsc);

        final Context mContext = context;

        mOnClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isOnline(mContext)) {
                    try {
                        IStatusBarService.Stub.asInterface(
                            ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).expandNotificationsPanel();
                    } catch (RemoteException e) {
                    }

                    Intent i = new Intent(Intent.ACTION_CHECK_FOR_UPDATES);
                    mContext.sendBroadcast(i);
                } else {
                    mQsc.mBar.collapseAllPanels(true);  // the toast will not show above the expanded statusbar
                    final String cheese = mContext.getString(R.string.update_check_failed);
                    Toast.makeText(mContext, cheese, Toast.LENGTH_SHORT).show();
                }
            }
        };

        mOnLongClick = new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                mStatusbarService.animateCollapsePanels();
                Intent intent = new Intent("android.intent.action.MAIN");
                intent.setComponent(ComponentName.unflattenFromString(
                    "com.vanir.updater/com.vanir.updater.UpdatesSettings"));
                intent.addCategory("android.intent.category.LAUNCHER");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
                return true;
            }
        };
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
        mDrawable = R.drawable.ic_qs_update;
        mLabel = mContext.getString(R.string.title_tile_update); 
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        updateResources();
    }

    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnected()) {
            return true;
        }
        return false;
    }
}
