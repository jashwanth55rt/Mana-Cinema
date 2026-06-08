package com.example.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseInitializer {
    fun initialize(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApiKey("AIzaSyB6nptlXvEtBK9JA28kmaipKogE284TkAc")
                    .setApplicationId("1:991323524559:web:e518c938c79eb0d1ac56ba")
                    .setDatabaseUrl("https://infinity-movies-fa1f2-default-rtdb.firebaseio.com")
                    .setProjectId("infinity-movies-fa1f2")
                    .setStorageBucket("infinity-movies-fa1f2.firebasestorage.app")
                    .setGcmSenderId("991323524559")
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseInitializer", "FirebaseApp initialization failed", e)
        }
    }
}
