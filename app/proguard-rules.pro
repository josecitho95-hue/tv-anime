# Keep OkHttp and Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Keep Jsoup
-keep public class org.jsoup.** { *; }

# Keep Room entities
-keep class com.jkanimetv.app.data.** { *; }

# Keep Coil
-dontwarn coil.**

# Keep ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**
