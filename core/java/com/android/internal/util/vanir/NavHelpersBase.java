/*
 * Copyright (C) 2014 VanirAOSP && The Android Open Source Project
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

package com.android.internal.util.vanir;

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
import java.util.Arrays;
import java.util.List;

import com.android.internal.util.cm.QSUtils;
import com.android.internal.util.vanir.AwesomeConstants;
import com.android.internal.util.vanir.AwesomeConstants.AwesomeConstant;

public class NavHelpersBase {

    // These items will be subtracted from NavBar Actions when RC requests list of
    // Available Actions
    protected static final List<AwesomeConstant> EXCLUDED_FROM_NAV = Arrays.<AwesomeConstant>asList(
            AwesomeConstant.ACTION_NULL,
            AwesomeConstant.ACTION_POWER,
            AwesomeConstant.ACTION_ARROW_LEFT,
            AwesomeConstant.ACTION_ARROW_RIGHT,
            AwesomeConstant.ACTION_ARROW_UP,
            AwesomeConstant.ACTION_ARROW_DOWN,
            AwesomeConstant.ACTION_WIDGETS,
            AwesomeConstant.ACTION_APP_WINDOW
    );

    public boolean isExcluded(AwesomeConstant action) {
        return (EXCLUDED_FROM_NAV.contains(action));
    }

    public Drawable getIconImage(Context context, String uri) {
        AwesomeConstant act = AwesomeConstant.fromAction(uri);
        if (act != AwesomeConstant.ACTION_APP) {
            return act.getDrawable(context);
        } 
        try {
            return context.getPackageManager().getActivityIcon(Intent.parseUri(uri, 0));
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return AwesomeConstant.ACTION_NULL.getDrawable(context);
    }

    public String[] getActions(Context context) {
        ArrayList<AwesomeConstant> actionList = new ArrayList<AwesomeConstant>();
        for(AwesomeConstant act : AwesomeConstant.values()) {
            if (!isExcluded(act))
                actionList.add(act);
        }
        if (!context.getResources().getBoolean(com.android.internal.R.bool.config_enableTorch)) {
            actionList.remove(AwesomeConstant.ACTION_TORCH.value());
        }
        return actionList.toArray(new String[0]);
    }
}
