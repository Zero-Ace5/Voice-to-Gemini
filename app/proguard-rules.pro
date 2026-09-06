# R8 / ProGuard rules for Voice to Gemini
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
