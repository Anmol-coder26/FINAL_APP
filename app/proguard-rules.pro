# Agora
-keep class io.agora.** { *; }
-dontwarn io.agora.**

# Bhashini (any reflection-based JSON parsing)
-keep class com.guardian.app.bhashini.** { *; }

# Gemini / Generative Language
-keep class com.google.ai.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# Room
-keep class androidx.room.** { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep data classes used in JSON & Call Protect
-keep class com.guardian.app.callprotect.** { *; }
-keep class com.guardian.app.** { *; }