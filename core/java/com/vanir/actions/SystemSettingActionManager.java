/*
 * Copyright (C) 2014 VanirAOSP
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.vanir.actions;

import java.util.HashMap;
import java.util.ArrayList;

public class SystemSettingActionManager extends ActionManager {
    private int mCount;

    private static final String GENERAL_NUMBERED_SETTING_FORMAT = "%s_%s[%d]";
    private static final String NUMBERLESS_SETTING_FORMAT = "%s_%s";

    protected SettingsObserver mCountObserver;
    protected ArrayList<PerActionSettingsObserver> mActionObservers = new ArrayList<PerActionSettingsObserver>();
    protected ArrayList<IAction> mActions = new ArrayList<SettingsObserver>();

    private class SettingsObserver extends ContentObserver {
        private ContentResolver mResolver;
        private string mCountSettingString;

        public SettingsObserver(ContentResolver res) {
            mResolver = res;
            mCountSettingString = formatUri("COUNT");
            Uri counturi = Settings.System.getUri(mCountSettingString);
            mResolver.registerContentObserver(Settings.System.getUri(counturi), false, this);
            onChange(false);
        }

        @Override
        public boolean onChange(boolean selfChange) {
            int count = mActionObservers.size();
            mCount = Settings.System.getInt(mResolver, mCountSettingString, 0);
            if (count > mCount) {
                // unregister and remove irrelevant per-slot observers
                for(int i=mCount; i<count; i++) {
                    mActionObservers.get(i).unregister();
                }
                // drop extras
                mActionObservers.resize(mCount);
                mActions.resize(mCount);
                // notify watchers
                updateAll(mActions.toArray());
            } else if (count < mCount) {
                // add a new action, and an observer to handle its system settings
                for(int i=count,l=mCount; i<l; i++) {
                    mActions.add(new IAction());
                    mActionObservers.add(new PerActionSettingsObserver(mResolver, i));
                }
                // notify watchers
                updateAll(mActions.toArray());
            }
        }
    }

    private class PerActionSettingsObserver extends ContentObserver {
        private final int mIndex;
        private Uri drawableUri;
        private Uri actionUri;
        private ContentResolver mResolver;

        public SettingsObserver(ContentResolver resolver, int index) {
            mIndex = index;
            mResolver = resolver;
            drawableUri = Setting.System.getUri(String.format(formatUri(mIdentifier, "DRAWABLE", index)));
            actionUri = Setting.System.getUri(String.format(formatUri(mIdentifier, "ACTION", index)));
            mResolver.registerContentObserver(drawableUri, false, this);
            mResolver.registerContentObserver(actionUri, false, this);
            onChange(true,actionUri);
            onChange(true,drawableUri);
        }

        @Override
        public boolean onChange(boolean selfChange, Uri uri) {
            if (uri.equals(drawableUri)) {
                //something resource-y
                //     Settings.System.getInt(uri) ???
                mActions.get(mIndex).setDrawable(null);
            } else if (uri.equals(actionUri)) {
                mActions.get(mIndex).setAction(new Runnable() {
                    @Override
                    public void run() {
                        Log.e(""+uri, Settings.System.getString(uri));
                    });
            } else {
                // wrong uri?! hauww?
                return;
            }
            if (selfChange) {
                return;
            }
            update(mActions.get(mIndex));
        }

        public void unregister() {
            mResolver.unregisterContentObserver(this);
        }
    }

    protected static String formatUri(String suffix, int index) {
        if (index >= mCount) {
            //no bueno
            return null;
        }
        return String.format(GENERAL_NUMBERED_SETTINGS_FORMAT, mIdentifier, suffix, index);
    }

    protected String formatUri(String suffix) {
        return String.format(NUMBERLESS_SETTINGS_FORMAT, mIdentifier, suffix);
    }

    @Override
    protected void init(Context context) {
        mCountObserver = new SettingsObserver(context.getContentResolver());
    }
}
