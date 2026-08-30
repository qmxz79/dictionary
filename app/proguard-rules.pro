# Proguard rules for Bilingual Dictionary App
-keepattributes SourceFile,LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep data models
-keep class com.bilingual.dictionary.data.model.** { *; }
