plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ru.nksk.lctapp.feature.debug"
    compileSdk = 37
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    constraints {
        implementation(libs.androidx.core.ktx) {
            because("Align transitive Core dependencies in the runtime and lint classpaths with the app.")
        }
        implementation(libs.androidx.activity.compose) {
            because("Align Material's runtime Activity dependencies with the app.")
        }
        implementation(libs.androidx.navigationevent) {
            because("Align Activity's runtime NavigationEvent dependency with the app.")
        }
        implementation(libs.androidx.lifecycle.runtime.compose) {
            because("Align Compose's transitive Lifecycle dependencies with the app.")
        }
        implementation(libs.androidx.compose.runtime) {
            because("Use the same Compose runtime as the app's Navigation 3 host.")
        }
        implementation(libs.androidx.savedstate.compose) {
            because("Compile saveable debug UI against the app's SavedState version.")
        }
        implementation(libs.kotlinx.serialization.core) {
            because("Align SavedState's transitive serialization with the app.")
        }
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
}
