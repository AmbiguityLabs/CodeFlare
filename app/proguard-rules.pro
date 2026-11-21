# Keep readable crash stack traces
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod

# Safe mode: shrink + optimize, but do NOT rename (avoids name-based reflection bugs)
-dontobfuscate

# Moshi (codegen @JsonClass + reflection via KotlinJsonAdapterFactory) and Kotlin metadata
-keep class kotlin.Metadata { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * { @com.squareup.moshi.Json <fields>; }

# JSON/API models and Retrofit services (accessed by reflection / generic signatures)
-keep class com.example.data.remote.** { *; }
-keep class com.example.data.auth.** { *; }
-keep class com.example.data.local.Cached*Envelope { *; }
-keep class *JsonAdapter {
    public <init>(com.squareup.moshi.Moshi);
    public <init>(com.squareup.moshi.Moshi, java.lang.reflect.Type[]);
}

# ViewModels instantiated via default ViewModelProvider reflection
-keepclassmembers class * extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
}

# Retrofit / OkHttp optional dependencies
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
