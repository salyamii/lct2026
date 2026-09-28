plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "ru.nksk.lctapp"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "ru.nksk.lctapp"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.1"

        // Team endpoint lives in gradle.properties; an explicit empty override keeps the app offline.
        val backendUrl = providers.gradleProperty("LCT_BACKEND_BASE_URL").orElse("").get().trim()
        require(backendUrl.isEmpty() || (backendUrl.startsWith("https://") && backendUrl.endsWith("/") &&
            backendUrl.none { it == '"' || it == '\\' || it.isWhitespace() })) {
            "LCT_BACKEND_BASE_URL must be an HTTPS URL ending in /"
        }
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendUrl\"")

        // Jaeger OTLP/HTTP base URL (the agent appends /v1/traces); empty disables telemetry.
        val otelEndpoint = providers.gradleProperty("LCT_OTEL_ENDPOINT").orElse("").get().trim()
        require(otelEndpoint.isEmpty() || (otelEndpoint.startsWith("https://") && !otelEndpoint.endsWith("/") &&
            otelEndpoint.none { it == '"' || it == '\\' || it.isWhitespace() })) {
            "LCT_OTEL_ENDPOINT must be an HTTPS base URL without a trailing slash"
        }
        buildConfigField("String", "OTEL_EXPORTER_ENDPOINT", "\"$otelEndpoint\"")

        testInstrumentationRunner = "ru.nksk.lctapp.HiltTestRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // OpenTelemetry android-agent requires desugaring below minSdk 26.
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    androidResources {
        noCompress += listOf("mp3", "mp4")
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

// The OpenTelemetry android-agent pulls a newer stdlib than the project compiler can read.
configurations.configureEach {
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(platform(libs.opentelemetry.bom))
    implementation(libs.opentelemetry.api)
    implementation(libs.android.agent)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.lottie.compose)
    implementation(libs.qrcodegen)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:parents"))
    debugImplementation(project(":feature:debug"))
    implementation(project(":core:game"))
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    androidTestImplementation(libs.hilt.testing)
    kspAndroidTest(libs.hilt.compiler)
    implementation(libs.hilt.android)
    ksp(libs.androidx.room.compiler)
    ksp(libs.hilt.compiler)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.room.testing)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(platform(libs.opentelemetry.bom))
    testImplementation(libs.opentelemetry.sdk.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

tasks.withType<Test>().configureEach {
    val mainSources = layout.projectDirectory.dir("src/main/java")
    inputs.dir(mainSources).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("lctapp.mainSourceDir", mainSources.asFile.absolutePath)
    inputs.dir(layout.projectDirectory.dir("src/main/assets/media")).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(rootProject.layout.projectDirectory.file("docs/design/assets/media-manifest.json"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    val domainSources = rootProject.layout.projectDirectory.dir("core/game/src/main/kotlin")
    inputs.dir(domainSources).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("lctapp.domainSourceDir", domainSources.asFile.absolutePath)
    val onboardingSources = rootProject.layout.projectDirectory.dir("feature/onboarding/src/main/java")
    inputs.dir(onboardingSources).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("lctapp.onboardingSourceDir", onboardingSources.asFile.absolutePath)
    val parentsSources = rootProject.layout.projectDirectory.dir("feature/parents/src/main/java")
    inputs.dir(parentsSources).withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("lctapp.parentsSourceDir", parentsSources.asFile.absolutePath)
}
