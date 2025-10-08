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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep all classes that might be used by reflection
-keep class com.example.screentimeoverlay.** { *; }

# Keep all native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep all classes with @Keep annotation
-keep @androidx.annotation.Keep class * { *; }

# Keep all classes in the overlay package
-keep class com.example.screentimeoverlay.OverlayService { *; }
-keep class com.example.screentimeoverlay.ScreenTimeAccessibilityService { *; }
-keep class com.example.screentimeoverlay.MainActivity { *; }

# Keep all data classes
-keep class com.example.screentimeoverlay.ScreenTimeData { *; }
-keep class com.example.screentimeoverlay.SessionStats { *; }
-keep class com.example.screentimeoverlay.AppUsageData { *; }

# Keep all notification related classes
-keep class com.example.screentimeoverlay.NotificationManager { *; }
-keep class com.example.screentimeoverlay.SmartNotificationScheduler { *; }

# Keep all fragment classes
-keep class com.example.screentimeoverlay.*Fragment { *; }

# Keep all manager classes
-keep class com.example.screentimeoverlay.*Manager { *; }

# Keep all service classes
-keep class com.example.screentimeoverlay.*Service { *; }
