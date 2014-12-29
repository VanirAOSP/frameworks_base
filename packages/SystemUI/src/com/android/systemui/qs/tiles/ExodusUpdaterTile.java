/*
 * Copyright (C) 2014 Exodus
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

package com.android.systemui.qs.tiles;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.widget.Toast;

import com.android.internal.statusbar.IStatusBarService;

import com.android.systemui.R;
import com.android.systemui.qs.UsageTracker;
import com.android.systemui.qs.QSTile;
import com.android.systemui.statusbar.policy.HotspotController;

import com.vanir.util.DeviceUtils;

/** Quick settings tile: Hotspot **/
public class ExodusUpdaterTile extends QSTile<QSTile.BooleanState> {

    public ExodusUpdaterTile(Host host) {
        super(host);
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
    }

    @Override
    protected BooleanState newTileState() {
        return new BooleanState();
    }

    @Override
    public void setListening(boolean listening) {
    }

    @Override
    protected void handleClick() {
        if (DeviceUtils.isNetworkAvailable(mContext)) {
            try {
                IStatusBarService.Stub.asInterface(
                        ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).collapsePanels();
            } catch (RemoteException e) {
            }

            Handler mHandler = new Handler();
            mHandler.postDelayed(new Runnable() {
                public void run() {
                    Intent i = new Intent(Intent.ACTION_CHECK_FOR_UPDATES);
                    mContext.sendBroadcast(i);
				}
			}, 1200);
        } else {
            final String cheese = mContext.getString(R.string.update_check_failed);
            Toast.makeText(mContext, cheese, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void handleSecondaryClick() {
        Intent intent = new Intent("android.intent.action.MAIN");
        intent.setComponent(ComponentName.unflattenFromString(
                "com.exodus.updater/com.exodus.updater.UpdatesSettings"));
        intent.addCategory("android.intent.category.LAUNCHER");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.visible = true;
        state.label = mContext.getString(R.string.title_tile_update);
        state.iconId = R.drawable.ic_qs_update;
    }
}
