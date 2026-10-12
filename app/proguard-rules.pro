# Keep Room generated code
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

# Moshi / Retrofit DTOs
-keep class com.calorietracker.app.data.recognition.dto.** { *; }
-keepclassmembers class kotlin.Metadata { *; }
