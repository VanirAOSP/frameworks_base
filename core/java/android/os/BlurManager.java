package android.os;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.IBlurService;
import android.util.Log;

public class BlurManager {
    public static final boolean DEBUG = false;
    public static final String TAG = "blur_manager";

    private Context mContext;
    private IBlurService mService;

    private OnBitmapReady mListener;

    public BlurManager(Context context, IBlurService service) {
        mContext = context;
        mService = service;
    }

    public Bitmap prepare() {
        Bitmap prepare = null;
        try {
            if ((prepare = mService.prepare()) != null) {
                if (DEBUG) Log.d(TAG, "bitmap to be blurred is ready!");
            }
            return prepare;
        } catch (RemoteException ex) {
            return prepare;
        }
    }

    public Bitmap getFullBlurBmp(int radius) {
    Bitmap blur = null;
        try {
        blur = mService.getFullBlurBmp(radius);
            if (blur != null && mListener != null) mListener.onBitmapReady(blur);
        return blur;
        } catch (RemoteException ex) {
            return blur;
        }
    }

    public Bitmap getBlurBmp(Bitmap bmp, int radius) {
        Bitmap blur = null;
        try {
        blur = mService.getBlurBmp(bmp, radius);
            if (blur != null && mListener != null) mListener.onBitmapReady(blur);
            return blur;
        } catch (RemoteException ex) {
            return blur;
        }
    }

    public Bitmap getBlurWallpaper(int radius) {
    Bitmap blur = null;
        try {
        blur = mService.getBlurWallpaper(radius);
            if (blur != null && mListener != null) mListener.onBitmapReady(blur);
            return blur;
        } catch (RemoteException ex) {
            return blur;
        }
    }

    public void setOnBitmapReady(OnBitmapReady callback) {
    mListener = callback;
    }

    public interface OnBitmapReady {
    public void onBitmapReady(Bitmap bitmap);
    }
}
