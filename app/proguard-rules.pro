# Room、Hilt、Compose 等库自带混淆规则，这里无需额外配置。
# 保留崩溃堆栈的行号，方便排查问题
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# sherpa-onnx JNI looks up the Kotlin configuration and result classes by name.
-keep class com.k2fsa.sherpa.onnx.** { *; }

# Tesseract and Leptonica look up bridge classes and callbacks from JNI.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }
