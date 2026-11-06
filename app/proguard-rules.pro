# kotlinx.serialization: keep generated serializers for the Plaid models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.webforks.networth.data.plaid.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.webforks.networth.data.plaid.**$$serializer { *; }
