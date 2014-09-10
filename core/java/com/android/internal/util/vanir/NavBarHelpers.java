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

import com.android.internal.util.cm.QSUtils;
import com.android.internal.util.vanir.AwesomeConstants;
import com.android.internal.util.vanir.AwesomeConstants.AwesomeConstant;

public class NavBarHelpers extends NavHelpersBase {

    // These items will be subtracted from NavBar Actions when RC requests list of
    // Available Actions
    private static final ArrayList<AwesomeConstant> EXCLUDED_FROM_NAVRING = Arrays.asList(
            AwesomeConstant.ACTION_RING_SILENT,
            AwesomeConstant.ACTION_RING_VIB,
            AwesomeConstant.ACTION_RING_VIB_SILENT
    );

    public static boolean isExcluded(AwesomeConstant action) {
        return (EXCLUDED_FROM_NAVBAR.contains(action) || super.isExcluded(action);
    }

    public static String[] getNavBarActions(Context context) {
        return getActions(context);
    }
}
