    plugins {
        alias(libs.plugins.android.application)
        id("com.google.gms.google-services")
    }

    android {
        namespace = "com.example.snaplearn"
        compileSdk = 36

        defaultConfig {
            applicationId = "com.example.snaplearn"
            minSdk = 24
            targetSdk = 36
            versionCode = 1
            versionName = "1.0"

            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        buildTypes {
            release {
                isMinifyEnabled = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro"
                )
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_11
            targetCompatibility = JavaVersion.VERSION_11
        }
        androidResources {
            noCompress += "tflite"
        }
        buildFeatures {
            viewBinding = true
        }
    }

    dependencies {

        implementation(libs.appcompat)
        implementation(libs.material)
        implementation(libs.activity)
        implementation(libs.constraintlayout)
        implementation(libs.navigation.fragment)
        implementation(libs.navigation.ui)
        testImplementation(libs.junit)
        androidTestImplementation(libs.ext.junit)
        androidTestImplementation(libs.espresso.core)
        implementation("androidx.constraintlayout:constraintlayout:2.1.4")

        // CameraX: xem trước và chụp ảnh
        val camerax = "1.3.4"
        implementation("androidx.camera:camera-core:$camerax")
        implementation("androidx.camera:camera-camera2:$camerax")
        implementation("androidx.camera:camera-lifecycle:$camerax")
        implementation("androidx.camera:camera-view:$camerax")

        // TensorFlow Lite: chạy mô hình nhận dạng
        implementation("org.tensorflow:tensorflow-lite:2.14.0")
        implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

        // Room: lưu sổ từ vựng và lịch sử (dùng ở giai đoạn sau)
        implementation("androidx.room:room-runtime:2.6.1")
        annotationProcessor("androidx.room:room-compiler:2.6.1")

        implementation(platform("com.google.firebase:firebase-bom:35.1.0"))
        implementation("com.google.firebase:firebase-auth")
        implementation("com.google.firebase:firebase-analytics")
        implementation("com.google.firebase:firebase-firestore")
    }