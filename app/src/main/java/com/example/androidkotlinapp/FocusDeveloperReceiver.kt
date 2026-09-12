package com.example.androidkotlinapp

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

class FocusDeveloperReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        if (action == "com.example.androidkotlinapp.FORCE_DEACTIVATE_ADMIN") {
            try {
                // 1. Remove active admin
                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val adminComponent = ComponentName(context, FocusDeviceAdminReceiver::class.java)
                dpm.removeActiveAdmin(adminComponent)
                
                // 2. Stop FocusService
                val serviceIntent = Intent(context, FocusService::class.java)
                context.stopService(serviceIntent)
                
                // 3. Clear session
                FocusService.clearSessionEndTime(context)
                
                Toast.makeText(context, "Developer Override: Admin Disabled & Session Stopped!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (action == "com.example.androidkotlinapp.STOP_FOCUS_SESSION") {
            try {
                // 1. Stop FocusService
                val serviceIntent = Intent(context, FocusService::class.java)
                context.stopService(serviceIntent)
                
                // 2. Clear session
                FocusService.clearSessionEndTime(context)
                
                Toast.makeText(context, "Developer Override: Focus Session Stopped!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
