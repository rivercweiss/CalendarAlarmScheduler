plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.example.calendaralarmscheduler"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.calendaralarmscheduler"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true
    }
    
    signingConfigs {
        create("release") {
            storeFile = file("release-key.keystore")
            storePassword = "android"
            keyAlias = "release-key"
            keyPassword = "android"
        }
    }
    
    buildTypes {
        debug {
            buildConfigField("boolean", "SHOW_DEBUG_FEATURES", "true")
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "SHOW_DEBUG_FEATURES", "false")
            signingConfig = signingConfigs.getByName("release")
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
    kotlinOptions {
        jvmTarget = "11"
    }
    
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
        
        // Enhanced instrumentation test configuration for comprehensive E2E testing
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        
        animationsDisabled = true
        
        reportDir = "$projectDir/build/reports/androidTests"
        resultsDir = "$projectDir/build/outputs/androidTest-results"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    
    // Room database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    
    
    // Lifecycle and ViewModels
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.livedata.ktx)
    
    // Fragment and Navigation
    implementation(libs.fragment.ktx)
    implementation(libs.navigation.fragment.ktx)
    implementation(libs.navigation.ui.ktx)
    
    // Kotlin serialization for type converters
    implementation(libs.kotlinx.serialization.json)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    
    // Hilt Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    
    // Google Play Billing for in-app purchases
    implementation("com.android.billingclient:billing-ktx:7.0.0")
    
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room.testing)
    testImplementation(libs.mockk)
    testImplementation(libs.truth)
    testImplementation(libs.arch.core.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    
    // Comprehensive E2E Testing Framework
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    
    // Enhanced UI Testing
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-accessibility:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-web:3.6.1")
    
    // Test Orchestration and Reporting
    androidTestImplementation("androidx.test:orchestrator:1.5.0")
    androidTestUtil("androidx.test:orchestrator:1.5.0")
    
    // Performance and Memory Monitoring
    androidTestImplementation("androidx.benchmark:benchmark-junit4:1.3.0")
    androidTestImplementation("androidx.test.services:test-services:1.5.0")
    
    // Calendar and ContentProvider Testing
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.2.1")
    
    // Time and Date Mocking
    androidTestImplementation("org.threeten:threetenbp:1.6.8")
    
    // Network Mocking (for any API interactions)
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}