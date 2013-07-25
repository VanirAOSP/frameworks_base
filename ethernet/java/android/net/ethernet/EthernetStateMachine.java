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

package android.net.ethernet;

import java.net.UnknownHostException;

import static android.net.ethernet.EthernetManager.ETHERNET_STATE_DISABLED;
import static android.net.ethernet.EthernetManager.ETHERNET_STATE_ENABLED;
import static android.net.ethernet.EthernetManager.ETHERNET_STATE_UNKNOWN;

import android.os.IBinder;
import android.os.INetworkManagementService;
import android.os.ServiceManager;
import android.os.WorkSource;

import android.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkStateTracker;
import android.net.NetworkUtils;
import android.net.ProxyProperties;
import android.net.RouteInfo;
import android.net.LinkAddress;
import android.net.DhcpInfo;
import android.net.DhcpInfoInternal;
import android.net.DhcpStateMachine;
import android.net.LinkCapabilities;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.SystemProperties;
import android.util.*;
import java.util.Iterator;

import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

import android.net.DnsPinger;
import java.util.concurrent.atomic.AtomicBoolean;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class EthernetStateMachine extends StateMachine {

    private static final String TAG = "EthernetStateMachine";
    private static final String NETWORKTYPE = "ETHERNET";
    private static final boolean DBG = true;

    public static final int EVENT_DHCP_START                        = 0;
    public static final int EVENT_INTERFACE_CONFIGURATION_SUCCEEDED = 1;
    public static final int EVENT_INTERFACE_CONFIGURATION_FAILED    = 2;
    public static final int EVENT_HW_CONNECTED                      = 3;
    public static final int EVENT_HW_DISCONNECTED                   = 4;
    public static final int EVENT_HW_PHYCONNECTED                   = 5;
    private static final int NOTIFY_ID                              = 6;
    private static final boolean localLOGV = true;


    private EthernetManager mEthernetManager;

    private INetworkManagementService mNwService;
    private ConnectivityManager mCm;

    private DhcpHandler mDhcpTarget;
    private Handler mTrackerTarget;
    private String mInterfaceName;

    private LinkCapabilities mLinkCapabilities;

    private Context mContext;

    private DhcpInfo mDhcpInfo;
    private DhcpInfoInternal mDhcpInfoInternal;
    private String[] sDnsPropNames;
    private NetworkInfo mNetworkInfo;
    private DhcpStateMachine mDhcpStateMachine;

    private boolean mStackConnected;
    private boolean mHWConnected;
    private boolean mInterfaceStopped;
    private boolean mStartingDhcp;

    private static final int DEFAULT_MAX_DHCP_RETRIES = 9;


    /**
     * One of  {@link EthernetManager#ETHERNET_STATE_DISABLED},
     *         {@link EthernetManager#ETHERNET_STATE_ENABLED},
     *         {@link EthernetManager#ETHERNET_STATE_UNKNOWN}
     *
     */
    private final AtomicInteger mEthernetState = new AtomicInteger(ETHERNET_STATE_DISABLED);

    /**
     * Keep track of whether ETHERNET is running.
     */
    private boolean mIsRunning = false;

    /**
     * Keep track of whether we last told the battery stats we had started.
     */
    private boolean mReportedRunning = false;

    /**
     * Most recently set source of starting ETHERNET.
     */
    private final WorkSource mRunningEthernetUids = new WorkSource();

    /**
     * The last reported UIDs that were responsible for starting ETHERNET.
     */
    private final WorkSource mLastRunningEthernetUids = new WorkSource();

    private boolean mNextEthernetActionExplicit = false;
    private int mLastExplicitNetworkId;
    private long mLastNetworkChoiceTime;
    private static final long EXPLICIT_CONNECT_ALLOWED_DELAY_MS = 2 * 60 * 1000;


    public EthernetStateMachine(Context context, String ethInterface) {
        super(TAG);

        mDhcpInfo = new DhcpInfo();

        mContext = context;
        mInterfaceName = ethInterface;

        mNetworkInfo = new NetworkInfo(ConnectivityManager.TYPE_ETHERNET, 0, NETWORKTYPE, "");

        IBinder b = ServiceManager.getService(Context.NETWORKMANAGEMENT_SERVICE);
        mNwService = INetworkManagementService.Stub.asInterface(b);

        HandlerThread dhcpThread = new HandlerThread("DHCP Handler Thread");
        dhcpThread.start();
        mDhcpTarget = new DhcpHandler(dhcpThread.getLooper());

        mNetworkInfo.setIsAvailable(false);

        if (DBG) setDbg(true);

        //start the state machine
//        start();
    }


    public void setEthernetEnabled(boolean enable) {
    }

    public int syncGetEthernetState() {
        return mEthernetState.get();
    }

    public String syncGetEthernetStateByName() {
        switch (mEthernetState.get()) {
            case ETHERNET_STATE_DISABLED:
                return "disabled";
            case ETHERNET_STATE_ENABLED:
                return "enabled";
            case ETHERNET_STATE_UNKNOWN:
                return "unknown state";
            default:
                return "[invalid state]";
        }
    }

    public DhcpInfo syncGetDhcpInfo() {
        synchronized (mDhcpInfoInternal) {
            return mDhcpInfoInternal.makeDhcpInfo();
        }
    }


    /*********************************************************
     * Internal private functions
     ********************************************************/

    private void checkAndSetConnectivityInstance() {
        if (mCm == null) {
            mCm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        }
    }


    /**
     * Fetch LinkProperties for the network
     */
    public void startMonitoring(Context context) {
        mContext = context;

        Slog.v(TAG,"start to monitor the ethernet devices");
        mEthernetManager = (EthernetManager)mContext.getSystemService(Context.ETHERNET_SERVICE);
        int state = mEthernetManager.getState();
        if (state != mEthernetManager.ETHERNET_STATE_DISABLED) {
            if (state == mEthernetManager.ETHERNET_STATE_UNKNOWN) {
                // maybe this is the first time we run, enable it if ethernet devices exist
                mEthernetManager.setEnabled(mEthernetManager.getDeviceNameList() != null);
            } else {
                try {
                    Slog.e(TAG, "startMonitoring resetInterface()");
                    resetInterface();
                } catch (UnknownHostException e) {
                    Slog.e(TAG, "Wrong ethernet configuration");
                }
            }
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(EthernetManager.NETWORK_STATE_CHANGED_ACTION);
    }

    private boolean configureInterface(EthernetDevInfo info) throws UnknownHostException {

        LinkProperties linkProperties = getLinkProperties();

        mStackConnected = false;
        mHWConnected = false;
        mInterfaceStopped = false;
        mStartingDhcp = true;
        if (info.getConnectMode().equals(EthernetDevInfo.ETHERNET_CONN_MODE_DHCP)) {
            if (localLOGV) Slog.i(TAG, "trigger dhcp for device " + info.getIfName());
            sDnsPropNames = new String[] {
                "dhcp." + mInterfaceName + ".dns1",
                "dhcp." + mInterfaceName + ".dns2"
             };

            mDhcpTarget.sendEmptyMessage(EVENT_DHCP_START);
        } else {
            int event;
            sDnsPropNames = new String[] {
                "net." + mInterfaceName + ".dns1",
                "net." + mInterfaceName + ".dns2"
             };

            mDhcpInfo.ipAddress = lookupHost(info.getIpAddress());
            mDhcpInfo.gateway = lookupHost(info.getRouteAddr());
            mDhcpInfo.netmask = lookupHost(info.getNetMask());
            mDhcpInfo.dns1 = lookupHost(info.getDnsAddr());
            mDhcpInfo.dns2 = 0;

            NetworkUtils.resetConnections(info.getIfName(), NetworkUtils.RESET_ALL_ADDRESSES);

            if (NetworkUtils.configureInterface(info.getIfName(), mDhcpInfo)) {
                event = EVENT_INTERFACE_CONFIGURATION_SUCCEEDED;
                SystemProperties.set("net.dns1", info.getDnsAddr());
                SystemProperties.set("net." + info.getIfName() + ".dns1", info.getDnsAddr());
                SystemProperties.set("net." + info.getIfName() + ".dns2", "0.0.0.0");
                if (localLOGV) Slog.v(TAG, "Static IP configuration succeeded");
            } else {
                event = EVENT_INTERFACE_CONFIGURATION_FAILED;
                if (localLOGV) Slog.w(TAG, "Static IP configuration failed");
            }
//            sendMessage(event);
        }
        return true;
    }


    /**
     * reset ethernet interface
     * @return true
     * @throws UnknownHostException
     */
    public boolean resetInterface()  throws UnknownHostException {
        /*
         * This will guide us to enabled the enabled device
         */
        if (mEthernetManager != null) {
            EthernetDevInfo info = mEthernetManager.getSavedConfig();
            if (info != null && mEthernetManager.isConfigured()) {
                synchronized (this) {
                    mInterfaceName = info.getIfName();
                    Slog.i(TAG, "reset device " + mInterfaceName);
                    NetworkUtils.resetConnections(mInterfaceName, NetworkUtils.RESET_ALL_ADDRESSES);

                     // Stop DHCP
                    if (mDhcpTarget != null) {
                       mDhcpTarget.removeMessages(EVENT_DHCP_START);
                    }

                    if (!NetworkUtils.stopDhcp(mInterfaceName)) {
                        Slog.w(TAG, "Could not stop DHCP");
                    }
                    configureInterface(info);
                }
            }
        }
        return true;
    }

    /**
     * Stop etherent interface
     * @param suspend {@code false} disable the interface {@code true} only reset the connection without disable the interface
     * @return true
     */
    public boolean stopInterface(boolean suspend) {
        if (mEthernetManager != null) {
            EthernetDevInfo info = mEthernetManager.getSavedConfig();
            if (info != null && mEthernetManager.isConfigured()) {
                synchronized (mDhcpTarget) {
                    mInterfaceStopped = true;
                    Slog.i(TAG, "stop dhcp and interface");
                    mDhcpTarget.removeMessages(EVENT_DHCP_START);
                    String ifname = info.getIfName();

                    if (!NetworkUtils.stopDhcp(ifname)) {
                        Slog.w(TAG, "Could not stop DHCP");
                    }
                    NetworkUtils.resetConnections(ifname, NetworkUtils.RESET_ALL_ADDRESSES);
                    if (!suspend)
                        NetworkUtils.disableInterface(ifname);
                }
            }
        }
        return true;
    }


    private void postNotification(int event) {
        String ns = Context.NOTIFICATION_SERVICE;
        Intent intent = new Intent(EthernetManager.ETHERNET_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(EthernetManager.EXTRA_ETHERNET_STATE, event);
        mContext.sendStickyBroadcast(intent);
    }

    private void setState(boolean state, int event) {
        if (mNetworkInfo.isConnected() != state) {
            if (state) {
                mNetworkInfo.setDetailedState(DetailedState.CONNECTED, null, null);
            } else {
                mNetworkInfo.setDetailedState(DetailedState.DISCONNECTED, null, null);
                stopInterface(true);
            }
            mNetworkInfo.setIsAvailable(state);
            postNotification(event);
        }
    }

    public void handleMessage(Message msg) {

        synchronized (this) {
            switch (msg.what) {
            case EVENT_INTERFACE_CONFIGURATION_SUCCEEDED:
                if (localLOGV) Slog.i(TAG, "received configured succeeded, stack=" + mStackConnected + " HW=" + mHWConnected);
                mStackConnected = true;
                if (mHWConnected)
                    setState(true, msg.what);
                break;
            case EVENT_INTERFACE_CONFIGURATION_FAILED:
                mStackConnected = false;
                //start to retry ?
                break;
            case EVENT_HW_CONNECTED:
                if (localLOGV) Slog.i(TAG, "received HW connected, stack=" + mStackConnected + " HW=" + mHWConnected);
                mHWConnected = true;
                if (mStackConnected)
                    setState(true, msg.what);
                break;
            case EVENT_HW_DISCONNECTED:
                if (localLOGV) Slog.i(TAG, "received disconnected events, stack=" + mStackConnected + " HW=" + mHWConnected);
                setState(mHWConnected = false, msg.what);
                break;
            case EVENT_HW_PHYCONNECTED:
                if (localLOGV) Slog.i(TAG, "interface up event, kick off connection request");
                if (!mStartingDhcp) {
                    int state = mEthernetManager.getState();
                    if (state != mEthernetManager.ETHERNET_STATE_DISABLED) {
                        EthernetDevInfo info = mEthernetManager.getSavedConfig();
                        if (info != null && mEthernetManager.isConfigured()) {
                            try {
                                configureInterface(info);
                            } catch (UnknownHostException e) {
                                 // TODO Auto-generated catch block
                                 //e.printStackTrace();
                                 Slog.e(TAG, "Cannot configure interface");
                            }
                        }
                    }
                }
                break;
            }
        }
    }

    private class DhcpHandler extends Handler {
         public DhcpHandler(Looper looper) {
             super(looper);
         }

         public void handleMessage(Message msg) {
             int event;

             switch (msg.what) {
                 case EVENT_DHCP_START:
                     synchronized (mDhcpTarget) {
                         if (!mInterfaceStopped) {
                             Slog.d(TAG, "DhcpHandler: DHCP request started");
                             DhcpInfoInternal dhcpInfoInternal = new DhcpInfoInternal();
                             if (NetworkUtils.runDhcp(mInterfaceName, dhcpInfoInternal)) {
                                 SystemProperties.set("net.dns1", dhcpInfoInternal.dns1);
                                 event = EVENT_INTERFACE_CONFIGURATION_SUCCEEDED;
                                 Slog.d(TAG, "DhcpHandler: DHCP request succeeded: " + dhcpInfoInternal.toString());
                            } else {
                                 event = EVENT_INTERFACE_CONFIGURATION_FAILED;
                                 Slog.e(TAG, "DhcpHandler: DHCP request failed: " + NetworkUtils.getDhcpError());
                            }
                          //  sendMessage(event);
                         } else {
                                 mInterfaceStopped = false;
                         }
                         mStartingDhcp = false;
                     }
                     break;
             }
         }
    }

    static LinkProperties getLinkProperties() {
        return new LinkProperties();
    }

    public void reconnectCommand() {
//        sendMessage(CMD_RECONNECT);
    }

    private static int lookupHost(String hostname) {
        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.getByName(hostname);
        } catch (UnknownHostException e) {
            return -1;
        }
        byte[] addrBytes;
        int addr;
        addrBytes = inetAddress.getAddress();
        addr = ((addrBytes[3] & 0xff) << 24)
                | ((addrBytes[2] & 0xff) << 16)
                | ((addrBytes[1] & 0xff) << 8)
                |  (addrBytes[0] & 0xff);
        return addr;
    }
}

