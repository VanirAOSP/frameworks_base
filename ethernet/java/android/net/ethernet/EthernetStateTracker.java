/*
 * Copyright (C) 2010 The Android-X86 Open Source Project
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

package android.net.ethernet;

import java.net.InetAddress;
import java.net.UnknownHostException;

import android.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothHeadset;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkStateTracker;
import android.net.NetworkUtils;
import android.net.ProxyProperties;
import android.net.RouteInfo;
import android.net.LinkAddress;
import android.net.DhcpInfoInternal;
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

import android.net.DnsPinger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Track the state of Ethernet connectivity. All event handling is done here,
 * and all changes in connectivity state are initiated here.
 *
 * @hide
 */

public class EthernetStateTracker implements NetworkStateTracker {
    private static final String NETWORKTYPE = "ETHERNET";
    private static final String TAG = "EthernetStateTracker";

    private static final boolean localLOGV = true;

    public static final int EVENT_DHCP_START                        = 0;
    public static final int EVENT_INTERFACE_CONFIGURATION_SUCCEEDED = 1;
    public static final int EVENT_INTERFACE_CONFIGURATION_FAILED    = 2;
    public static final int EVENT_HW_CONNECTED                      = 3;
    public static final int EVENT_HW_DISCONNECTED                   = 4;
    public static final int EVENT_HW_PHYCONNECTED                   = 5;
    private static final int NOTIFY_ID                              = 6;

    private EthernetManager mEthernetManager;

    private AtomicBoolean mTeardownRequested = new AtomicBoolean(false);
    private AtomicBoolean mPrivateDnsRouteSet = new AtomicBoolean(false);
    private AtomicBoolean mDefaultRouteSet = new AtomicBoolean(false);

    private LinkProperties mLinkProperties;
    private LinkCapabilities mLinkCapabilities;
    private NetworkInfo mNetworkInfo;

    private DnsPinger mDnsPinger;

    private boolean mStackConnected;
    private boolean mHWConnected;
    private boolean mInterfaceStopped;
    private DhcpHandler mDhcpTarget;
    private String mInterfaceName ;
    private DhcpInfoInternal mDhcpInfoInternal;
    private EthernetMonitor mMonitor;
    private String[] sDnsPropNames;
    private boolean mStartingDhcp;
    private NotificationManager mNotificationManager;
    private Notification mNotification;

    private Handler mTrackerTarget;
    public static EthernetStateTracker sInstance;

    /* For sending events to connectivity service handler */
    private Handler mCsHandler;
    private Context mContext;

    public EthernetStateTracker(int netType, String networkName) {
        if (localLOGV) Slog.v(TAG, "Starts...");

        if (EthernetNative.initEthernetNative() != 0) {
            Slog.e(TAG,"Can not init ethernet device layers");
            return;
        }

        if (localLOGV) Slog.v(TAG,"Successed");
        HandlerThread dhcpThread = new HandlerThread("DHCP Handler Thread");
        dhcpThread.start();
//        mDhcpTarget = new DhcpHandler(dhcpThread.getLooper(), this);
        mMonitor = new EthernetMonitor(this);

        mNetworkInfo = new NetworkInfo(netType, 0, networkName, "");
        mLinkProperties = new LinkProperties();
        mLinkCapabilities = new LinkCapabilities();

        mNetworkInfo.setIsAvailable(false);
        setTeardownRequested(false);

    }

    public void setTeardownRequested(boolean isRequested) {
        mTeardownRequested.set(isRequested);
    }

    public void captivePortalCheckComplete() {
    }

    public boolean isTeardownRequested() {
        return mTeardownRequested.get();
    }

