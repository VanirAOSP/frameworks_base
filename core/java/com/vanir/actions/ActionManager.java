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

public abstract class ActionManager /*implements Disposable { //TODO: something smexy with a dispose override to clean up shop */ {

    //instance management
    private static HashMap<String, ActionManager> managers;
    private static Object padlock = new Object();

    public static ActionManager getManager(String identifier, Context context) {
        synchronized(padlock) {
            if (managers == null) {
                managers = new HashMap<String, ActionManager>();
            }
            if (managers.get(identifier) == null) {
                managers.put(identifier, new ActionManager(identifier, context));
            }
            return managers.get(identifier);
        }
    }
    //end of instance management

    protected ArrayList<IActionWatcher> mWatchers = new ArrayList<IActionWatcher>();
    protected String mIdentifier;
    protected Context context;

    protected ActionManager(String identifier, Context context) {
        mIdentifier = identifier;
        init(context);
    }

    protected void init(Context context) {
    }

    public void addWatcher(IActionWatcher watcher) {
        synchronized(mWatchers) {
            mWatchers.add(watcher);
        }
        // push state to fresh watcher via its init function
    }

    public void removeWatcher(IActionWatcher watcher) {
        synchronized(mWatchers) {
            if (mWatchers.has(watcher)) {
                mWatchers.remove(watcher);
            }
        }
    }

    protected void update(IAction action) {
        synchronized(mWatchers) {
            for (IActionWatcher watcher : mWatchers) {
                watcher.update(action);
            }
        }
    }

    protected void updateAll(IAction[] actions) {
        synchronized(mWatchers) {
            for (IActionWatcher watcher : mWatchers) {
                watcher.initialize(actions);
            }
        }
    }
}
