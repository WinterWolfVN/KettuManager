import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.beefers.vendetta.manager"
    compileSdk = 36

    defaultConfig {
        applicationId = "cocobo1.pupu.manager"
        minSdk = 24
        targetSdk = 34
        versionCode = 1220
        versionName = "1.2.2"

        buildConfigField("String", "GIT_BRANCH", "\"${getCurrentBranch()}\"")
        buildConfigField("String", "GIT_COMMIT", "\"${getLatestCommit()}\"")
        buildConfigField("boolean", "GIT_LOCAL_COMMITS", "${hasLocalCommits()}")
        buildConfigField("boolean", "GIT_LOCAL_CHANGES", "${hasLocalChanges()}")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        named("release") {
            isCrunchPngs = true
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-Xcontext-receivers",
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=" +
                    layout.buildDirectory.get().asFile.resolve("report").absolutePath,
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.6"
    }

    androidComponents {
        onVariants(selector().withBuildType("release")) {
            it.packaging.resources.excludes.apply {
                add("/**/*.version")
                add("/kotlin-tooling-metadata.json")
                add("/DebugProbesKt.bin")
            }
        }
    }

    packaging {
        resources {
            excludes += "/**/*.kotlin_builtins"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    configurations {
        all {
            exclude(module = "listenablefuture")
            exclude(module = "error_prone_annotations")
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))

    implementation(libs.bundles.accompanist)
    implementation(libs.bundles.androidx)
    implementation(libs.bundles.coil)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.koin)
    
    implementation(libs.bundles.ktor)
    implementation("io.ktor:ktor-client-okhttp:3.4.2")
    
    implementation(libs.bundles.shizuku)
    implementation(libs.bundles.voyager)

    implementation(files("libs/lspatch.aar"))

    implementation(libs.aboutlibraries.core)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    
    implementation(libs.binaryResources) {
        exclude(module = "checker-qual")
        exclude(module = "jsr305")
        exclude(module = "guava")
    }
    
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.collections)
    
    implementation("com.squareup.okio:okio:3.9.0")
}

fun getCurrentBranch(): String? =
    exec("git", "symbolic-ref", "--short", "HEAD")

fun getLatestCommit(): String? =
    exec("git", "rev-parse", "--short", "HEAD")

fun hasLocalCommits(): Boolean {
    val branch = getCurrentBranch() ?: return false
    return exec("git", "log", "origin/$branch..HEAD")?.isNotBlank() ?: false
}

fun hasLocalChanges(): Boolean =
    exec("git", "status", "-s")?.isNotEmpty() ?: false

fun exec(vararg command: String): String? {
    return try {
        val stdout = ByteArrayOutputStream()
        val errout = ByteArrayOutputStream()

        project.exec {
            commandLine = command.toList()
            standardOutput = stdout
            errorOutput = errout
            isIgnoreExitValue = true
        }

        if (errout.size() > 0) null
        else stdout.toString(Charsets.UTF_8).trim()
    } catch (e: Throwable) {
        null
    }
}
