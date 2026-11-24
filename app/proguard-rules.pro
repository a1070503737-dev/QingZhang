# Room、Hilt、Compose 等库自带混淆规则，这里无需额外配置。
# 保留崩溃堆栈的行号，方便排查问题
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Vosk uses JNA direct native method registration.
-keep class org.vosk.LibVosk { *; }
-keep class com.sun.jna.* { *; }
-keepclassmembers class * extends com.sun.jna.* { public *; }

# JNA's desktop window helpers are unused by the Android audio engine.
-dontwarn java.awt.Component
-dontwarn java.awt.GraphicsEnvironment
-dontwarn java.awt.HeadlessException
-dontwarn java.awt.Window
