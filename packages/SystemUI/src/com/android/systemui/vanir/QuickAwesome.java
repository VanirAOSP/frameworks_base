/*
 * Copyright 2011 AOKP/Vanir
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
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.Intent.ShortcutIconResource;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Vibrator;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.util.AttributeSet;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.widget.Toast;

import com.android.internal.statusbar.IStatusBarService;
import com.android.systemui.R;

import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;

public class QuickAwesome {

    public final static String TAG = "VanirTarget";
    private final static String SysUIPackage = "com.android.systemui";

    public final static String QUICK_POWER = "**power**";
    public final static String QUICK_KILL = "**kill**";
    public final static String QUICK_CUSTOM = "**custom**";
    public final static String QUICK_SILENT = "**ring_silent**";
    public final static String QUICK_VIB = "**ring_vib**";
    public final static String QUICK_SILENT_VIB = "**ring_vib_silent**";
    public final static String QUICK_TORCH = "**torch**";
    public final static String QUICK_NULL = "**null**";

    public final static int INT_ACTION_POWER = 1;
    public final static int INT_ACTION_KILL = 2;
    public final static int INT_ACTION_CUSTOM = 3;
    public final static int INT_ACTION_SILENT = 4;
    public final static int INT_ACTION_VIB = 5;
    public final static int INT_ACTION_SILENT_VIB = 6;
    public final static int INT_ACTION_TORCH = 7;
    public final static int INT_ACTION_NULL = 8;

    private HashMap<String, Integer> actionMap;

    private HashMap<String, Integer> getActionMap() {
        if (actionMap == null) {
            actionMap = new HashMap<String, Integer>();
            actionMap.put(QUICK_POWER, INT_ACTION_POWER);
            actionMap.put(QUICK_KILL, INT_ACTION_KILL);
            actionMap.put(QUICK_CUSTOM, INT_ACTION_CUSTOM);
            actionMap.put(QUICK_SILENT, INT_ACTION_SILENT);
            actionMap.put(QUICK_VIB, INT_ACTION_VIB);
            actionMap.put(QUICK_SILENT_VIB, INT_ACTION_SILENT_VIB);
            actionMap.put(QUICK_TORCH, INT_ACTION_TORCH);
            actionMap.put(QUICK_NULL, INT_ACTION_NULL);
        }
        return actionMap;
    }

    private int mInjectKeyCode;
    final private Context mContext;
    final private Handler mHandler;
    private AudioManager am;

    private static QuickAwesome sInstance = null;

    public static QuickAwesome getInstance(Context c) {
        if (sInstance == null) {
            sInstance = new QuickAwesome(c);
        }
        return sInstance;
    }

    public QuickAwesome(Context context) {
        mContext = context;
        mHandler = new Handler();
        am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    public boolean launchAction(String action) {
        if (action == null || action.equals(QUICK_NULL)) {
            return false;
        }

        if (getActionMap().containsKey(action)) {
            switch(getActionMap().get(action)) {

            case INT_ACTION_KILL:
                mHandler.post(mKillTask);
                break;
            case INT_ACTION_VIB:
                if(am != null){
                    if(am.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
                        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        Vibrator vib = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                        if(vib != null){
                            vib.vibrate(50);
                        }
                    }else{
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, (int)(ToneGenerator.MAX_VOLUME * 0.85));
                        if(tg != null){
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                break;
            case INT_ACTION_SILENT:
                if(am != null){
                    if(am.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    }else{
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, (int)(ToneGenerator.MAX_VOLUME * 0.85));
                        if(tg != null){
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                break;
            case INT_ACTION_SILENT_VIB:
                if(am != null){
                    if(am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        Vibrator vib = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
                        if(vib != null){
                            vib.vibrate(50);
                        }
                    } else if(am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
                        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    } else {
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, (int)(ToneGenerator.MAX_VOLUME * 0.85));
                        if(tg != null){
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                break;
            case INT_ACTION_POWER:
                injectKeyDelayed(KeyEvent.KEYCODE_POWER);
                break;
            case INT_ACTION_TORCH:
                Intent intentTorch = new Intent("com.android.systemui.INTENT_TORCH_TOGGLE");
                intentTorch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intentTorch);
                break;
            default:
            break;
            }
            return true;
        } else {
            try {
                Intent intent = Intent.parseUri(action, 0);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
                return true;
            } catch (URISyntaxException e) {
                Log.e(TAG, "URISyntaxException: [" + action + "]");
            } catch (ActivityNotFoundException e){
                Log.e(TAG, "ActivityNotFound: [" + action + "]");
            }
            return false;
        }
    }

    public Drawable getIconImage(String uri) {
        if (uri == null)
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_null);
        if (uri.equals(QUICK_KILL))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_killtask);
        if (uri.equals(QUICK_POWER))
            return mContext.getResources().getDrawable(R.drawable.ic_sysbar_power);
        try {
            return mContext.getPackageManager().getActivityIcon(Intent.parseUri(uri, 0));
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return mContext.getResources().getDrawable(R.drawable.ic_sysbar_null);
    }

    public String getProperSummary(String uri) {
        if (uri.equals(QUICK_KILL))  // yup. we're ffffking metal!! \m/
            return mContext.getResources().getString(R.string.action_kill);
        if (uri.equals(QUICK_POWER))
            return mContext.getResources().getString(R.string.action_power);
        if (uri.equals(QUICK_NULL))
            return mContext.getResources().getString(R.string.action_none);
        try {
            Intent intent = Intent.parseUri(uri, 0);
            if (Intent.ACTION_MAIN.equals(intent.getAction())) {
                return getFriendlyActivityName(intent);
            }
            return getFriendlyShortcutName(intent);
        } catch (URISyntaxException e) {
        }
        return mContext.getResources().getString(R.string.action_none);
    }

    private String getFriendlyActivityName(Intent intent) {
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

    private String getFriendlyShortcutName(Intent intent) {
        String activityName = getFriendlyActivityName(intent);
        String name = intent.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);

        if (activityName != null && name != null) {
            return activityName + ": " + name;
        }
        return name != null ? name : intent.toUri(0);
    }

    private void injectKeyDelayed(int keycode) {
        mInjectKeyCode = keycode;
        mHandler.removeCallbacks(onInjectKey_Down);
        mHandler.removeCallbacks(onInjectKey_Up);
        mHandler.post(onInjectKey_Down);
        mHandler.postDelayed(onInjectKey_Up, 10); // introduce small delay to
                                                  // handle key press
    }

    final Runnable onInjectKey_Down = new Runnable() {
        public void run() {
            final KeyEvent ev = new KeyEvent(SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_DOWN, mInjectKeyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD,
                    0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                    InputDevice.SOURCE_KEYBOARD);
            InputManager.getInstance().injectInputEvent(ev,
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };

    final Runnable onInjectKey_Up = new Runnable() {
        public void run() {
            final KeyEvent ev = new KeyEvent(SystemClock.uptimeMillis(),
                    SystemClock.uptimeMillis(),
                    KeyEvent.ACTION_UP, mInjectKeyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                    KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY,
                    InputDevice.SOURCE_KEYBOARD);
            InputManager.getInstance().injectInputEvent(ev,
                    InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        }
    };

    Runnable mKillTask = new Runnable() {
        public void run() {
            final Intent intent = new Intent(Intent.ACTION_MAIN);
            final ActivityManager am = (ActivityManager) mContext
                    .getSystemService(Activity.ACTIVITY_SERVICE);
            String defaultHomePackage = "com.android.launcher";
            intent.addCategory(Intent.CATEGORY_HOME);
            final ResolveInfo res = mContext.getPackageManager().resolveActivity(intent, 0);
            if (res.activityInfo != null && !res.activityInfo.packageName.equals("android")) {
                defaultHomePackage = res.activityInfo.packageName;
            }
            String packageName = am.getRunningTasks(1).get(0).topActivity.getPackageName();
            if (SysUIPackage.equals(packageName))
                return; // don't kill SystemUI
            if (!defaultHomePackage.equals(packageName)) {
                am.forceStopPackage(packageName);
                Toast.makeText(mContext, R.string.app_killed_message, Toast.LENGTH_SHORT).show();
            }
        }
    };

    private Handler H = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {

            }
        }
    };
}
