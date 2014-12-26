/*
 * Copyright (C) 2014 VanirAOSP && Android Open Kang Project
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
import android.os.Vibrator;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;
import android.view.IWindowManager;
import android.view.KeyEvent;
import android.view.WindowManagerGlobal;
import android.widget.Toast;

import com.android.internal.R;
import com.android.internal.statusbar.IStatusBarService;

import java.net.URISyntaxException;
import java.util.List;

//import com.android.internal.util.cm.TorchConstants;
import static com.android.internal.util.vanir.NavbarConstants.NavbarConstant;
import static com.android.internal.util.vanir.NavbarConstants.fromString;
import com.android.internal.util.cm.ActionUtils;

public class VanirActions {

    public static final String TAG = "VanirActions";

    private static final int LAYOUT_LEFT = -1;
    private static final int LAYOUT_RIGHT = 1;
    private static final int LAYOUT_IME = NavbarConstants.LAYOUT_IME;

    private static final int STANDARD_FLAGS = KeyEvent.FLAG_FROM_SYSTEM | KeyEvent.FLAG_VIRTUAL_HARD_KEY;
    private static final int CURSOR_FLAGS = KeyEvent.FLAG_SOFT_KEYBOARD | KeyEvent.FLAG_KEEP_TOUCH_MODE;

    private static int mCurrentUserId = 0;

    private static Handler mHandler = new Handler();

    private VanirActions() {
    }

    public static void setCurrentUser(int newUserId) {
        mCurrentUserId = newUserId;
    }

    public static boolean launchAction(final Context mContext, final String action) {
        NavbarConstant AwesomeEnum = fromString(action);
        switch (AwesomeEnum) {
            case ACTION_HOME:
                IWindowManager mWindowManagerService = WindowManagerGlobal.getWindowManagerService();
                try {
                    mWindowManagerService.sendHomeAction();
                } catch (RemoteException e) {
                    Log.e(TAG, "HOME ACTION FAILED");
                }
                break;

			case ACTION_RECENTS:
				try {
					IStatusBarService.Stub.asInterface(
							ServiceManager.getService(mContext.STATUS_BAR_SERVICE))
							.toggleRecentApps();
				} catch (RemoteException e) {
					Log.e(TAG, "RECENTS ACTION FAILED");
				}
				break;

            case ACTION_BACK:
                InputManager.triggerVirtualKeypress(KeyEvent.KEYCODE_BACK, STANDARD_FLAGS);
                break;

            case ACTION_MENU:
                InputManager.triggerVirtualKeypress(KeyEvent.KEYCODE_MENU, STANDARD_FLAGS);
                break;

            case ACTION_SEARCH:
                InputManager.triggerVirtualKeypress(KeyEvent.KEYCODE_SEARCH, STANDARD_FLAGS);
                break;

            case ACTION_KILL:
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
                InputManager.triggerVirtualKeypress(KeyEvent.KEYCODE_POWER, STANDARD_FLAGS);
                break;

            case ACTION_TORCH:
//                Intent intentTorch = new Intent(TorchConstants.ACTION_TOGGLE_STATE);
//                mContext.sendBroadcast(intentTorch);
                break;

            case ACTION_LAST_APP:
                ActionUtils.switchToLastApp(mContext, mCurrentUserId);
                break;

            case ACTION_NOTIFICATIONS:
                try {
                    IStatusBarService.Stub.asInterface(
                        ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).animateNotificationsOrSettingsPanel();
                } catch (RemoteException e) {
                    Log.e(TAG, "NOTIFICATION ACTION FAILED");
                }
                break;

            case ACTION_APP:
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Intent intentapp = Intent.parseUri(action, 0);
                            intentapp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            mContext.startActivity(intentapp);
                        } catch (URISyntaxException e) {
                            Log.e(TAG, "URISyntaxException: [" + action + "]");
                        } catch (ActivityNotFoundException e) {
                            Log.e(TAG, "ActivityNotFound: [" + action + "]");
                        }
                }});
                break;

			case ACTION_IME_LAYOUT:
				try {
                    IStatusBarService.Stub.asInterface(
                            ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).notifyLayoutChange(LAYOUT_IME);
                } catch (RemoteException e) {
                }
                break;

            case ACTION_ARROW_LEFT:
                InputManager.triggerVirtualKeypress(KeyEvent.KEYCODE_DPAD_LEFT, CURSOR_FLAGS);
                break;

            case ACTION_ARROW_RIGHT:
                InputManager.triggerVirtualKeypress(KeyEvent.KEYCODE_DPAD_RIGHT, CURSOR_FLAGS);
                break;

            case ACTION_ARROW_UP:
                InputManager.triggerVirtualKeypress(KeyEvent.KEYCODE_DPAD_UP, CURSOR_FLAGS);
                break;

            case ACTION_ARROW_DOWN:
                InputManager.triggerVirtualKeypress(KeyEvent.KEYCODE_DPAD_DOWN, CURSOR_FLAGS);
                break;

            case ACTION_RING_VIB:
                final AudioManager rv = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                if (rv != null) {
                    if (rv.getRingerMode() != AudioManager.RINGER_MODE_VIBRATE) {
                        rv.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        Vibrator vib = (Vibrator) mContext
                                .getSystemService(Context.VIBRATOR_SERVICE);
                        if (vib != null) {
                            vib.vibrate(50);
                        }
                    } else {
                        rv.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                        ToneGenerator tg = new ToneGenerator(
                                AudioManager.STREAM_NOTIFICATION,
                                (int) (ToneGenerator.MAX_VOLUME * 0.85));
                        if (tg != null) {
                            tg.startTone(ToneGenerator.TONE_PROP_BEEP);
                        }
                    }
                }
                break;

            case ACTION_LAYOUT_LEFT:
                try {
                    IStatusBarService.Stub.asInterface(
                            ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).notifyLayoutChange(LAYOUT_LEFT);
                } catch (RemoteException e) {
                }
                break;

            case ACTION_LAYOUT_RIGHT:
                try {
                    IStatusBarService.Stub.asInterface(
                            ServiceManager.getService(mContext.STATUS_BAR_SERVICE)).notifyLayoutChange(LAYOUT_RIGHT);
                } catch (RemoteException e) {
                }
                break;

            case ACTION_IME:
                mContext.sendBroadcast(new Intent(
                        "android.settings.SHOW_INPUT_METHOD_PICKER"));
                break;

            case ACTION_RING_SILENT:
                final AudioManager rs = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                if (rs != null) {
                    if (rs.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                        rs.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    } else {
                        rs.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
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
                final AudioManager rvs = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
                if (rvs != null) {
                    if (rvs.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                        rvs.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                        Vibrator vib = (Vibrator) mContext
                                .getSystemService(Context.VIBRATOR_SERVICE);
                        if (vib != null) {
                            vib.vibrate(50);
                        }
                    } else if (rvs.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
                        rvs.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    } else {
                        rvs.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
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

            case ACTION_NULL:
            case ACTION_BLANK:
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
}
