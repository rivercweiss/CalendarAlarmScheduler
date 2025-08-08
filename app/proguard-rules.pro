# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# Room database
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker
-keep class * extends androidx.work.InputMerger
-keep class androidx.work.impl.background.systemalarm.RescheduleReceiver

# Keep our data model classes
-keep class com.example.calendaralarmscheduler.domain.models.** { *; }
-keep class com.example.calendaralarmscheduler.data.database.entities.** { *; }

# Keep our receivers and activities
-keep class com.example.calendaralarmscheduler.receivers.** { *; }
-keep class com.example.calendaralarmscheduler.ui.alarm.AlarmActivity { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.calendaralarmscheduler.**$$serializer { *; }
-keepclassmembers class com.example.calendaralarmscheduler.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.calendaralarmscheduler.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep calendar contract classes
-keep class android.provider.CalendarContract** { *; }

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile