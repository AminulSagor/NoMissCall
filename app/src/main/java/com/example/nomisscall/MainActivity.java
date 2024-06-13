package com.example.nomisscall;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.ContentResolver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;


public class MainActivity extends AppCompatActivity {

    private ContactInfo contactToRemove;

    EditText editText,messageWriting;
    Button searchButton,setMessage;
    private static final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 123;
    private static final int MY_PERMISSIONS_REQUEST_INTERNET = 234;

    RecyclerView recyclerView;
    private static final int MY_PERMISSIONS_REQUEST_READ_CALL_LOG = 789;
    private static final int MY_PERMISSIONS_REQUEST_READ_PHONE_STATE = 456;
    private IncomingCallReceiver receiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);




        editText = findViewById(R.id.number);
        searchButton = findViewById(R.id.addBtn);
        recyclerView = findViewById(R.id.addedContact);
        setMessage=findViewById(R.id.setMessage);
        messageWriting=findViewById(R.id.messageWriting);

        // Check if the app has permission to access the internet
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            // Request the permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.INTERNET},
                    MY_PERMISSIONS_REQUEST_INTERNET);
        }



        checkAndRequestPhoneStatePermission();


        checkAndRequestCallLogPermission();


        // Register the IncomingCallReceiver
        IntentFilter filter = new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        receiver = new IncomingCallReceiver();
        registerReceiver(receiver, filter);



        // Retrieve and display existing contacts when the app starts
        List<ContactInfo> contactInfoList = getStoredContacts();
        if (contactInfoList != null) {
            ContactAdapter adapter = new ContactAdapter(contactInfoList);
            recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
            recyclerView.setAdapter(adapter);
        }




        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Check if the permission is not granted
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_CONTACTS)
                        != PackageManager.PERMISSION_GRANTED) {
                    // Request the permission
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.READ_CONTACTS},
                            MY_PERMISSIONS_REQUEST_READ_CONTACTS);
                } else {
                    // Permission already granted, proceed with your functionality
                    String searchNumber = editText.getText().toString().trim();
                    boolean numberFound = checkNumberInContacts(searchNumber);

                    if (numberFound) {
                        String foundNumber = searchNumber;
                        String foundName = getContactNameFromNumber(foundNumber);

                        // Retrieve existing contacts
                        List<ContactInfo> contactInfoList = getStoredContacts();

                        if (contactInfoList == null) {
                            contactInfoList = new ArrayList<>();
                        }

                        // Check if the number already exists in the contact list
                        boolean isDuplicate = false;
                        for (ContactInfo contact : contactInfoList) {
                            if (contact.getNumber().equals(foundNumber)) {
                                isDuplicate = true;
                                break;
                            }
                        }

                        if (!isDuplicate) {
                            contactInfoList.add(new ContactInfo(foundName, foundNumber));

                            // Save the updated contact list
                            saveContacts(contactInfoList);

                            // Display the updated contact list in RecyclerView
                            ContactAdapter adapter = new ContactAdapter(contactInfoList);
                            recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                            recyclerView.setAdapter(adapter);
                        } else {
                            Toast.makeText(MainActivity.this, "Number already exists in contacts.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this, "Number not found in contacts.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        setMessage.setOnClickListener(v -> {
            // Retrieve the unique identifier (phone number in this case) from the tag
            String phoneNumber = (String) setMessage.getTag();

            // Get the message from messageWriting EditText
            String message = messageWriting.getText().toString().trim();

            // Save the message in SharedPreferences using the unique identifier
            saveMessageForContact(phoneNumber, message);

            // Hide the message writing views after saving
            setMessage.setVisibility(View.GONE);
            messageWriting.setVisibility(View.GONE);

            // Optionally, update the RecyclerView here if needed
        });

    }

    private void saveContacts(List<ContactInfo> contactList) {
        SharedPreferences sharedPreferences = getSharedPreferences("MyContacts", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(contactList);
        editor.putString("contactList", json);
        editor.apply();
    }

    // Function to retrieve contact number from SharedPreferences
    private List<ContactInfo> getStoredContacts() {
        SharedPreferences sharedPreferences = getSharedPreferences("MyContacts", MODE_PRIVATE);
        Gson gson = new Gson();
        String json = sharedPreferences.getString("contactList", null);
        Type type = new TypeToken<List<ContactInfo>>() {}.getType();
        return gson.fromJson(json, type);
    }
    private boolean checkNumberInContacts(String searchNumber) {
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                null,
                ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?",
                new String[]{searchNumber},
                null);

        boolean numberFound = cursor != null && cursor.getCount() > 0;
        if (cursor != null) {
            cursor.close();
        }

        return numberFound;
    }
    private String getContactNameFromNumber(String searchNumber) {
        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME},
                ContactsContract.CommonDataKinds.Phone.NUMBER + " = ?",
                new String[]{searchNumber},
                null);

        String contactName = "";
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            if (columnIndex != -1) {
                contactName = cursor.getString(columnIndex);
            }
            cursor.close();
        }
        return contactName;
    }

    private void saveMessageForContact(String phoneNumber, String message) {
        SharedPreferences sharedPreferences = getSharedPreferences("ContactMessages", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(phoneNumber, message);
        editor.apply();
    }

    // ContactInfo class to hold name and number together
    static class ContactInfo {
        final String name;
        final String number;

        public ContactInfo(String name, String number) {
            this.name = name;
            this.number = number;
        }

        public String getName() {
            return name;
        }

        public String getNumber() {
            return number;
        }
    }


    private class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {

        final List<ContactInfo> contactList;

        public ContactAdapter(List<ContactInfo> contacts) {
            this.contactList = contacts;
        }


        @NonNull
        @Override
        public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_number, parent, false);
            return new ContactViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
            holder.bind(contactList.get(position));

            holder.btnDelete.setOnClickListener(v -> {
                int currentPosition = holder.getAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION) {
                    ContactInfo deletedContact = contactList.remove(currentPosition);
                    notifyItemRemoved(currentPosition);
                    saveContacts(contactList);

                    // Delete corresponding SharedPreferences value
                    deleteContactAndMessage(deletedContact, contactList);
                }
            });
        }



        @Override
        public int getItemCount() {
            return contactList.size();
        }

        class ContactViewHolder extends RecyclerView.ViewHolder {
            final TextView nameTextView;
            final TextView numberTextView;
            final Button btnMessage;
            final Button btnDelete;

            public ContactViewHolder(@NonNull View itemView) {
                super(itemView);
                nameTextView = itemView.findViewById(R.id.Name);
                numberTextView = itemView.findViewById(R.id.Number);
                btnMessage=itemView.findViewById(R.id.message);
                btnDelete = itemView.findViewById(R.id.delete);
            }

            public void bind(ContactInfo contactInfo) {
                nameTextView.setText(contactInfo.getName());
                numberTextView.setText(contactInfo.getNumber());
                btnMessage.setOnClickListener(v -> {
                    setMessage.setVisibility(View.VISIBLE);
                    messageWriting.setVisibility(View.VISIBLE);

                    setMessage.setTag(contactInfo.getNumber());
                });


                btnDelete.setOnClickListener(v -> {
                    int currentPosition = getAdapterPosition();
                    contactList.remove(currentPosition);
                    notifyItemRemoved(currentPosition);

                });

            }

        }
    }

    //-------------------------------------------------------------------------

    private void checkAndRequestCallLogPermission() {
        // Check if the READ_CALL_LOG permission is not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {

            // Request the permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CALL_LOG},
                    MY_PERMISSIONS_REQUEST_READ_CALL_LOG);
        }
    }
    //-----------------------------------------------------
    private void checkAndRequestPhoneStatePermission() {
        // Check if the READ_PHONE_STATE permission is not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {

            // Request the permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_PHONE_STATE},
                    MY_PERMISSIONS_REQUEST_READ_PHONE_STATE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister the receiver to avoid multiple registrations
        if (receiver != null) {
            unregisterReceiver(receiver);
        }
    }

    private void deleteContactAndMessage(ContactInfo contactInfo,List<ContactInfo> contactList) {
        SharedPreferences sharedPreferencesContacts = getSharedPreferences("MyContacts", MODE_PRIVATE);
        SharedPreferences.Editor editorContacts = sharedPreferencesContacts.edit();
        Gson gson = new Gson();
        String jsonContacts = gson.toJson(contactList);
        editorContacts.putString("contactList", jsonContacts);
        editorContacts.apply();

        SharedPreferences sharedPreferencesMessages = getSharedPreferences("ContactMessages", MODE_PRIVATE);
        SharedPreferences.Editor editorMessages = sharedPreferencesMessages.edit();
        editorMessages.remove(contactInfo.getNumber());
        editorMessages.apply();
    }


}