    public void startMonitoring(Context context, Handler target) {
        mCsHandler = target;
        mContext = context;

        if (localLOGV) Slog.v(TAG,"start to monitor the ethernet devices");
        mEthernetManager = (EthernetManager) mContext.getSystemService(Context.ETHERNET_SERVICE);

        int state = mEthernetManager.getState();
        if (state != mEthernetManager.ETHERNET_STATE_DISABLED) {
            if (state == mEthernetManager.ETHERNET_STATE_UNKNOWN) {
                // maybe this is the first time we run, enable it if ethernet devices exist
                mEthernetManager.setEnabled(mEthernetManager.getDeviceNameList() != null);
            } else {
                try {
                    resetInterface();
                } catch (UnknownHostException e) {
                    Slog.e(TAG, "Wrong ethernet configuration");
                }
            }
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(EthernetManager.NETWORK_STATE_CHANGED_ACTION);
    }

    /**
     * Disable connectivity to a network
     * TODO: do away with return value after making MobileDataStateTracker async
     */
    public boolean teardown() {
        mTeardownRequested.set(true);
        mEthernetManager.stopEthernet();
        return true;
    }

    /**
     * Re-enable connectivity to a network after a {@link #teardown()}.
     */
    public boolean reconnect() {
        mTeardownRequested.set(false);
        mEthernetManager.startEthernet();
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
/*                synchronized (mDhcpTarget) {
                    mInterfaceStopped = true;
                    if (localLOGV) Slog.i(TAG, "stop dhcp and interface");
                    mDhcpTarget.removeMessages(EVENT_DHCP_START);
                    String ifname = info.getIfName();

                    if (!NetworkUtils.stopDhcp(ifname)) {
                        if (localLOGV) Slog.w(TAG, "Could not stop DHCP");
                    }
                    NetworkUtils.resetConnections(ifname, NetworkUtils.RESET_ALL_ADDRESSES);
                    if (!suspend)
                        NetworkUtils.disableInterface(ifname);
                }
*/
            }
        }
        return true;
    }

    private boolean configureInterface(EthernetDevInfo info) throws UnknownHostException {

        mStackConnected = false;
        mHWConnected = false;
        mInterfaceStopped = false;
        mStartingDhcp = true;

        DhcpInfoInternal mDhcpInfoInternal = new DhcpInfoInternal();
        LinkProperties linkProperties = getLinkProperties();

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

            if (linkProperties != null) {
            Iterator<LinkAddress> iter = linkProperties.getLinkAddresses().iterator();
            if (iter.hasNext()) {
                LinkAddress linkAddress = iter.next();
                mDhcpInfoInternal.ipAddress = linkAddress.getAddress().getHostAddress();
                for (RouteInfo route : linkProperties.getRoutes()) {
                    mDhcpInfoInternal.addRoute(route);
                }

                mDhcpInfoInternal.prefixLength = linkAddress.getNetworkPrefixLength();
                Iterator<InetAddress> dnsIterator = linkProperties.getDnses().iterator();
                mDhcpInfoInternal.dns1 = dnsIterator.next().getHostAddress();

                if (dnsIterator.hasNext()) {
                    mDhcpInfoInternal.dns2 = dnsIterator.next().getHostAddress();
                }
            }
        }


            if (localLOGV) Slog.i(TAG, "set ip manually " + mDhcpInfoInternal.toString());
            NetworkUtils.resetConnections(info.getIfName(), NetworkUtils.RESET_ALL_ADDRESSES);

            if (NetworkUtils.runDhcp(info.getIfName(), mDhcpInfoInternal)) {
                event = EVENT_INTERFACE_CONFIGURATION_SUCCEEDED;
                SystemProperties.set("net.dns1", info.getDnsAddr());
                SystemProperties.set("net." + info.getIfName() + ".dns1", info.getDnsAddr());
                SystemProperties.set("net." + info.getIfName() + ".dns2", "0.0.0.0");
                if (localLOGV) Slog.v(TAG, "Static IP configuration succeeded");
            } else {
                event = EVENT_INTERFACE_CONFIGURATION_FAILED;
                if (localLOGV) Slog.w(TAG, "Static IP configuration failed");
            }
//            this.sendEmptyMessage(event);
        }
        return true;
    }

    /**
     * reset ethernet interface
     * @return true
     * @throws UnknownHostException
     */
    public boolean resetInterface()  throws UnknownHostException{
        /*
         * This will guide us to enabled the enabled device
         */
        if (mEthernetManager != null) {
            EthernetDevInfo info = mEthernetManager.getSavedConfig();
            if (info != null && mEthernetManager.isConfigured()) {
                synchronized (this) {
                    mInterfaceName = info.getIfName();
                    if (localLOGV) Slog.i(TAG, "reset device " + mInterfaceName);
                    NetworkUtils.resetConnections(mInterfaceName, NetworkUtils.RESET_ALL_ADDRESSES);
                     // Stop DHCP
//                    if (mDhcpTarget != null) {
//                        mDhcpTarget.removeMessages(EVENT_DHCP_START);
//                    }
                    if (!NetworkUtils.stopDhcp(mInterfaceName)) {
                        if (localLOGV) Slog.w(TAG, "Could not stop DHCP");
                    }
                    configureInterface(info);
                }
            }
        }
        return true;
    }

    public void StartPolling() {
        mMonitor.startMonitoring();
    }

    public synchronized boolean isAvailable() {
        // Only say available if we have interfaces and user did not disable us.
        return ((mEthernetManager.getTotalInterface() != 0) && (mEthernetManager.getState() != EthernetManager.ETHERNET_STATE_DISABLED));
    }

