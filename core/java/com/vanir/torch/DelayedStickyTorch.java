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
import android.os.Handler;
import android.util.Log;
import android.view.ViewConfiguration;
import com.android.internal.util.cm.TorchConstants;

public class DelayedStickyTorch extends StickyTorch {

    private Handler mHandler;

    public DelayedStickyTorch(Context context) {
        super(context);
        mHandler = new Handler();
    }

    @Override
    void setTorchState(final Context context, final boolean on) {
        mHandler.removeCallbacksAndMessages(null);

        Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            if (DEBUG) Dump("Torch runnable ("+on+") running");
                            internalChangeTorchState(context, on);
                        }
                     };

        if (on) {
            if (DEBUG) Dump("Posting delayed enable");
            mHandler.postDelayed(r, ViewConfiguration.getLongPressTimeout());
        } else {
            mHandler.post(r);
        }
    }
}
