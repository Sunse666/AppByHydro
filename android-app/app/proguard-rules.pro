# kotlinx.serialization 需要保留 @Serializable 生成的序列化器
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.jxau.oj.** {
    *** Companion;
}
-keepclasseswithmembers class com.jxau.oj.** {
    kotlinx.serialization.KSerializer serializer(...);
}
