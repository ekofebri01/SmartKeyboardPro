# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Moshi Models & Adapters
-keep class com.example.model.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonClass *;
}
-keep class *JsonAdapter { *; }

# IME Service & Activities
-keep public class com.example.keyboard.SmartKeyboardService { *; }
-keep public class com.example.MainActivity { *; }
-keep public class com.example.SmartKeyboardApp { *; }
