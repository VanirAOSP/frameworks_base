package com.android.server.os;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManagerGlobal;
import android.os.Handler;
import android.os.IBlurService;
import android.view.Display;
import android.view.DisplayInfo;
import android.view.SurfaceControl;
import android.util.Log;

import android.renderscript.Allocation;
import android.renderscript.Allocation.MipmapControl;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

import java.util.concurrent.ExecutionException;

public class BlurService extends IBlurService.Stub {
    private static final String TAG = "blur";
    private static final boolean DEBUG = false;

    private Context mContext;

    private DisplayInfo mDisplayInfo;
    private Handler mHandler;

    private final int BLUR_WIDTH = 300;
    private final int BLUR_HEIGHT = 550;

    private Bitmap mTemp;

    private int fullW = 0;
    private int fullH = 0;

    // Preload wallpaper
    private Bitmap mWallpaper;

    public BlurService(Context context) {
    mContext = context;
        mHandler = new Handler();

    if (DEBUG) Log.d(TAG, "Blur service up");
    }

    @Override
    public Bitmap prepare() {
    getFullSize();
    if (fullW == 0) {
        fullW = BLUR_WIDTH + 1;
        fullH = BLUR_HEIGHT + 1;
    }
    mTemp = SurfaceControl.screenshot(fullW, fullH, 0, 22000);
    return mTemp;
    }

    @Override
    public Bitmap getFullBlurBmp(int radius) {
    if (fullW < BLUR_WIDTH || fullH < BLUR_HEIGHT) {
            if (DEBUG) Log.d(TAG, "Blurring wallpaper");
        return getBlurWallpaper(radius);
    }
        if (DEBUG) Log.d(TAG, "Blurring screenshot");
    return blurIt(mTemp, radius);
    }

    @Override
    public Bitmap getBlurBmp(Bitmap bmp, int radius) {
    return blurIt(bmp, radius);
    }

    @Override
    public Bitmap getBlurWallpaper(int radius) {
        WallpaperManager wallpaperManager = WallpaperManager.getInstance(mContext);
        Drawable temp = wallpaperManager.getDrawable();
        mWallpaper = convertToBitmap(temp);

    return blurIt(mWallpaper, radius);
    }

    private void getFullSize() {
    mDisplayInfo = DisplayManagerGlobal.getInstance().getDisplayInfo(
            Display.DEFAULT_DISPLAY);
    if (mDisplayInfo == null) {
            if (DEBUG) Log.d(TAG, "DisplayInfo is null... bailing out");
        return;
    }

        fullW = mDisplayInfo.getNaturalWidth();
        fullH = mDisplayInfo.getNaturalHeight();

        if (DEBUG) Log.d(TAG, fullW + " " + fullH);
    }

    private Bitmap blurIt(Bitmap bmp, int radius) {
            RenderScript rs = RenderScript.create(mContext);
            ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
            Bitmap tmpBmp = bmp;

            if (bmp.getWidth() > BLUR_WIDTH)
                 tmpBmp = bmp.createScaledBitmap(bmp, BLUR_WIDTH, BLUR_HEIGHT, false);

            Bitmap out = Bitmap.createBitmap(tmpBmp);

            Allocation input = Allocation.createFromBitmap(
                    rs, tmpBmp, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
            Allocation output = Allocation.createTyped(rs, input.getType());

            script.setInput(input);
            script.setRadius(radius);
            script.forEach(output);

            output.copyTo(out);
            return out;
    }

    private Bitmap convertToBitmap(Drawable drawable) {
        Bitmap mutableBitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(mutableBitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);

        return mutableBitmap;
    }
}
