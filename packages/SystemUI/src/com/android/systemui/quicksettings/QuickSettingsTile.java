package com.android.systemui.quicksettings;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.Animator.AnimatorListener;
import android.app.ActivityManagerNative;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.R;
import com.android.systemui.statusbar.phone.QuickSettingsController;
import com.android.systemui.statusbar.phone.PhoneStatusBar;
import com.android.systemui.statusbar.phone.QuickSettingsContainerView;
import com.android.systemui.statusbar.phone.QuickSettingsTileView;

public class QuickSettingsTile implements OnClickListener {

    protected final Context mContext;
    protected QuickSettingsContainerView mContainer;
    protected QuickSettingsTileView mTile;
    protected OnClickListener mOnClick;
    protected OnLongClickListener mOnLongClick;
    protected final int mTileLayout;
    protected int mDrawable = R.drawable.ic_notifications;
    protected String mLabel;
    protected int mTileTextSize;
    protected int mTileTextPadding;
    protected PhoneStatusBar mStatusbarService;
    protected QuickSettingsController mQsc;
    private final Handler mHandler;
    private final Vibrator mVibrator;

    private static SettingsObserver mObserver;

    private class SettingsObserver extends ContentObserver {

        private final ContentResolver mResolver;
        private final Uri mFlipUri, mCollapseUri;
        private boolean mFlip, shouldCollapse;
        private int mCount;

        public void incrementCount() {
            synchronized(this) {
                if (mCount++ == 0) {
                    observe();
                }
            }
        }

        public boolean decrementCount() {
            synchronized(this) {
                if (--mCount == 0) {
                    unobserve();
                }
            }
            return mCount == 0;
        }

        public boolean getFlip() { return mFlip; }
        public boolean getCollapse() { return shouldCollapse; }

        public SettingsObserver(Context context, Handler handler) {
            super(handler);
            mResolver = context.getContentResolver();
            mFlipUri = Settings.System.getUriFor(Settings.System.QUICK_SETTINGS_TILES_FLIP);
            mCollapseUri = Settings.System.getUriFor(Settings.System.QS_COLLAPSE_PANEL);
        }

        private void observe() {
            mResolver.registerContentObserver(mFlipUri, false, this);
            mResolver.registerContentObserver(mCollapseUri, false, this);

            mFlip = Settings.System.getInt(mResolver,
                Settings.System.QUICK_SETTINGS_TILES_FLIP, 0) == 1;
            shouldCollapse = Settings.System.getIntForUser(mResolver,
                Settings.System.QS_COLLAPSE_PANEL, 0, UserHandle.USER_CURRENT) == 1;
        }

        private void unobserve() {
            mResolver.unregisterContentObserver(this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(mFlipUri)) {
                mFlip = Settings.System.getInt(mResolver,
                        Settings.System.QUICK_SETTINGS_TILES_FLIP, 0) == 1;
            } else {
                shouldCollapse = Settings.System.getIntForUser(mResolver,
                        Settings.System.QS_COLLAPSE_PANEL, 0, UserHandle.USER_CURRENT) == 1;
            }
        }
    }

    public QuickSettingsTile(Context context, QuickSettingsController qsc) {
        this(context, qsc, R.layout.quick_settings_tile_basic);
    }

    public QuickSettingsTile(Context context, QuickSettingsController qsc, int layout) {
        mContext = context;
        mLabel = mContext.getString(R.string.quick_settings_label_enabled);
        mStatusbarService = qsc.mStatusBarService;
        mQsc = qsc;
        mTileLayout = layout;
        mHandler = new Handler();
        mVibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        if (mObserver == null) {
            mObserver = new SettingsObserver(context, mHandler);
        }
    }

    public void setupQuickSettingsTile(LayoutInflater inflater,
            QuickSettingsContainerView container) {
        container.updateResources();
        mTileTextSize = container.getTileTextSize();
        mTileTextPadding = container.getTileTextPadding();

        mTile = (QuickSettingsTileView) inflater.inflate(
                R.layout.quick_settings_tile, container, false);
        mTile.setContent(mTileLayout, inflater);
        mContainer = container;
        mContainer.addView(mTile);
        onPostCreate();
        updateQuickSettings();
        mTile.setOnClickListener(this);
        mTile.setOnLongClickListener(mOnLongClick);
    }

