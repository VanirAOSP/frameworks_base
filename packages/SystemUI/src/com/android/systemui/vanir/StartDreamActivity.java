/*
 * Copyright 2014 VanirAOSP && the Android Open Source Project
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

package com.android.systemui.vanir;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.widget.Toast;

import com.android.systemui.R;

/*
 * Start Dreams
 */

public class StartDreamActivity extends Activity {

    IDreamManager mDreamManager;
    
    public StartDreamActivity() {
        super();
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDreamManager = IDreamManager.Stub.asInterface(
                ServiceManager.getService(DreamService.DREAM_SERVICE));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mDreamManager == null)
            return;
        try {
            mDreamManager.dream();
        } catch (RemoteException e) {
            final String cheese = StartDreamActivity.this.getString(R.string.toggle_dream_failed);
            Toast.makeText(StartDreamActivity.this, cheese, Toast.LENGTH_SHORT).show();
        }
        this.finish();
    }
}
