/*
 * Copyright (C) 2013 AOKP by Mike Wilson - Zaphod-Beeblebrox && Steve Spear - Stevespear426
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
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import java.util.Arrays;

public class AwesomeConstants {

    public static final String ASSIST_ICON_METADATA_NAME = "com.android.systemui.action_assist_icon";

    public final static int SWIPE_LEFT = 0;
    public final static int SWIPE_RIGHT = 1;
    public final static int SWIPE_DOWN = 2;
    public final static int SWIPE_UP = 3;
    public final static int TAP_DOUBLE = 4;
    public final static int PRESS_LONG = 5;
    public final static int SPEN_REMOVE = 6;
    public final static int SPEN_INSERT = 7;

    private static Resources mSystemUiResources = null;

    public interface AwesomeGuts {
        int getDrawableID(Context context);
        int getStringID();
    }

    /* Adding Actions here will automatically add them to NavBar actions in Settings.
     */
    public static enum AwesomeConstant implements AwesomeGuts {
        // THESE AIN'T YOUR DAD'S Assignable actions

        //ENUM NAME MUST BE THE ACTION STRING WITHOUT ASTERISKS, TOUPPER, APPENDED TO "ACTION_". CAPICHE?!
        ACTION_APP           ("**app**",              com.android.internal.R.string.action_app,                  null),
        ACTION_BACK          ("**back**",             com.android.internal.R.string.action_back,                 "com.android.systemui:drawable/ic_sysbar_back"),
        ACTION_HOME          ("**home**",             com.android.internal.R.string.action_home,                 "com.android.systemui:drawable/ic_sysbar_home"),
        ACTION_RECENTS       ("**recents**",          com.android.internal.R.string.action_recents,              "com.android.systemui:drawable/ic_sysbar_recent"),
        ACTION_BLANK         ("**blank**",            com.android.internal.R.string.action_blank,                "com.android.systemui:drawable/ic_sysbar_blank"),
        ACTION_GESTURE_ACTIONS("**gesture_actions**", com.android.internal.R.string.gesture_actions,             "com.android.systemui:drawable/ic_sysbar_gesture"),
        ACTION_KILL          ("**kill**",             com.android.internal.R.string.action_kill,                 "com.android.systemui:drawable/ic_sysbar_killtask"),
        ACTION_LASTAPP       ("**lastapp**",          com.android.internal.R.string.action_lastapp,              "com.android.systemui:drawable/ic_sysbar_lastapp"),
        ACTION_MENU          ("**menu**",             com.android.internal.R.string.action_menu,                 "com.android.systemui:drawable/ic_sysbar_menu_big"),
        ACTION_NOTIFICATIONS ("**notifications**",    com.android.internal.R.string.action_notifications,        "com.android.systemui:drawable/ic_sysbar_notifications"),
        ACTION_IME           ("**ime**",              com.android.internal.R.string.action_ime,                  "com.android.systemui:drawable/ic_sysbar_ime_switcher"),
        ACTION_ASSIST        ("**assist**",           com.android.internal.R.string.action_assist,               "com.android.systemui:drawable/ic_sysbar_assist"),
        ACTION_SEARCH        ("**search**",           com.android.internal.R.string.action_search,               "com.android.systemui:drawable/ic_sysbar_search"),
        ACTION_VOICEASSIST   ("**voiceassist**",      com.android.internal.R.string.action_voiceassist,          "com.android.systemui:drawable/ic_sysbar_voiceassist"),
        ACTION_RING_SILENT   ("**ring_silent**",      com.android.internal.R.string.action_silent,               "com.android.systemui:drawable/ic_sysbar_silent"),
        ACTION_RING_SILENT_VIB("**ring_vib_silent**",  com.android.internal.R.string.action_silent_vib,          "com.android.systemui:drawable/ic_sysbar_silent_vib"),
        ACTION_RING_VIB           ("**ring_vib**",         com.android.internal.R.string.action_vib,             "com.android.systemui:drawable/ic_sysbar_vib"),
        ACTION_TORCH         ("**torch**",            com.android.internal.R.string.action_torch,                "com.android.systemui:drawable/ic_sysbar_torch"),
        /* unassignable actions */
        ACTION_ARROW_LEFT    ("**arrow_left**",       com.android.internal.R.string.action_null,                 "com.android.systemui:drawable/ic_sysbar_ime_left"),
        ACTION_ARROW_RIGHT   ("**arrow_right**",      com.android.internal.R.string.action_null,                 "com.android.systemui:drawable/ic_sysbar_ime_right"),
        ACTION_ARROW_UP      ("**arrow_up**",         com.android.internal.R.string.action_null,                 "com.android.systemui:drawable/ic_sysbar_ime_up"),
        ACTION_ARROW_DOWN    ("**arrow_down**",       com.android.internal.R.string.action_null,                 "com.android.systemui:drawable/ic_sysbar_ime_down"),
        /* Disabled or non-assignable actions?? */
        ACTION_POWER         ("**power**",            com.android.internal.R.string.action_null,                 null),
        ACTION_WIDGETS       ("**widgets**",          com.android.internal.R.string.action_null,                 null),
        ACTION_APP_WINDOW    ("**app_window**",       com.android.internal.R.string.action_null,                 null),
        ACTION_NULL          ("**null**",             com.android.internal.R.string.action_null,                 null);

        private final String action;
        private final int string_id;
        private String drawable_fqid;
        private int drawable_id = 0;

        private AwesomeConstant(final String a, final int s, final String unresolved_drawable_id) {
            action = a;
            string_id = s;
            drawable_fqid = unresolved_drawable_id;
        }

        //only look up the drawable id once
        public int getDrawableID(Context context) {
            if (drawable_fqid != null) {
                drawable_id = getSystemUIDrawableID(context, drawable_fqid);
                drawable_fqid = null;
            }
            return drawable_id;
        }

        //it's an integer. STREAMLINE this shit.
        public int getStringID() { return string_id; }

        public String value() { return action; }

        @Override
        public String toString() { return action; }
    }



    // NOW WITHOUT LINEAR PROBING
    public static AwesomeConstant fromString(String string) {
        if (!TextUtils.isEmpty(string) && string.contains("**")) {
            final String suffix = string.replace("**","").toUpperCase();
            return AwesomeConstant.valueOf("ACTION_"+suffix);
        }
        // not in ENUM must be custom
        return AwesomeConstant.ACTION_APP;
    }



    public static String[] AwesomeActions() {
        return fromAwesomeActionArray(AwesomeConstant.values());
    }



    public static String[] fromAwesomeActionArray(AwesomeConstant[] allTargs) {
        return Arrays.toString(allTargs).split("[\\[\\]\\* \\,]*");
    }



    public static Drawable getSystemUIDrawable(Context mContext, int ResID) {
        if (mSystemUiResources == null) {
            return null;
        }

        try {
            return mSystemUiResources.getDrawable(ResID);
        } catch (Exception e) {
            mSystemUiResources = null;
        }
        return null;
    }



    public static int getSystemUIDrawableID(Context mContext, String DrawableID) {

        if (mSystemUiResources == null) {
            PackageManager pm = mContext.getPackageManager();
            try {
                mSystemUiResources = pm.getResourcesForApplication("com.android.systemui");
            } catch (Exception e) {
            }
        }

        if (mSystemUiResources == null) {
            return 0;
        }

        if (DrawableID != null) {
            return mSystemUiResources.getIdentifier(DrawableID, null, null);
        }

        return 0;
    }



    // Will return a string for the associated action, but will need the caller's context to get resources.
    public static String getProperName(Context context, String actionstring) {
        // 50:1 line reduction by nuclearmistake
        AwesomeConstant act = TextUtils.isEmpty(actionstring) ? AwesomeConstant.ACTION_NULL : fromString(actionstring);
        return context.getResources().getString(act.getStringID());
    }



        // Will return a Drawable for the associated action, but will need the caller's context to get resources.
    public static Drawable getActionIcon(Context context,String actionstring) {
        // 50:1 line reduction by nuclearmistake
        AwesomeConstant action = fromString(actionstring);
        return getSystemUIDrawable(context, action.getDrawableID(context));
    }



    public static String defaultNavbarLayout(Context context) {
        Resources res = context.getResources();
        return res.getString(com.android.internal.R.string.def_navbar_layout);
    }



    public static String defaultIMEKeyLayout(Context context) {
        Resources res = context.getResources();
        return res.getString(com.android.internal.R.string.def_ime_layout);
    }
}
