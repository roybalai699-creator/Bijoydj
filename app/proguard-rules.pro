# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Keep BuildConfig, Room entities/DAOs, and audio/AI data classes intact under R8
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.example.BuildConfig { *; }
-keep class com.example.database.** { *; }
-keep class com.example.audio.** { *; }
-keep class com.example.ai.** { *; }
-keep class com.example.sources.** { *; }
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

