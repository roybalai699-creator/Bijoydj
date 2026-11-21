package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

/**
 * Configures Firebase App Check before any Firebase AI Logic / Gemini API calls execute.
 * - Debug builds: synchronously persists FIREBASE_APPCHECK_DEBUG_TOKEN (via commit()) and installs DebugAppCheckProviderFactory.
 * - Release builds: installs RecaptchaAppCheckProviderFactory using the provisioned recaptcha_site_key string resource.
 */
fun configureAppCheck(context: Context, intent: Intent? = null) {
    try {
        val app = FirebaseApp.getApps(context).firstOrNull()
            ?: FirebaseApp.initializeApp(context)
            ?: return

        val appCheck = FirebaseAppCheck.getInstance(app)
        if (BuildConfig.DEBUG) {
            val persistenceKey = app.persistenceKey
            val debugSecret = intent?.getStringExtra("FIREBASE_APPCHECK_DEBUG_TOKEN")
                ?.takeIf { it.isNotBlank() }
                ?: runCatching {
                    val field = BuildConfig::class.java.getField("FIREBASE_APPCHECK_DEBUG_TOKEN")
                    field.get(null) as? String
                }.getOrNull()?.takeIf { it.isNotBlank() }

            if (!debugSecret.isNullOrBlank()) {
                val prefs = context.getSharedPreferences(
                    "com.google.firebase.appcheck.debug.store.$persistenceKey",
                    Context.MODE_PRIVATE
                )
                prefs.edit()
                    .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", debugSecret)
                    .commit()
            }
            appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
        } else {
            val siteKeyResId = context.resources.getIdentifier(
                "recaptcha_site_key",
                "string",
                context.packageName
            )
            if (siteKeyResId != 0) {
                val siteKey = context.getString(siteKeyResId)
                if (siteKey.isNotBlank()) {
                    appCheck.installAppCheckProviderFactory(
                        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                    )
                }
            }
        }
    } catch (t: Throwable) {
        Log.w("BijoyAppCheck", "App Check initialization deferred: ${t.message}")
    }
}
