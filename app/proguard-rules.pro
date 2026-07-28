# ──────────────────────────────────────────────────────────────────────────
# قواعد التصغير (R8) — نسخة Kotlin.
#
# ⚠️ `isMinifyEnabled` و`isShrinkResources` مُفعَّلان في الإصدار. حذف قواعد
# `-keep` أدناه يُعيد علّة «اختفاء الأقسام في نسخة المتجر»: Firestore يعكس
# الحقول بالاسم عبر Reflection، وR8 يُعيد تسمية النماذج فتصل خرائط فارغة.
# اختبر بناء release على جهاز فعليّ قبل أيّ رفع.
# ──────────────────────────────────────────────────────────────────────────

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,RuntimeVisible*Annotations
-keepattributes SourceFile,LineNumberTable

# نماذج التطبيق التي تُقرأ/تُكتب من Firestore بالانعكاس.
-keep class com.nebras.mobile.core.model.** { *; }
-keep class com.nebras.mobile.feature.**.*Model { *; }
-keepclassmembers class com.nebras.mobile.** {
    <init>(...);
}

# Firebase / Firestore / Auth / Storage / Messaging.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Credential Manager + Google Identity (تسجيل الدخول).
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class androidx.credentials.** { *; }

# Media3 (ExoPlayer + MediaSession) — تحميل ديناميكيّ للمُستخرِجات.
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# OkHttp / Okio.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Coil 3.
-dontwarn coil3.**

# Lottie.
-dontwarn com.airbnb.lottie.**

# Kotlin coroutines.
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Compose — R8 يتعامل معه أصلاً، نُبقي أسماء المصدر للتشخيص فقط.
-dontwarn androidx.compose.**

# org.json مستعمل في LocalStore — جزء من المنصّة، لا حاجة لتحذير.
-dontwarn org.json.**
