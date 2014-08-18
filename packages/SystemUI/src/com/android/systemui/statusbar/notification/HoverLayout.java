/*
 * Copyright (C) 2014 ParanoidAndroid Project.
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

package com.android.systemui.statusbar.notification;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.RemoteException;
import android.service.notification.StatusBarNotification;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.RelativeLayout;

import com.android.systemui.ExpandHelper;
import com.android.systemui.R;
import com.android.systemui.SwipeHelper;

/*
 * Hover container
 * Handles touch eventing for hover.
 */
public class HoverLayout extends RelativeLayout implements ExpandHelper.Callback {

    private ExpandHelper mExpandHelper; /* Y axis (expander) */
    private SwipeHelper mSwipeHelper; /* X axis (swiper, to dismiss) */

    private Context mContext;
    private Hover mHover;

    private boolean mTouchOutside;
    private boolean mExpanded;

    public HoverLayout(Context context) {
        super(context, null);
    }

    /**
     * Creates the hover container
     * @Param context the current Context
     * @Param attrs the current AttributeSet
     */
    public HoverLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mTouchOutside = false;
        mExpanded = false;

        final Resources res = mContext.getResources();
        int minHeight = res.getDimensionPixelSize(R.dimen.default_notification_min_height);
        int maxHeight = res.getDimensionPixelSize(R.dimen.default_notification_row_max_height);

        mExpandHelper = new ExpandHelper(mContext, this, minHeight, maxHeight);
        mExpandHelper.setForceOneFinger(true);
        float densityScale = res.getDisplayMetrics().density;
        float pagingTouchSlop = ViewConfiguration.get(mContext).getScaledPagingTouchSlop();
        mSwipeHelper = new SwipeHelper(SwipeHelper.X, new SwipeHelperCallbackX(), densityScale, pagingTouchSlop);
    }

    public void setHoverContainer(Hover hover) {
        mHover = hover;
    }

    public boolean getTouchOutside() {
        return mTouchOutside;
    }

    public void setTouchOutside(boolean touch) {
        mTouchOutside = touch;
    }

    public boolean getExpanded() {
        return mExpanded;
    }

    public void setExpanded(boolean expanded) {
        mExpanded = expanded;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        boolean intercept = super.onInterceptTouchEvent(event); // call super to consume touch

        if (mHover.isAnimatingVisibility() || mHover.isHiding()) return intercept;

        intercept |= (mExpandHelper.onInterceptTouchEvent(event) |
                mSwipeHelper.onInterceptTouchEvent(event));

        return intercept;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean touch = super.onTouchEvent(event); // call super to consume touch

        if (mHover.isAnimatingVisibility() || mHover.isHiding()) return touch;

        int action = event.getAction(); // get touch input

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_OUTSIDE:
                if (!mTouchOutside) {
                    mHover.clearHandlerCallbacks();
                    // hide Hover after 1.25 seconds
                    mHover.startMicroHideCountdown();
                    mTouchOutside = true;
                }

                return touch;
        }

        touch |= mExpandHelper.onTouchEvent(event) |
                mSwipeHelper.onTouchEvent(event);

        return touch;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        float densityScale = getContext().getResources().getDisplayMetrics().density;
        mSwipeHelper.setDensityScale(densityScale);
        float pagingTouchSlop = ViewConfiguration.get(getContext()).getScaledPagingTouchSlop();
        mSwipeHelper.setPagingTouchSlop(pagingTouchSlop);
    }

    // ExpandHelper.Callback methods

    @Override
    public View getChildAtRawPosition(float x, float y) {
        return getChildAtPosition(x, y);
    }

    @Override
    public View getChildAtPosition(float x, float y) {
        return mHover.getCurrentLayout();
    }

    @Override
    public boolean canChildBeExpanded(View v) {
        return mHover.getCurrentNotification().getEntry().row.isExpandable();
    }

    @Override
    public void setUserExpandedChild(View v, boolean userExpanded) {
        mExpanded = userExpanded;
    }

    @Override
    public void setUserLockedChild(View v, boolean userLocked) {
        if (userLocked) { // lock it and clear countdowns
            mTouchOutside = false; // reset
            mHover.setLocked(userLocked);
            mHover.clearHandlerCallbacks();
        } else { // unlock and process next notification
            mTouchOutside = false; // reset
            mHover.setLocked(userLocked);
            mHover.clearHandlerCallbacks();
            mHover.processOverridingQueue(mExpanded);
        }
    }

    // SwipeHelper.Callback methods

    private class SwipeHelperCallbackX implements SwipeHelper.Callback {
        @Override
        public View getChildAtPosition(MotionEvent ev) {
            return getChildContentView(null);
        }

        @Override
        public View getChildContentView(View v) {
            return mHover.getCurrentLayout();
        }

        @Override
        public boolean canChildBeDismissed(View v) {
            return mHover.isClearable();
        }

        @Override
        public void onChildDismissed(View v) {
            mTouchOutside = false; // reset
            mHover.clearHandlerCallbacks();
            mHover.setAnimatingVisibility(false);
            mHover.setLocked(false);

            // better to store the current notification from Hover class in another object
            // so it can't be null if something happens when we get it from the array
            StatusBarNotification n = mHover.getCurrentNotification().getContent();
            if (n.isClearable() && !direction) { // remove only removable on dismiss
                final String pkg = n.getPackageName();
                final String tag = n.getTag();
                final int id = n.getId();
                try {
                    mHover.getStatusBarService().onNotificationClear(pkg, tag, id);
                } catch (RemoteException ex) {
                    // system process is dead if we're here.
                }
            }
            // quickly remove layout
            mHover.dismissHover(false, false);
        }

        @Override
        public void onBeginDrag(View v) {
            mTouchOutside = false; //reset
            requestDisallowInterceptTouchEvent(true);
            mHover.setLocked(true);
            mHover.clearHandlerCallbacks();
        }

        @Override
        public void onDragCancelled(View v) {
            mTouchOutside = false; // reset
            mHover.setLocked(false);
            mHover.clearHandlerCallbacks();
            mHover.processOverridingQueue(mExpanded);
        }
    }
}
