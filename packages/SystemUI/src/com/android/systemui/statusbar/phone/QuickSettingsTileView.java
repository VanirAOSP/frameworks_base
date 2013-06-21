/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

/**
 *
 */
public class QuickSettingsTileView extends FrameLayout {
    private static final String TAG = "QuickSettingsTileView";

    private int mContentLayoutId;
    private int mColSpan;
    private boolean mPrepared;
    private OnPrepareListener mOnPrepareListener;

    private static final int DEFAULT_QUICK_TILES_BG_COLOR = 0xff161616;
    private static final int DEFAULT_QUICK_TILES_BG_PRESSED_COLOR = 0xff212121;

    public QuickSettingsTileView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mContentLayoutId = -1;
        mColSpan = 1;

        int bgColor = Settings.System.getInt(context.getContentResolver(),
                Settings.System.QUICK_TILES_BG_COLOR, -2);
        int presColor = Settings.System.getInt(context.getContentResolver(),
                Settings.System.QUICK_TILES_BG_PRESSED_COLOR, -2);

        if (bgColor != -2 || presColor != -2) {
            if (bgColor == -2) {
                bgColor = DEFAULT_QUICK_TILES_BG_COLOR;
            }
            if (presColor == -2) {
                presColor = DEFAULT_QUICK_TILES_BG_COLOR;
            }
            ColorDrawable bgDrawable = new ColorDrawable(bgColor);
            ColorDrawable presDrawable = new ColorDrawable(presColor);
            StateListDrawable states = new StateListDrawable();
            states.addState(new int[] {android.R.attr.state_pressed}, presDrawable);
            states.addState(new int[] {}, bgDrawable);
            this.setBackground(states);
        }
    }

    void setColumnSpan(int span) {
        mColSpan = span;
    }

    int getColumnSpan() {
        return mColSpan;
    }

    public void setContent(int layoutId, LayoutInflater inflater) {
        mContentLayoutId = layoutId;
        inflater.inflate(layoutId, this);
    }

    void reinflateContent(LayoutInflater inflater) {
        if (mContentLayoutId != -1) {
            removeAllViews();
            setContent(mContentLayoutId, inflater);
        } else {
            Log.e(TAG, "Not reinflating content: No layoutId set");
        }
    }

    @Override
    public void setVisibility(int vis) {
        if (QuickSettings.DEBUG_GONE_TILES) {
            if (vis == View.GONE) {
                vis = View.VISIBLE;
                setAlpha(0.25f);
                setEnabled(false);
            } else {
                setAlpha(1f);
                setEnabled(true);
            }
        }
        super.setVisibility(vis);
    }

    public void setOnPrepareListener(OnPrepareListener listener) {
        if (mOnPrepareListener != listener) {
            mOnPrepareListener = listener;
            mPrepared = false;
            post(new Runnable() {
                @Override
                public void run() {
                    updatePreparedState();
                }
            });
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        updatePreparedState();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updatePreparedState();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        updatePreparedState();
    }

    private void updatePreparedState() {
        if (mOnPrepareListener != null) {
            if (isParentVisible()) {
                if (!mPrepared) {
                    mPrepared = true;
                    mOnPrepareListener.onPrepare();
                }
            } else if (mPrepared) {
                mPrepared = false;
                mOnPrepareListener.onUnprepare();
            }
        }
    }

    private boolean isParentVisible() {
        if (!isAttachedToWindow()) {
            return false;
        }
        for (ViewParent current = getParent(); current instanceof View;
                current = current.getParent()) {
            View view = (View)current;
            if (view.getVisibility() != VISIBLE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Called when the view's parent becomes visible or invisible to provide
     * an opportunity for the client to provide new content.
     */
    public interface OnPrepareListener {
        void onPrepare();
        void onUnprepare();
    }
}
