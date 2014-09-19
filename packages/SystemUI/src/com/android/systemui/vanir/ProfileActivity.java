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
import android.app.AlertDialog;
import android.app.Profile;
import android.app.ProfileManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.Toast;

import com.android.systemui.R;

import java.util.UUID;

/*
 * Switch profiles
 */

public class ProfileActivity extends Activity {

    Profile mChosenProfile;
    ProfileManager mProfileManager;
    
    public ProfileActivity() {
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
        if (Settings.System.getInt(getContentResolver(),
                Settings.System.POWER_MENU_PROFILES_ENABLED, 0) == 1) {
            createProfileDialog();
        } else {
            final String cheese = ProfileActivity.this.getString(R.string.toggle_profile_toast);
            Toast.makeText(ProfileActivity.this, cheese, Toast.LENGTH_SHORT).show();
        }
    }

    private void createProfileDialog() {
        final ProfileManager profileManager = (ProfileManager) ProfileActivity.this
                .getSystemService(Context.PROFILE_SERVICE);

        final Profile[] profiles = profileManager.getProfiles();
        UUID activeProfile = profileManager.getActiveProfile().getUuid();
        final CharSequence[] names = new CharSequence[profiles.length];

        int i = 0;
        int checkedItem = 0;

        for (Profile profile : profiles) {
            if (profile.getUuid().equals(activeProfile)) {
                checkedItem = i;
                mChosenProfile = profile;
            }
            names[i++] = profile.getName();
        }

        final AlertDialog.Builder ab = new AlertDialog.Builder(ProfileActivity.this);

        AlertDialog dialog = ab.setSingleChoiceItems(names, checkedItem,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < 0)
                            return;
                        mChosenProfile = profiles[which];
                        profileManager.setActiveProfile(mChosenProfile.getUuid());
                        dialog.cancel();
                        finish();
                    }
                }).create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        try {
            WindowManagerGlobal.getWindowManagerService().dismissKeyguard();
        } catch (RemoteException e) {
        }
        dialog.show();
    }
}
