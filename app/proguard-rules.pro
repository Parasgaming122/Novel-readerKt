# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepclassmembers class com.paras.novelreaderkt.WtrWebAppInterface {
   public *;
}

-keep class com.paras.novelreaderkt.data.** { *; }
-keep class com.paras.novelreaderkt.WtrLogManager { *; }
-keep class com.paras.novelreaderkt.WtrWebAppInterface { *; }
-keep class com.paras.novelreaderkt.WtrAudioControlBridge { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*

# Moshi
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keepclassmembers class ** {
    *** Companion;
}

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coil
-keep class coil.** { *; }

# Google Generative AI
-keep class com.google.ai.client.** { *; }

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
