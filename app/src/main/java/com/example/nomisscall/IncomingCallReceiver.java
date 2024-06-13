package com.example.nomisscall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Map;

public class IncomingCallReceiver extends BroadcastReceiver {

    Context context;
    private static final int ESP32_PORT = 8888;
    int count = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        this.context = context;
        // Check if the received intent has the expected action string
        if (intent.getAction() != null && intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (state != null && state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

                // Retrieve stored numbers and their associated messages
                SharedPreferences sharedPreferences = context.getSharedPreferences("ContactMessages", Context.MODE_PRIVATE);
                Map<String, ?> allEntries = sharedPreferences.getAll();

                // Check if the incoming number matches any stored number
                for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
                    String storedNumber = entry.getKey();
                    String storedMessage = entry.getValue().toString();

                    // Compare incoming number with stored numbers
                    if (incomingNumber != null && incomingNumber.equals(storedNumber) && count == 1) {
                        // Send message to ESP32
                        new SendMessageTask().execute(storedMessage);
                        showToast("Message has sent");
                        showToast(storedMessage);
                        break; // Break the loop if a match is found
                    }
                }
                count++;
            }
        }
    }

    private void showToast(String message) {
        // Display a toast message on the UI thread
        if (context != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        }
    }

    private static class SendMessageTask extends AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... params) {
            String message = params[0];
            try {
                Socket socket = new Socket("192.168.114.95", ESP32_PORT);
                OutputStream outputStream = socket.getOutputStream();
                outputStream.write(message.getBytes());
                outputStream.close();
                socket.close();
            } catch (UnknownHostException e) {
                Log.e("SendMessageTask", "Unknown host: Check ESP32 IP address", e);
            } catch (IOException e) {
                Log.e("SendMessageTask", "Error sending message to ESP32", e);
            }
            return null;
        }
    }
}
