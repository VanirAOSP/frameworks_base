/*
 * Copyright (C) 2014 VanirAOSP
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

package com.vanir.torch;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import com.android.internal.util.cm.TorchConstants;

public abstract class StickyTorch {
    private static final String TAG = "STICKYTORCH";

    protected static final boolean DEBUG = false;
    protected static final boolean SPEW = false;

    protected boolean mGlobalTorchOn; // global state of torch
    protected boolean mTorchOn;       // if mGlobalTorchOn && mTorchOn, that means we turned it on

    public StickyTorch(Context context) {
        // register for Torch updates
        IntentFilter filter = new IntentFilter();
        filter.addAction(TorchConstants.ACTION_STATE_CHANGED);
        context.registerReceiver(mTorchReceiver, filter);
    }

    private BroadcastReceiver mTorchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized(this) {
                mGlobalTorchOn = intent.getIntExtra(TorchConstants.EXTRA_CURRENT_STATE, 0) != 0;
                if (DEBUG) Dump("Received torch state broadcast");
                if (mTorchOn && !mGlobalTorchOn) {
                    Dump("Forcing torch back on because something else disabled it");
                    Intent i = new Intent(TorchConstants.ACTION_TOGGLE_STATE);
                    context.sendBroadcast(i);
                }
            }
        }
    };

    abstract void setTorchState(Context context, boolean on);

    protected void internalChangeTorchState(Context context, boolean on) {
        Dump("internalChangeTorchState("+on+")");
        if (on != mTorchOn && mTorchOn == mGlobalTorchOn) {
            mTorchOn = on;
            if (DEBUG) Dump("Firing torch toggle intent");
            Intent i = new Intent(TorchConstants.ACTION_TOGGLE_STATE);
            context.sendBroadcast(i);
        }
    }

    public void changeTorchState(Context context, boolean on) {
        synchronized(this) {
            if (DEBUG) Dump("changeTorchState("+on+")");
            setTorchState(context, on);
        }
    }

    protected void Dump(String s) {
        Log.v(TAG, s);
        if (SPEW) {
            Log.v(TAG, "\tmGlobalTorchOn="+mGlobalTorchOn);
            Log.v(TAG, "\tmTorchOn="+mTorchOn);
        }
    }
}
