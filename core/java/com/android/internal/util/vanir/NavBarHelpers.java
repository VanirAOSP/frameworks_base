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

import java.util.Arrays;
import java.util.List;

import com.android.internal.util.vanir.AwesomeConstants.AwesomeConstant;

public class NavBarHelpers extends NavHelpersBase {

    // These items will be subtracted from NavBar Actions when RC requests list of
    // Available Actions
    protected static final List<AwesomeConstant> EXCLUDED_FROM_NAVBAR = Arrays.<AwesomeConstant>asList(
            AwesomeConstant.ACTION_RING_SILENT,
            AwesomeConstant.ACTION_RING_VIB,
            AwesomeConstant.ACTION_RING_VIB_SILENT
    );

    @Override
    public boolean isExcluded(AwesomeConstant action) {
        return (EXCLUDED_FROM_NAVBAR.contains(action) || super.isExcluded(action));
    }

    public static String[] getNavBarActions(Context context) {
        return new NavBarHelpers().getActions(context);
    }
}
