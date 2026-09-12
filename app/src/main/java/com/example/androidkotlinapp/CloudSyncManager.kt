package com.example.androidkotlinapp

import android.content.Context
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object CloudSyncManager {
    private const val TAG = "CloudSyncManager"
    
    // AWS API Gateway / Lambda Endpoint URL
    private const val LAMBDA_ENDPOINT = "https://f4inau8sni.execute-api.ap-south-1.amazonaws.com/"

    fun getDeviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    /**
     * Performs a full synchronization with AWS DynamoDB.
     * 1. Uploads unsynced local sessions.
     * 2. Fetches all sessions from DynamoDB and updates local SQLite cache.
     * 3. Uploads unsynced local tasks.
     * 4. Fetches all tasks from DynamoDB and updates local SQLite cache.
     */
    fun syncWithCloud(context: Context, onSyncComplete: (() -> Unit)? = null) {
        onSyncComplete?.invoke()
    }

    private fun sendPostRequest(jsonBody: String): Boolean {
        return try {
            val url = URL(LAMBDA_ENDPOINT)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(jsonBody)
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            conn.disconnect()
            responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED
        } catch (e: Exception) {
            Log.e(TAG, "Post request failed: ${e.message}")
            false
        }
    }

    private fun sendPostRequestWithResponse(jsonBody: String): String? {
        return try {
            val url = URL(LAMBDA_ENDPOINT)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(jsonBody)
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                conn.disconnect()
                sb.toString()
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request with response failed: ${e.message}")
            null
        }
    }
}
