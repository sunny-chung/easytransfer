# JavaCPP locates generated FFmpeg bindings and native methods through runtime metadata.
-keep class org.bytedeco.javacpp.** { *; }
-keep class org.bytedeco.ffmpeg.** { *; }
-dontwarn org.bytedeco.**

# JNA's initializer reads constants from implemented interfaces, reflects over
# mappings, and calls JNI methods whose names must remain unchanged.
-keep class com.sun.jna.** { *; }
-keep interface com.sun.jna.** { *; }
-dontwarn com.sun.jna.**

# JNA discovers callback entry points through reflection
-keep class * implements com.sun.jna.Callback { *; }

# SQLite registers its JDBC entry point by class name and JNI references helper
# types such as Function that are otherwise invisible to static reachability.
-keep class org.sqlite.** { *; }
-keep interface org.sqlite.** { *; }
-dontwarn org.sqlite.**

# EnumMap obtains an enum's constants by reflectively invoking values(). Keep
# the compiler-generated methods so shrinking does not break that JDK contract.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
