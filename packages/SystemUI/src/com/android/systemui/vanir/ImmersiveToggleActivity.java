/*
 * Copyright 2011 AOKP by Mike Wilson - Zaphod-Beeblebrox
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
 * Immersive activity toggle
 */

public class ImmersiveToggleActivity extends Activity {

  public ImmersiveToggleActivity() {
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
    setImmersive();
    this.finish();
  }

  private void setImmersive() {
    int immersiveModeValue = Settings.System.getInt(getContentResolver(),
            Settings.System.GLOBAL_IMMERSIVE_MODE_STYLE, 0);
    if (immersiveModeValue > 0) {
        boolean on = Settings.System.getInt(getContentResolver(),
                Settings.System.GLOBAL_IMMERSIVE_MODE_STATE, 0) == 1;
        Settings.System.putInt(getContentResolver(),
                Settings.System.GLOBAL_IMMERSIVE_MODE_STATE, on ? 0 : 1);
    } else {
        final String cheese = ImmersiveToggleActivity.this.getString(R.string.toggle_immersive_toast);
        Toast.makeText(ImmersiveToggleActivity.this, cheese, Toast.LENGTH_SHORT).show();
    }
  }
}
