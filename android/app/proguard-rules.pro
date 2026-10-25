# Keep JNI methods and native interface
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.printer.zpl.ZplNative { *; }
-keep class com.printer.zpl.ZplConfig { *; }

