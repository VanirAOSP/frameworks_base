/*
 * Copyright (C) 2010 The Android-x86 Open Source Project
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
 *
 * Author: Yi Sun <beyounn@gmail.com>
 */

package com.android.server;

import java.net.UnknownHostException;
import android.net.ethernet.EthernetNative;
import android.net.ethernet.IEthernetManager;
import android.net.ethernet.EthernetManager;
import android.net.ethernet.EthernetStateTracker;
import android.net.ethernet.EthernetDevInfo;
import android.net.ethernet.EthernetStateMachine;
import android.provider.Settings;
import android.util.Slog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemProperties;

/**
 * EthernetService handles remote Ethernet operation requests by implementing
 * the IEthernetManager interface. It also creates a EtherentMonitor to listen
 * for Etherent-related events.
 *
 * @hide
 */
public class EthernetService<syncronized> extends IEthernetManager.Stub {
    private static final String TAG = "EthernetService";
    private static final int ETHERNET_HAS_CONFIG = 1;
    private static final boolean localLOGV = true;

    private final EthernetStateMachine mEthernetStateMachine;

    private int mEthState= EthernetManager.ETHERNET_STATE_UNKNOWN;
    private Context mContext;
    private String[] DevName;
    private int isEnabled ;
    private String mInterfaceName;

    public EthernetService(Context context) {
        mContext = context;
        mInterfaceName =  SystemProperties.get("ethernet.interface", "eth0");
        mEthernetStateMachine = new EthernetStateMachine(mContext, mInterfaceName);
    }

    /**
     * Check if Wi-Fi needs to be enabled and start
     * if needed
     *
     * This function is used only at boot time
     */
    public void checkAndStartEthernet() {
        isEnabled = getPersistedState();
        if (localLOGV == true) Slog.i(TAG, "Ethernet dev enabled " + isEnabled);
        getDeviceNameList();
        setState(isEnabled);

        mEthernetStateMachine.startMonitoring(mContext);

    }


    /**
     * check if the ethernet service has been configured.
     * @return {@code true} if configured {@code false} otherwise
     */
    public boolean isConfigured() {
        final ContentResolver cr = mContext.getContentResolver();
        return (Settings.Secure.getInt(cr, Settings.Secure.ETHERNET_CONF, 0) == ETHERNET_HAS_CONFIG);

    }

    /**
     * Return the saved ethernet configuration
     * @return ethernet interface configuration on success, {@code null} on failure
     */
    public synchronized EthernetDevInfo getSavedConfig() {
        if (!isConfigured())
            return null;

        final ContentResolver cr = mContext.getContentResolver();
        EthernetDevInfo info = new EthernetDevInfo();
        info.setConnectMode(Settings.Secure.getString(cr, Settings.Secure.ETHERNET_MODE));
        info.setIfName(Settings.Secure.getString(cr, Settings.Secure.ETHERNET_IFNAME));
        info.setIpAddress(Settings.Secure.getString(cr, Settings.Secure.ETHERNET_IP));
        info.setDnsAddr(Settings.Secure.getString(cr, Settings.Secure.ETHERNET_DNS));
        info.setNetMask(Settings.Secure.getString(cr, Settings.Secure.ETHERNET_MASK));
        info.setRouteAddr(Settings.Secure.getString(cr, Settings.Secure.ETHERNET_ROUTE));

        return info;
    }

    /**
     * Set the ethernet interface configuration mode
     * @param mode {@code ETHERNET_CONN_MODE_DHCP} for dhcp {@code ETHERNET_CONN_MODE_MANUAL} for manual configure
     */
    public synchronized void setMode(String mode) {
        final ContentResolver cr = mContext.getContentResolver();
        if (DevName != null) {
            Settings.Secure.putString(cr, Settings.Secure.ETHERNET_IFNAME, DevName[0]);
            Settings.Secure.putInt(cr, Settings.Secure.ETHERNET_CONF, 1);
            Settings.Secure.putString(cr, Settings.Secure.ETHERNET_MODE, mode);
        }
    }

