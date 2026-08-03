# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-keepclassmembers class com.example.WtrWebAppInterface {
   public *;
}

-keep class com.example.data.** { *; }
-keep class com.example.WtrLogManager { *; }

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
