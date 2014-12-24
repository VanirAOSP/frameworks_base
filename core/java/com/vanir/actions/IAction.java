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

import android.content.Context;
import android.content.res.ContentObserver;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.Runnable;
import android.view.View;

import java.lang.UnsuportedOperationException;

public abstract class IAction implements Parcelable {
    private int mPosition;
    private Drawable mDrawable;
    private View mView;
    private Runnable mAction;

    public IAction() {
    }

    public IAction(Parcel in) {
        readFromParcel(in);
    }

    void readFromParcel(Parcel in) {
        this.mPosition = in.readInt();
        if (in.readBool()) {    //parcel is packed with boolean indicating whether there is or is not a drawable to read
            this.mDrawable = (Drawable)new BitmapDrawable((Bitmap)in.readParcelable(getClass().getClassLoader()));
        }
    }

    void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPosition);
        dest.writeBool(mDrawable != null);
        if (mDrawable != null) {
            dest.writeParcelable(((BitmapDrawable)mDrawable).getBitmap());
        }
    }

    //// BEGIN GETTERS

    /* returns the left-relative or otherwise-defined array position.
     *   For navbar, this would be left-relative position in parent view.
     *   For hardware buttons, this would be the binary log of the associated hardwareKey mask
     * @hide
     */
    public int getPosition() {
        return mPosition;
    }

    /* if applicable...
     * @hide
     */
    public Drawable getDrawable() {
        return mDrawable;
    }

    /* if applicable...
     * @hide
     */
    public View getView() {
        return mView;
    }

    /* "WTF TO DO"
     * @hide
     */
    public Runnable getAction() {
        return mAction;
    }

    //// END GETTERS

    //// BEGIN SETTERS

    /* sets this action's array order for view update firing, etc.
     * @hide
     */
    public void setPosition(int pos) {
        mPosition = pos;
    }

    /* sets this action's drawable... not applicable for hardware actions
     * @hide
     */
    public void setDrawable(Contect context, int resource, Drawable drawable) {
        if (resource > 0) {
            mDrawable = context.getDrawable(resource);
        } else if (drawable != null) {
            mDrawable = drawable;
        }
    }

    /* sets this action's view... not applicable for hardware actions
     * @hide
     */
    public void setView(View view) {
        mView = view;
    }


    /* sets the runnable that happens when this specific action is triggered by PWM or NBV
     * @hide
     */
    public void setAction(Runnable runnable) {
        mAction = runnable;
    }

    //// END SETTERS

    static final Parcelable.Creator<IAction> CREATOR
            = new Parcelable.Creator<IAction>() {

        IAction createFromParcel(Parcel in) {
            return new IAction(in);
        }

        IAction[] newArray(int size) {
            return new IAction[size];
        }
    };
}