    /**
     * update a ethernet interface information
     * @param info  the interface infomation
     */
    public synchronized void updateDevInfo(EthernetDevInfo info) {
        final ContentResolver cr = mContext.getContentResolver();
        Settings.Secure.putInt(cr, Settings.Secure.ETHERNET_CONF, 1);
        Settings.Secure.putString(cr, Settings.Secure.ETHERNET_IFNAME, info.getIfName());
        Settings.Secure.putString(cr, Settings.Secure.ETHERNET_IP, info.getIpAddress());
        Settings.Secure.putString(cr, Settings.Secure.ETHERNET_MODE, info.getConnectMode());
        Settings.Secure.putString(cr, Settings.Secure.ETHERNET_DNS, info.getDnsAddr());
        Settings.Secure.putString(cr, Settings.Secure.ETHERNET_ROUTE, info.getRouteAddr());
        Settings.Secure.putString(cr, Settings.Secure.ETHERNET_MASK, info.getNetMask());
        if (mEthState == EthernetManager.ETHERNET_STATE_ENABLED) {
            try {
                mEthernetStateMachine.resetInterface();
            } catch (UnknownHostException e) {
                Slog.e(TAG, "Wrong ethernet configuration");
            }
        }
    }

    /**
     * get the number of ethernet interfaces in the system
     * @return the number of ethernet interfaces
     */
    public int getTotalInterface() {
        return EthernetNative.getInterfaceCnt();
    }


    private int scanDevice() {
        int i, j;
        if ((i = EthernetNative.getInterfaceCnt()) == 0)
            return 0;

        DevName = new String[i];

        for (j = 0; j < i; j++) {
            DevName[j] = EthernetNative.getInterfaceName(j);
            if (DevName[j] == null)
                break;
            if (localLOGV) Slog.v(TAG, "device " + j + " name " + DevName[j]);
        }

        return i;
    }

    /**
     * get all the ethernet device names
     * @return interface name list on success, {@code null} on failure
     */
    public String[] getDeviceNameList() {
        return (scanDevice() > 0) ? DevName : null;
    }

    private int getPersistedState() {
        final ContentResolver cr = mContext.getContentResolver();
        try {
            return Settings.Secure.getInt(cr, Settings.Secure.ETHERNET_ON);
        } catch (Settings.SettingNotFoundException e) {
            return EthernetManager.ETHERNET_STATE_UNKNOWN;
        }
    }

    private synchronized void persistEnabled(boolean enabled) {
        final ContentResolver cr = mContext.getContentResolver();
        Settings.Secure.putInt(cr, Settings.Secure.ETHERNET_ON, enabled ? EthernetManager.ETHERNET_STATE_ENABLED : EthernetManager.ETHERNET_STATE_DISABLED);
    }

    /**
     * Enable or Disable a ethernet service
     * @param enable {@code true} to enable, {@code false} to disable
     */
    public synchronized void setState(int state) {

        if (mEthState != state) {
            mEthState = state;
            if (state == EthernetManager.ETHERNET_STATE_DISABLED) {
                Slog.e(TAG, "EthernetManager.ETHERNET_STATE_DISABLED");
                persistEnabled(false);
                mEthernetStateMachine.stopInterface(false);
            } else {
                Slog.e(TAG, "EthernetManager.ETHERNET_STATE_ENABLED");
                persistEnabled(true);
                if (!isConfigured()) {
                    // If user did not configure any interfaces yet, pick the first one
                    // and enable it.
                    setMode(EthernetDevInfo.ETHERNET_CONN_MODE_DHCP);
                }
                try {
                    mEthernetStateMachine.resetInterface();
                } catch (UnknownHostException e) {
                    Slog.e(TAG, "Wrong ethernet configuration");
                }
            }
        }
    }

    public void reconnect() {
        mEthernetStateMachine.reconnectCommand();
    }

    /**
     * Get ethernet service state
     * @return the state of the ethernet service
     */
    public int getState( ) {
        return mEthState;
    }


    public void startEthernet() {
    }

    public void stopEthernet() {
    }

}
