# Room、Hilt、Compose 等库自带混淆规则，这里无需额外配置。
# 保留崩溃堆栈的行号，方便排查问题
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Vosk uses JNA direct native method registration.
-keep class org.vosk.LibVosk { *; }
-keep class com.sun.jna.** { *; }