//    @Override
    public void setUserDataEnable(boolean enabled) {
        Slog.w(TAG, "ignoring setUserDataEnable(" + enabled + ")");
    }

//    @Override
    public void setPolicyDataEnable(boolean enabled) {
        Slog.w(TAG, "ignoring setPolicyDataEnable(" + enabled + ")");
    }

/*    @Override
    public boolean reconnect() {
        try {
            synchronized (this) {
                if (mHWConnected && mStackConnected)
                    return true;
            }
            if (mEthernetManager.getState() != EthernetManager.ETHERNET_STATE_DISABLED) {
                // maybe this is the first time we run, so set it to enabled
                mEthernetManager.setEnabled(true);
                if (!mEthernetManager.isConfigured()) {
                    mEthernetManager.setDefaultConf();
                }
                return resetInterface();
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return false;

    }
*/
//    @Override
    public boolean setRadio(boolean turnOn) {
        return false;
    }

/*
    public boolean teardown() {
        return (mEthernetManager != null) ? stopInterface(false) : false;
    }
*/
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
                mNetworkInfo.setDetailedState(DetailedState.CONNECTED, null, null);
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
         public DhcpHandler(Looper looper, Handler target) {
             super(looper);
             mTrackerTarget = target;
         }

         public void handleMessage(Message msg) {
             int event;

             switch (msg.what) {
                 case EVENT_DHCP_START:
                     if (!mInterfaceStopped) {
                         if (localLOGV) Slog.d(TAG, "DhcpHandler: DHCP request started");

                         DhcpInfoInternal dhcpInfoInternal = new DhcpInfoInternal();
                         if (NetworkUtils.runDhcp(mInterfaceName, dhcpInfoInternal)) {
                             event = EVENT_INTERFACE_CONFIGURATION_SUCCEEDED;
                             if (localLOGV) Slog.d(TAG, "DhcpHandler: DHCP request succeeded: " + mDhcpInfoInternal.toString());
                        } else {
                             event = EVENT_INTERFACE_CONFIGURATION_FAILED;
                             Slog.e(TAG, "DhcpHandler: DHCP request failed: " + NetworkUtils.getDhcpError());
                        }
                     } else {
                             mInterfaceStopped = false;
                     }
                     mStartingDhcp = false;
                     break;
             }
         }
    }

    public void notifyPhyConnected(String ifname) {
        if (localLOGV) Slog.v(TAG, "report interface is up for " + ifname);
        synchronized(this) {
//            this.sendEmptyMessage(EVENT_HW_PHYCONNECTED);
        }

    }

    public void notifyStateChange(String ifname,DetailedState state) {
        if (localLOGV) Slog.i(TAG, "report new state " + state.toString() + " on dev " + ifname);
        if (ifname.equals(mInterfaceName)) {
            if (localLOGV) Slog.v(TAG, "update network state tracker");
            synchronized(this) {
     //           this.sendEmptyMessage(state.equals(DetailedState.CONNECTED, null, null)
     //               ? EVENT_HW_CONNECTED : EVENT_HW_DISCONNECTED);

            }
        }
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

    public void setDependencyMet(boolean met) {
        // not supported on this network
    }

    /**
     * Check if private DNS route is set for the network
     */
    public boolean isPrivateDnsRouteSet() {
        return mPrivateDnsRouteSet.get();
    }

    /**
     * Set a flag indicating private DNS route is set
     */
    public void privateDnsRouteSet(boolean enabled) {
        mPrivateDnsRouteSet.set(enabled);
    }

    /**
     * Fetch NetworkInfo for the network
     */
    public NetworkInfo getNetworkInfo() {
        return new NetworkInfo(mNetworkInfo);
    }

    /**
     * Fetch LinkProperties for the network
     */
    public LinkProperties getLinkProperties() {
        return new LinkProperties(mLinkProperties);
    }

    /**
     * A capability is an Integer/String pair, the capabilities
     * are defined in the class LinkSocket#Key.
     *
     * @return a copy of this connections capabilities, may be empty but never null.
     */
    public LinkCapabilities getLinkCapabilities() {
        return new LinkCapabilities(mLinkCapabilities);
    }

    /**
     * Check if default route is set
     */
    public boolean isDefaultRouteSet() {
        return mDefaultRouteSet.get();
    }

    /**
     * Set a flag indicating default route is set for the network
     */
    public void defaultRouteSet(boolean enabled) {
        mDefaultRouteSet.set(enabled);
    }

    /**
     * Return the system properties name associated with the tcp buffer sizes
     * for this network.
     */
    public String getTcpBufferSizesPropName() {
        return "net.tcp.buffersize.default";
    }

}
