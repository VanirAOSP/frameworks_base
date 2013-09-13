package com.android.systemui;

import android.app.KeyguardManager;

public class KeyguardState {

    private static boolean isLocked;
    private KeyguardManager mKeyguardManager;
    private static Context mContext;

    public KeyguardState(Context context) {
        this(context, null);
    }

    public static boolean isKeyguardEnabled() {
        KeyguardManager km = (KeyguardManager)mContext.getSystemService(Context.KEYGUARD_SERVICE);
        return km.inKeyguardRestrictedInputMode();
    }
}