    public void switchToRibbonMode() {
        TextView tv = (TextView) mTile.findViewById(R.id.text);
        if (tv != null) {
            tv.setVisibility(View.GONE);
        }
        View image = mTile.findViewById(R.id.image);
        if (image != null) {
            MarginLayoutParams params = (MarginLayoutParams) image.getLayoutParams();
            int margin = mContext.getResources().getDimensionPixelSize(
                    R.dimen.qs_tile_ribbon_icon_margin);
            params.topMargin = params.bottomMargin = margin;
            image.setLayoutParams(params);
        }
    }

    void onPostCreate() {
        mObserver.incrementCount();
    }

    public void onDestroy() {
        if (mObserver.decrementCount()) {
            mObserver = null;
        }
    }

    public void onReceive(Context context, Intent intent) {}

    public void onChangeUri(ContentResolver resolver, Uri uri) {}

    public void updateResources() {
        if (mTile != null) {
            updateQuickSettings();
        }
    }

    void updateQuickSettings() {
        TextView tv = (TextView) mTile.findViewById(R.id.text);
        if (tv != null) {
            tv.setText(mLabel);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTileTextSize);
            tv.setPadding(0, mTileTextPadding, 0, 0);
        }
        View image = mTile.findViewById(R.id.image);
        if (image != null && image instanceof ImageView) {
            ((ImageView) image).setImageResource(mDrawable);
        }
    }

    public void flipTile(int delay){
        final AnimatorSet anim = (AnimatorSet) AnimatorInflater.loadAnimator(
                mContext, R.anim.flip_down);
        anim.setTarget(mTile);
        anim.setDuration(200);

        anim.addListener(new AnimatorListener(){
                @Override
                public void onAnimationEnd(Animator animation) {}
                @Override
                public void onAnimationStart(Animator animation) {}
                @Override
                public void onAnimationCancel(Animator animation) {}
                @Override
                public void onAnimationRepeat(Animator animation) {}
        });

        Runnable doAnimation = new Runnable(){
            @Override
            public void run() {
                anim.start();
            }
        };

        mHandler.postDelayed(doAnimation, delay);
    }

    void startSettingsActivity(String action) {
        Intent intent = new Intent(action);
        startSettingsActivity(intent);
    }

    void startSettingsActivity(Intent intent) {
        startSettingsActivity(intent, true);
    }

    private void startSettingsActivity(Intent intent, boolean onlyProvisioned) {
        if (onlyProvisioned && !mStatusbarService.isDeviceProvisioned()) return;
        try {
            // Dismiss the lock screen when Settings starts.
            ActivityManagerNative.getDefault().dismissKeyguardOnNextActivity();
        } catch (RemoteException e) {
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivityAsUser(intent, new UserHandle(UserHandle.USER_CURRENT));
        mStatusbarService.animateCollapsePanels();
    }

    public void setColors(int bgColor, int presColor) {
        if (mTile != null) {
            mTile.setColors(bgColor, presColor);
        }
    }

    @Override
    public void onClick(View v) {
        if (mObserver.getFlip()) {
            flipTile(0);
        }

        if (mOnClick != null) {
            mOnClick.onClick(v);
        }

        if (mObserver.getCollapse()) {
            mQsc.mBar.collapseAllPanels(true);
        }

        vibrateTile(30);
    }

    // tiles can override this where it makes sense
    protected boolean isVibrationEnabled() {
        return (Settings.System.getInt(mContext.getContentResolver(),
                Settings.System.QUICK_SETTINGS_TILES_VIBRATE, 0) == 1);
    }

    private void vibrateTile(int duration) {
        if (mVibrator != null
                || !mVibrator.hasVibrator()
                || !isVibrationEnabled())
            return;
        mVibrator.vibrate(duration);
        }
    }
}
