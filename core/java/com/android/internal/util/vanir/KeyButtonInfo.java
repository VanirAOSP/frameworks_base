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

package com.android.internal.util.vanir;

import android.text.TextUtils;

import com.android.internal.util.vanir.NavbarConstants.NavbarConstant;

public class KeyButtonInfo {
    public static final String TAG = "keybuttoninfo";
    public static final String NULL_ACTION = NavbarConstant.ACTION_NULL.value();

    public String singleAction, doubleTapAction, longPressAction, iconUri;

    // reserved for single purpose keys
    public KeyButtonInfo(String singleTap) {
        this.singleAction = singleTap;
    }

    // reserved for configurable buttons
    public KeyButtonInfo(String singleTap, String doubleTap, String longPress, String uri) {
        this.singleAction = singleTap;
        this.doubleTapAction = doubleTap;
        this.longPressAction = longPress;
        this.iconUri = uri;

        if (singleAction != null) {
            if ((singleAction.isEmpty()
                    || singleAction.equals(NULL_ACTION))) {
                singleAction = null;
            }
        }

        if (doubleTapAction != null) {
            if ((doubleTapAction.isEmpty()
                    || doubleTapAction.equals(NULL_ACTION))) {
                doubleTapAction = null;
            }
        }

        if (longPressAction != null) {
            if ((longPressAction.isEmpty()
                    || longPressAction.equals(NULL_ACTION))) {
                longPressAction = null;
            }
        }
	}
}
