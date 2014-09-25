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

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.android.systemui.R;
import com.android.systemui.vanir.contacts.ContactListInfo;

public class QuickContactsActivity extends Activity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.contacts_activity_main);

        // TODO: set the layout for each contact in their info
		ViewGroup people_1 = (ViewGroup) findViewById(R.id.people_row_1);
        ViewGroup people_2 = (ViewGroup) findViewById(R.id.people_row_2);
        ViewGroup people_3 = (ViewGroup) findViewById(R.id.people_row_3);
        ViewGroup people_4 = (ViewGroup) findViewById(R.id.people_row_4);
        ViewGroup people_5 = (ViewGroup) findViewById(R.id.people_row_5);

        int length = ContactListInfo.PEOPLE.length; /* +1 in the loop for the add button */
        ViewGroup container = (ViewGroup) findViewById(R.id.people_row_1);
        Contact contact;

		for (int i = 0; i < length; i++) {
            contact = ContactListInfo.PEOPLE[i];

            if (i<=3) {
                container = people_1;
            } else if (i>3 && i<=7) {
                container = people_2;
            } else if (i>7 && i<=11) {
                container = people_3;
            } else if (i>11 && i<=15) {
                container = people_4;
            } else if (i>15 && i<=19) {
                container = people_5;
            }
            container.addView(ContactListInfo.inflatePersonView(this, container, contact));
		}

        final RelativeLayout layout = (RelativeLayout) findViewById(R.id.quick_access_contacts_main);
        layout.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View arg0, MotionEvent event) {
                finish();
                return false;
            }
        });

        final LinearLayout dialogLayout = (LinearLayout) findViewById(R.id.custom_contacts_dialog);
        dialogLayout.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Eat touches to the actual dialog
                return true;
            }
        });

/*      Add and cancel buttons. use for app window?
        
        final Button addButton = (Button) dialogLayout.findViewById(R.id.contacts_button1);
        addButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // fire intent to chooser new contact
            }
        });

        final Button cancelButton = (Button) dialogLayout.findViewById(R.id.contacts_button2);
        cancelButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
*/	}
}
