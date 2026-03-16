# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep ANCS model classes
-keep class com.wearos.ancsbridge.model.** { *; }
-keep class com.wearos.ancsbridge.data.** { *; }
