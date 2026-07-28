import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

// ──────────────────────────────────────────────────────────────────────────
// بيانات التوقيع تُقرأ من `key.properties` في جذر المشروع (غير مُدرَج في git).
// إن لم يوجد الملف يستخدم البناء توقيع debug تلقائياً (أيام التطوير)، أمّا
// في CI/المتجر فنوفّر الملف فيُستخدم مفتاح الإنتاج. نفس عقد نسخة Flutter.
// ──────────────────────────────────────────────────────────────────────────
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("key.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}

android {
    namespace = "com.nebras.mobile"
    // compileSdk = 36 يُبقي التوافق مع مكتبات AndroidX/Firebase الحديثة،
    // والتوافق إلى الخلف مضمون عبر minSdk/targetSdk.
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    defaultConfig {
        // Google Sign-In (debug): بصمة debug.keystore مسجّلة في
        // google-services.json تحت com.example.nebras_mobile_app فقط. إصدارات
        // المتجر (assembleRelease / bundleRelease) تبقى com.nebras.mobile.
        // ⛔ لا تُغيّر معرّف الإصدار — كسر سلسلة التوقيع يفصل المستخدمين.
        val taskNames = gradle.startParameter.taskNames
        val isStoreRelease = taskNames.any {
            it.contains("bundleRelease", ignoreCase = true) ||
                it.contains("assembleRelease", ignoreCase = true)
        }
        applicationId = if (isStoreRelease) {
            "com.nebras.mobile"
        } else {
            "com.example.nebras_mobile_app"
        }
        // minSdk = 23 (Android 6.0) — أقلّ ما تدعمه Firestore و Media3.
        minSdk = 23
        targetSdk = 35
        versionCode = 11
        versionName = "1.0.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            val storePath = keystoreProperties.getProperty("storeFile")
            if (!storePath.isNullOrEmpty()) {
                storeFile = file(storePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // حارس صارم: بناء AAB/APK للنشر يتطلّب `key.properties` وإلا نوقف
            // البناء بدل إنتاج artifact موقَّع بـ debug سيرفضه Play بصمت.
            val taskNames = gradle.startParameter.taskNames
            val isReleasePublishTask = taskNames.any {
                it.contains("bundleRelease", ignoreCase = true) ||
                    it.contains("assembleRelease", ignoreCase = true)
            }
            if (isReleasePublishTask && !keystorePropertiesFile.exists()) {
                throw GradleException(
                    "🛑 ملف key.properties غير موجود.\n" +
                        "لا يمكن بناء نسخة release للنشر بدون مفتاح توقيع الإنتاج.\n" +
                        "راجع STORE_RELEASE.md §2 لتوليد keystore.",
                )
            }
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }

            ndk { debugSymbolLevel = "SYMBOL_TABLE" }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")
    // بديل go_router.
    implementation("androidx.navigation:navigation-compose:2.9.8")
    // بديل طابور التنزيل الخلفيّ في Flutter.
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    // بديل shared_preferences (تفضيلات السمة/الترتيب).
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text-google-fonts")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // بديل just_audio + audio_service + audio_session + video_player:
    // Media3 يوفّر التشغيل الخلفيّ وإشعار الوسائط وشاشة القفل وإدارة التركيز.
    val media3Version = "1.10.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-common-ktx:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")

    // بديل cached_network_image.
    implementation("io.coil-kt.coil3:coil-compose:3.2.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.2.0")

    // بديل lottie (شاشة الفراغ + مقدّمة الإقلاع).
    implementation("com.airbnb.android:lottie-compose:6.6.6")

    val firebaseBom = platform("com.google.firebase:firebase-bom:34.16.0")
    implementation(firebaseBom)
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
    debugImplementation("com.google.firebase:firebase-appcheck-debug")

    // بديل google_sign_in عبر Credential Manager (الطريق المعتمد حالياً).
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // بديل dio (طبقة الشبكة + إعادة المحاولة + التنزيل).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
