/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.internal.util.aokp;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.net.URISyntaxException;
import java.util.ArrayList;

import com.android.internal.util.cm.QSUtils;
import com.android.internal.util.vanir.AwesomeConstants;
import com.android.internal.util.vanir.AwesomeConstants.AwesomeConstant;

public class NavBarHelpers {

    // These items will be subtracted from NavBar Actions when RC requests list of
    // Available Actions
    private static final AwesomeConstant[] EXCLUDED_FROM_NAVBAR = {
            AwesomeConstant.ACTION_RING_SILENT,
            AwesomeConstant.ACTION_RING_VIB,
            AwesomeConstant.ACTION_RING_VIB_SILENT,
            AwesomeConstant.ACTION_NULL,
            AwesomeConstant.ACTION_POWER,
            AwesomeConstant.ACTION_LAYOUT_LEFT,
            AwesomeConstant.ACTION_LAYOUT_RIGHT,
            AwesomeConstant.ACTION_ARROW_LEFT,
            AwesomeConstant.ACTION_ARROW_RIGHT,
            AwesomeConstant.ACTION_ARROW_UP,
            AwesomeConstant.ACTION_ARROW_DOWN,
            /* these are just not implemented yet: */
            AwesomeConstant.ACTION_WIDGETS,
            AwesomeConstant.ACTION_APP_WINDOW
    };

    private NavBarHelpers() {
    }

    public static Drawable getIconImage(Context mContext, String uri) {
        Drawable actionIcon;
        if (TextUtils.isEmpty(uri)) {
            uri = AwesomeConstant.ACTION_NULL.value();
        }
        if (uri.startsWith("**")) {
            return AwesomeConstants.getActionIcon(mContext, uri);
        } else {  // This must be an app 
            try {
                actionIcon = mContext.getPackageManager().getActivityIcon(Intent.parseUri(uri, 0));
            } catch (NameNotFoundException e) {
                e.printStackTrace();
                actionIcon = AwesomeConstants.getActionIcon(mContext,
                        AwesomeConstant.ACTION_NULL.value());
            } catch (URISyntaxException e) {
                e.printStackTrace();
                actionIcon = AwesomeConstants.getActionIcon(mContext,
                        AwesomeConstant.ACTION_NULL.value());
            }
        }
        return actionIcon;
    }

    public static String[] getNavBarActions(Context context) {
        boolean itemFound;
        String[] mActions;
        ArrayList<String> mActionList = new ArrayList<String>();
        String[] mActionStart = AwesomeConstants.AwesomeActions();
        int startLength = mActionStart.length;
        int excludeLength = EXCLUDED_FROM_NAVBAR.length;
        for (int i = 0; i < startLength; i++) {
            itemFound = false;
            for (int j = 0; j < excludeLength; j++) {
                if (mActionStart[i].equals(EXCLUDED_FROM_NAVBAR[j].value())) {
                    itemFound = true;
                }
            }
            if (!itemFound) {
                mActionList.add(mActionStart[i]);
            }
            if (!context.getResources().getBoolean(com.android.internal.R.bool.config_enableTorch)) {
                mActionList.remove(AwesomeConstant.ACTION_TORCH.value());
            }
        }
        int actionSize = mActionList.size();
        mActions = new String[actionSize];
        for (int i = 0; i < actionSize; i++) {
            mActions[i] = mActionList.get(i);
        }
        return mActions;
    }

    public static String getProperSummary(Context mContext, String uri) {
        if (TextUtils.isEmpty(uri)) {
            uri = AwesomeConstant.ACTION_NULL.value();
        }
        if (uri.startsWith("**")) {
            return AwesomeConstants.getProperName(mContext, uri);
        } else {  // This must be an app 
            try {
                Intent intent = Intent.parseUri(uri, 0);
                if (Intent.ACTION_MAIN.equals(intent.getAction())) {
                    return getFriendlyActivityName(mContext, intent);
                }
                return getFriendlyShortcutName(mContext, intent);
            } catch (URISyntaxException e) {
                return AwesomeConstants.getProperName(mContext, AwesomeConstant.ACTION_NULL.value());
            }
        }
    }

    private static String getFriendlyActivityName(Context mContext, Intent intent) {
        PackageManager pm = mContext.getPackageManager();
        ActivityInfo ai = intent.resolveActivityInfo(pm, PackageManager.GET_ACTIVITIES);
        String friendlyName = null;

        if (ai != null) {
            friendlyName = ai.loadLabel(pm).toString();
            if (friendlyName == null) {
                friendlyName = ai.name;
            }
        }

        return (friendlyName != null) ? friendlyName : intent.toUri(0);
    }

    private static String getFriendlyShortcutName(Context mContext, Intent intent) {
        String activityName = getFriendlyActivityName(mContext, intent);
        String name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (activityName != null && name != null) {
            return activityName + ": " + name;
        }
        return name != null ? name : intent.toUri(0);
    }
}
