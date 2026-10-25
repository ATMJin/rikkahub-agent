import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "me.rerere.rikkahub"
    compileSdk = 37
    defaultConfig {
        applicationId = "tw.littleyi.mobileagent"
        minSdk = 31
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.1-agent-ui"
        ndk { abiFilters += "arm64-v8a" }
    }
    // Only the selected upstream Codex account/provider sources were moved here.
    // No source directory from the old all-purpose application is compiled.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging.resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1")
    testOptions.unitTests.isReturnDefaultValues = true
}

kotlin.compilerOptions {
    jvmTarget.set(JvmTarget.JVM_17)
    optIn.addAll("kotlin.uuid.ExperimentalUuidApi", "kotlin.time.ExperimentalTime")
}

dependencies {
    implementation(project(":ai"))
    implementation(project(":common"))
    implementation(project(":local-llm"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio) // loopback OAuth callback ONLY; no Web management UI
    testImplementation(libs.junit)
}
