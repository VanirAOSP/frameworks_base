/*
 * Copyright (C) 2013 Android Open Kang Project
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

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.input.InputManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.IWindowManager;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.WindowManagerGlobal;
import android.widget.Toast;

import com.android.internal.R;
import com.android.internal.statusbar.IStatusBarService;

import java.net.URISyntaxException;
import java.util.List;

import com.android.internal.util.cm.TorchConstants;
import static com.android.internal.util.vanir.AwesomeConstants.AwesomeConstant;
import static com.android.internal.util.vanir.AwesomeConstants.fromString;
import com.android.internal.util.cm.ActionUtils;

public class AwesomeAction {

    public static final String TAG = "AwesomeAction";
    public static final String NULL_ACTION = AwesomeConstant.ACTION_NULL.value();

    private static final int STANDARD_FLAGS = KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY;
    private static final int CURSOR_FLAGS = KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE;

    private static boolean wtf = true;
    private static boolean ftw;

    private static int mCurrentUserId = 0;

    private AwesomeAction() {
    }

    public static void setCurrentUser(int newUserId) {
        mCurrentUserId = newUserId;
    }

    public static boolean launchAction(final Context mContext, final String action) {
        if (TextUtils.isEmpty(action) || action.equals(NULL_ACTION)) {
            return false;
        }
        AwesomeConstant AwesomeEnum = fromString(action);
        AudioManager am;
        switch (AwesomeEnum) {
            case ACTION_HOME:
                IWindowManager mWindowManagerService = WindowManagerGlobal.getWindowManagerService();
                try {
                    mWindowManagerService.sendHomeAction();
                } catch (RemoteException e) {
                    Log.e(TAG, "HOME ACTION FAILED");
                }
                break;

            case ACTION_BACK:
                triggerVirtualKeypress(KeyEvent.KEYCODE_BACK, STANDARD_FLAGS);
                break;

            case ACTION_MENU:
                triggerVirtualKeypress(KeyEvent.KEYCODE_MENU, STANDARD_FLAGS);
                break;

            case ACTION_SEARCH:
                triggerVirtualKeypress(KeyEvent.KEYCODE_SEARCH, STANDARD_FLAGS);
                break;

            case ACTION_KILL:
                mHandler.removeCallbacksAndMessages(null);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (ActionUtils.killForegroundApp(mContext,mCurrentUserId)) {
                            Toast.makeText(mContext, R.string.app_killed_message, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                break;

            case ACTION_ASSIST:
                Intent intent = new Intent(Intent.ACTION_ASSIST);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (isIntentAvailable(mContext, intent))
                    mContext.startActivity(intent);
                break;
            case ACTION_VOICEASSIST:
                Intent intentVoice = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
                intentVoice.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intentVoice);
                break;

            case ACTION_POWER:
                triggerVirtualKeypress(KeyEvent.KEYCODE_POWER, STANDARD_FLAGS);
                break;

            case ACTION_TORCH:
                Intent intentTorch = new Intent(TorchConstants.ACTION_TOGGLE_STATE);
                mContext.sendBroadcast(intentTorch);
                break;

            case ACTION_LASTAPP:
                ActionUtils.switchToLastApp(mContext, mCurrentUserId);
                break;

            case ACTION_NOTIFICATIONS:
                if (wtf) {
                    if (!ftw) {
                        try {
                            IStatusBarService.Stub.asInterface(
                                ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).expandNotificationsPanel();
                            ftw = true;
                        } catch (RemoteException e) {
                            // A RemoteException is like a cold
                            // Let's hope we don't catch one!
                        }
                    } else {
                        try {
                            IStatusBarService.Stub.asInterface(
                                ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).expandSettingsPanel();
                            ftw = false;
                        } catch (RemoteException e) {
                            // NO!!!
                        }
                        wtf = false;
                        }
                    } else {
                    try {
                        IStatusBarService.Stub.asInterface(
                            ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).collapsePanels();
                        wtf = true;
                    } catch (RemoteException e) {
                        // EL CHUPACABRA!!
                    }
                }
                break;

            case ACTION_APP:
                try {
                    Intent intentapp = Intent.parseUri(action, 0);
                    intentapp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intentapp);
                } catch (URISyntaxException e) {
                    Log.e(TAG, "URISyntaxException: [" + action + "]");
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "ActivityNotFound: [" + action + "]");
                }
                break;

            case ACTION_APP_WINDOW:
                Intent appWindow = new Intent();
                appWindow.setAction("com.android.systemui.ACTION_SHOW_APP_WINDOW");
                mContext.sendBroadcast(appWindow);
                break;

            case ACTION_BLANK:
                break;

            case ACTION_ARROW_LEFT:
                triggerVirtualKeypress(KeyEvent.KEYCODE_DPAD_LEFT, CURSOR_FLAGS);
                break;

            case ACTION_ARROW_RIGHT:
                triggerVirtualKeypress(KeyEvent.KEYCODE_DPAD_RIGHT, CURSOR_FLAGS);
                break;

            case ACTION_ARROW_UP:
                triggerVirtualKeypress(KeyEvent.KEYCODE_DPAD_UP, CURSOR_FLAGS);
                break;

            case ACTION_ARROW_DOWN:
                triggerVirtualKeypress(KeyEvent.KEYCODE_DPAD_DOWN, CURSOR_FLAGS);
                break;

            case ACTION_RING_VIB:
                am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    if (am.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
                        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        Vibrator vib = (Vibrator) mContext
                                .getSystemService(Context.VIBRATOR_SERVICE);
                        if (vib != null) {
                            vib.vibrate(50);
                        }
                    } else {
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(
                                AudioManager.STREAM_NOTIFICATION,
                                (int) (ToneGenerator.MAX_VOLUME * 0.85));
                        if (tg != null) {
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                break;

            case ACTION_IME:
                mContext.sendBroadcast(new Intent(
                        "android.settings.SHOW_INPUT_METHOD_PICKER"));
                break;

            case ACTION_RING_SILENT:
                am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    if (am.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    } else {
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(
                                AudioManager.STREAM_NOTIFICATION,
                                (int) (ToneGenerator.MAX_VOLUME * 0.85));
                        if (tg != null) {
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                break;

            case ACTION_RING_VIB_SILENT:
                am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    if (am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                        am.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        Vibrator vib = (Vibrator) mContext
                                .getSystemService(Context.VIBRATOR_SERVICE);
                        if (vib != null) {
                            vib.vibrate(50);
                        }
                    } else if (am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
                        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    } else {
                        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(
                                AudioManager.STREAM_NOTIFICATION,
                                (int) (ToneGenerator.MAX_VOLUME * 0.85));
                        if (tg != null) {
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                break;

            case ACTION_GESTURE_ACTIONS:
                mContext.sendBroadcast(new Intent(Intent.TOGGLE_GESTURE_ACTIONS));
                break;
        }
        return true;
    }

    public static boolean isIntentAvailable(Context context, Intent intent) {
        PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> list = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }

    private static void triggerVirtualKeypress(final int keyCode, int flags) {
        InputManager im = InputManager.getInstance();
        long now = SystemClock.uptimeMillis();

        final KeyEvent downEvent = new KeyEvent(now, now, KeyEvent.ACTION_DOWN,
                keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0,
                flags, InputDevice.SOURCE_KEYBOARD);
        final KeyEvent upEvent = KeyEvent.changeAction(downEvent, KeyEvent.ACTION_UP);

        im.injectInputEvent(downEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
        im.injectInputEvent(upEvent, InputManager.INJECT_INPUT_EVENT_MODE_ASYNC);
    }

    private static Handler mHandler = new Handler();

    public static void wtfHelper() {
        wtf = true;
        ftw = false;
    }
}
