/*
 * VoIP.ms SMS
 * Copyright � 2015 Michael Kourlas
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the
 * Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR
 * OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package net.kourlas.voipms_sms.activities;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.InputType;
import android.util.SparseBooleanArray;
import android.view.*;
import android.widget.*;
import com.example.android.floatingactionbuttonbasic.FloatingActionButton;
import net.kourlas.voipms_sms.Api;
import net.kourlas.voipms_sms.Preferences;
import net.kourlas.voipms_sms.R;
import net.kourlas.voipms_sms.RefreshReceiver;
import net.kourlas.voipms_sms.adapters.ConversationsListViewAdapter;
import net.kourlas.voipms_sms.adapters.SmsDatabaseAdapter;
import net.kourlas.voipms_sms.model.Conversation;
import net.kourlas.voipms_sms.model.Sms;

public class ConversationsActivity extends Activity {
    private Api api;
    private SmsDatabaseAdapter smsDatabaseAdapter;
    private ConversationsListViewAdapter conversationsListViewAdapter;
    private Preferences preferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.conversations);

        final Activity conversationsActivity = this;

        api = new Api(this);
        smsDatabaseAdapter = new SmsDatabaseAdapter(this);
        smsDatabaseAdapter.open();
        conversationsListViewAdapter = new ConversationsListViewAdapter(this);

        preferences = new Preferences(this);
        final Preferences preferences = this.preferences;

        SwipeRefreshLayout swipeRefreshLayout = (SwipeRefreshLayout) findViewById(
                R.id.swipe_refresh_layout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                api.updateSmses();
            }
        });

        final ListView listView = (ListView) findViewById(R.id.list);
        listView.setAdapter(conversationsListViewAdapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Conversation conversation = (Conversation) listView.getAdapter().getItem(position);
                String contact = conversation.getContact();

                Intent intent = new Intent(conversationsActivity, ConversationActivity.class);
                intent.putExtra("contact", contact);
                startActivity(intent);
            }
        });
        listView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            private void updateReadUnreadButton(ActionMode mode) {
                int read = 0;
                int unread = 0;

                SparseBooleanArray checkedItemPositions = listView.getCheckedItemPositions();
                for (int i = 0; i < conversationsListViewAdapter.getCount(); i++) {
                    if (checkedItemPositions.get(i)) {
                        Conversation conversation = (Conversation) conversationsListViewAdapter.getItem(i);
                        if (conversation.isUnread()) {
                            unread++;
                        } else {
                            read++;
                        }
                    }
                }

                MenuItem item = mode.getMenu().findItem(R.id.mark_read_unread_button);
                if (read > unread) {
                    item.setIcon(R.drawable.ic_markunread_white_24dp);
                    item.setTitle(R.string.conversations_action_mark_unread);
                } else {
                    item.setIcon(R.drawable.ic_drafts_white_24dp);
                    item.setTitle(R.string.conversations_action_mark_read);
                }
            }

            @Override
            public void onItemCheckedStateChanged(ActionMode mode, int position,
                                                  long id, boolean checked) {
                updateReadUnreadButton(mode);
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.mark_read_unread_button:
                        SparseBooleanArray checkedItemPositions = listView.getCheckedItemPositions();
                        for (int i = 0; i < conversationsListViewAdapter.getCount(); i++) {
                            if (checkedItemPositions.get(i)) {
                                Conversation conversation = (Conversation) conversationsListViewAdapter.getItem(i);
                                Sms[] smses = conversation.getAllSms();
                                for (Sms sms : smses) {
                                    sms.setUnread(item.getTitle().equals(getResources().getString(
                                            R.string.conversations_action_mark_unread)));
                                    smsDatabaseAdapter.replaceSms(sms);
                                }
                            }
                        }
                        conversationsListViewAdapter.refresh();
                        updateReadUnreadButton(mode);
                        mode.finish();
                        return true;
                    case R.id.delete_button:
                        checkedItemPositions = listView.getCheckedItemPositions();
                        for (int i = 0; i < checkedItemPositions.size(); i++) {
                            if (checkedItemPositions.get(i)) {
                                Conversation conversation = (Conversation) conversationsListViewAdapter.getItem(i);
                                for (Sms sms : conversation.getAllSms()) {
                                    api.deleteSms(sms.getId());
                                }
                            }
                        }
                        mode.finish();
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.conversations_secondary, menu);
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                // Do nothing.
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }
        });

        FloatingActionButton button = (FloatingActionButton) findViewById(R.id.new_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (preferences.getEmail().equals("")) {
                    Toast.makeText(getApplicationContext(), "New Conversation: VoIP.ms portal email not set",
                            Toast.LENGTH_SHORT).show();
                } else if (preferences.getPassword().equals("")) {
                    Toast.makeText(getApplicationContext(), "New Conversation: VoIP.ms API password not set",
                            Toast.LENGTH_SHORT).show();
                } else if (preferences.getDid().equals("")) {
                    Toast.makeText(getApplicationContext(), "New Conversation: DID not set",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Intent intent = new Intent(conversationsActivity, NewConversationActivity.class);
                    startActivity(intent);
                }
            }
        });

        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent("net.kourlas.voipms_sms.REFRESH"),
                0);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);

        if (preferences.getPollRate() != 0) {
            alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() +
                    preferences.getPollRate() * 60 * 1000, preferences.getPollRate() * 60 * 1000, pendingIntent);
        }

        if (preferences.getFirstRun()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Before using this app, make sure to set your VoIP.ms portal email address and API " +
                    "password in Settings.\n\nAfter setting your email and password, you will also need to pick an " +
                    "SMS-enabled DID by tapping the Select DID option in the menu at the upper right-hand corner of " +
                    "this screen.");
            builder.setNegativeButton("Settings", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    Intent preferencesIntent = new Intent(conversationsActivity, PreferencesActivity.class);
                    startActivity(preferencesIntent);
                    preferences.setFirstRun(false);
                }
            });
            builder.setPositiveButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    preferences.setFirstRun(false);
                }
            });
            builder.show();
        }
    }

    public void onPause() {
        getPackageManager().setComponentEnabledSetting(new ComponentName(this, RefreshReceiver.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        super.onPause();
    }

    public void onResume() {
        conversationsListViewAdapter.refresh();

        if (!preferences.getFirstRun()) {
            api.updateSmses();
        }

        getPackageManager().setComponentEnabledSetting(new ComponentName(this, RefreshReceiver.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.conversations, menu);

        SearchView searchView = (SearchView) menu.findItem(R.id.search_button).getActionView();
        searchView.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                conversationsListViewAdapter.refresh(newText);
                return true;
            }
        });

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.select_did_button:
                api.updateDid();
                return true;
            case R.id.preferences_button:
                Intent preferencesIntent = new Intent(this, PreferencesActivity.class);
                startActivity(preferencesIntent);
                return true;
            case R.id.help_button:
                Intent helpIntent = new Intent(this, HelpActivity.class);
                startActivity(helpIntent);
                return true;
            case R.id.credits_button:
                Intent creditsIntent = new Intent(this, CreditsActivity.class);
                startActivity(creditsIntent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public ConversationsListViewAdapter getConversationsListViewAdapter() {
        return conversationsListViewAdapter;
    }
}
