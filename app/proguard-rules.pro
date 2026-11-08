# Keep the JNI bridge entry points. Android's JNI lookup uses these names,
# so the shrinker must not rename or remove them.
-keep class com.portsotc.mobile.nativebridge.NativeCore { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
