package android.os;

import android.graphics.Bitmap;

interface IBlurService {
    Bitmap prepare();
    Bitmap getFullBlurBmp(int radius);
    Bitmap getBlurBmp(in Bitmap bmp, int radius);
    Bitmap getBlurWallpaper(int radius);
}
