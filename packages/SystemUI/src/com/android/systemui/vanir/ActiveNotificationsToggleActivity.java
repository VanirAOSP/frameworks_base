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
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import com.android.systemui.R;

/*
 * ActiveNotificationsToggleActivity activity toggle
 */

public class ActiveNotificationsToggleActivity extends Activity {

    public ActiveNotificationsToggleActivity() {
        super();
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        setActive();
        this.finish();
    }

    private void setActive() {
        boolean lockscreenNotifications =
                Settings.System.getInt(getContentResolver(),
                            Settings.System.LOCKSCREEN_NOTIFICATIONS, 0) == 1;
        boolean activeDisplay =
                Settings.System.getInt(getContentResolver(),
                            Settings.System.ENABLE_ACTIVE_DISPLAY, 0) == 1;

        if (lockscreenNotifications || activeDisplay) {
            boolean valueOn = Settings.System.getInt(getContentResolver(),
                Settings.System.ACTIVE_NOTIFICATIONS, 0) == 1;
            Settings.System.putInt(getContentResolver(),
                    Settings.System.ACTIVE_NOTIFICATIONS, valueOn ? 0 : 1);
            
            enabled(valueOn);
        } else {
            wtfAreYouDoing();
        }
    }

    private void enabled(boolean isOn) {
        String cheese = "";
        if (!isOn) {
            cheese = ActiveNotificationsToggleActivity.this.getString(R.string.active_notifications_on_toast);
        } else {
            cheese = ActiveNotificationsToggleActivity.this.getString(R.string.active_notifications_off_toast);
        }

        Toast.makeText(ActiveNotificationsToggleActivity.this, cheese, Toast.LENGTH_SHORT).show();
    }

    private void wtfAreYouDoing() {
        final String cheese = ActiveNotificationsToggleActivity.this.getString(R.string.active_notifications_wtf_toast);
        Toast.makeText(ActiveNotificationsToggleActivity.this, cheese, Toast.LENGTH_SHORT).show();
    }
}
