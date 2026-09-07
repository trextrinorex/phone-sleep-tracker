# Phone Sleep Tracker – keep rules
# Currently minimal because we use mostly first-party code and Room/WorkManager
# already ship with their own consumer rules.

-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Keep data classes used by Room / inference
-keep class com.phonesleeptracker.SleepSessionEntity { *; }
-keep class com.phonesleeptracker.PhoneSignal { *; }
-keep class com.phonesleeptracker.SleepSession { *; }
