/*
 * Copyright (C) 2014 VanirAOSP && the Android Open Source Project
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

package com.android.systemui.vanir.contacts;

import android.os.Parcel;
import android.os.Parcelable;

public class Contact implements Parcelable {
	private final int    mId;
	private final int    mIcon;
	private final String mName;
	
	public Contact(int id, int icon, String name) {
		mId     = id;
		mIcon   = icon;
		mName   = name;
	}
	
	public int getId() {
		return mId;
	}
	
	public int getIcon() {
		return mIcon;
	}
	
	public String getName() {
		return mName;
	}
	
	// -- Parcel
	
	private Contact(Parcel in) {
		mId     = in.readInt();
		mIcon   = in.readInt();
		mName   = in.readString();
	}
	
	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(mId);
		dest.writeInt(mIcon);
		dest.writeString(mName);
	}
	
	// -- Parcelable
	
	@Override
	public int describeContents() {
		return 0;
	}
	
	public static final Parcelable.Creator<Contact> CREATOR = new Parcelable.Creator<Contact>() {
		public Contact createFromParcel(Parcel in) {
			return new Contact(in);
		}
		
		public Contact[] newArray(int size) {
			return new Contact[size];
		}
	};
}
