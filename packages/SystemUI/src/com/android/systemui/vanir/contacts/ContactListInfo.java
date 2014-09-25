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

import android.content.ContentResolver;
import android.content.Context;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import com.android.systemui.R;

public final class ContactListInfo {
    private String mUserString;

    // hardcode contacts for now.. use linked list later
	public static Contact[] PEOPLE = {
		new Contact(1, R.drawable.ic_lockscreen_soundon, "VanirAOSP"),
		new Contact(1, R.drawable.ic_lockscreen_soundon, "EOS"),
		new Contact(1, R.drawable.ic_lockscreen_soundon, "AOKP"),
		new Contact(1, R.drawable.ic_lockscreen_soundon, "CM"),
		new Contact(1, R.drawable.ic_lockscreen_soundon, "VAOS")
	};

    public static Contact ADD_CONTACT_BUTTON = new Contact(1, R.drawable.contacts_add, "Add");

    public void getUserContactList() {
   //     ContentResolver resolver = mContext.getContentResolver();
   //     mUserString = Settings.System.getStringForUser(resolver,
   //             Settings.System.QUICK_ACCESS_CONTACTS, UserHandle.USER_CURRENT);
   //     this should be used to set Contact[]
    }
	
	public static View inflatePersonView(Context context, ViewGroup parent, Contact person) {
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		LinearLayout personView = (LinearLayout) inflater.inflate(R.layout.contacts_button_person, parent, false);
        ImageButton personImage = (ImageButton) personView.findViewById(R.id.contact_image);
		personImage.setImageDrawable(context.getResources().getDrawable(person.getIcon()));
        TextView personText = (TextView) personView.findViewById(R.id.contact_name);
		personText.setText(person.getName());
		personImage.setOnClickListener(mClickPersonView);
		personView.setTag(person);
		return personView;
	}
	
	private static View.OnClickListener mClickPersonView = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
            // add buttons to a container for call, text, full contact info etc? or open main contact info
		}
	};
}
