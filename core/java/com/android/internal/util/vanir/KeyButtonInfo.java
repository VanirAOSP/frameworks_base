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

import android.text.TextUtils;
import android.util.Log;

import com.android.internal.util.vanir.AwesomeConstants.AwesomeConstant;

public class KeyButtonInfo {
    public static final String TAG = "keybuttoninfo";
    public static final String NULL_ACTION = AwesomeConstant.ACTION_NULL.value();
    public static final int NO_EXTENSION = 0;
    public static final int NX_EXTENSION = 1;

    public String singleAction, doubleTapAction, longPressAction, iconUri;
    public String swipeLeft, swipeRight, swipeLeftShort, swipeRightShort, swipeUp;

    int viewType;

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
        this.viewType = NO_EXTENSION;
            
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

    // reserved for NX buttons
    public KeyButtonInfo(String[] actions) {
        if (actions[0] != null) this.singleAction = actions[0];
        if (actions[1] != null) this.doubleTapAction = actions[1];
        if (actions[2] != null) this.longPressAction = actions[2];
        if (actions[3] != null) this.swipeLeft = actions[3];
        if (actions[4] != null) this.swipeRight = actions[4];
        if (actions[5] != null) this.swipeLeftShort = actions[5];
        if (actions[6] != null) this.swipeRightShort = actions[6];
        if (actions[7] != null) this.swipeUp = actions[7];
        this.viewType = NX_EXTENSION;
    }

    public int getViewType() {
        return viewType;
    }
}
