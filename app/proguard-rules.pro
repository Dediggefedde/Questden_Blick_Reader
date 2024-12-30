
-dontwarn com.google.gson.**
-dontwarn javax.annotation.Nullable
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.OpenSSLProvider
-keep class * extends java.util.ArrayList { *; }
-keep class * extends java.util.HashMap { *; }
-keep class * implements java.util.List { *; }
-keep class * implements java.util.Map { *; }
-keep class androidx.paging.** { *; }
-keep class androidx.room.** { *; }
-keep class androidx.room.**$Companion { *; }
-keep class androidx.room.RoomDatabase { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.yourpackagename.net.** { *; }
-keep class kotlin.reflect.** { *; }
-keep class org.** { *; }
-keepattributes *Annotation*
-keepattributes Signature
-keepclassmembers class com.google.gson.** { *; }
-keepclassmembers class com.google.gson.reflect.TypeToken { *; }
-keepclassmembers class com.google.gson.stream.** { *; }
-keepclassmembers enum * { *; }
-keepnames class com.google.gson.** {*;}
-keepnames class com.yourpackagename.net.** {*;}
-keepnames class org.** {*;}
-keepnames enum com.google.gson.** {*;}
-keepnames enum com.yourpackagename.net.** {*;}
-keepnames enum org.** {*;}
-keepnames interface com.google.gson.** {*;}
-keepnames interface com.yourpackagename.net.** {*;}
-keepnames interface org.** {*;}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * {
    public <methods>;
    public <fields>;
}