plugins {
    jacoco
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.dokka)
    id("com.google.gms.google-services")
}

android {
    namespace = "io.github.CMPUT301F26stderr.campsbridge"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.CMPUT301F26stderr.campsbridge"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        val emulatorHost = providers.gradleProperty("firebaseEmulatorHost").orElse("").get()
        buildConfigField(
            "String",
            "FIREBASE_EMULATOR_HOST",
            groovy.json.JsonOutput.toJson(emulatorHost),
        )
        val mapsApiKey =
            providers
                .gradleProperty("mapsApiKey")
                .orElse("")
                .get()
                .trim()
        manifestPlaceholders["mapsApiKey"] = mapsApiKey
        buildConfigField("boolean", "MAPS_CONFIGURED", mapsApiKey.isNotEmpty().toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("firebaseDebug") {
            initWith(getByName("debug"))
            matchingFallbacks += "debug"
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dokka {
    dokkaPublications.html {
        moduleName.set("Campsbridge")
        outputDirectory.set(layout.buildDirectory.dir("dokka/html"))
    }
}

dependencies {
    implementation(libs.maps.compose)
    implementation(libs.maps.sdk)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.kotlinx.coroutines.play.services)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockito)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    add("firebaseDebugImplementation", libs.androidx.compose.ui.tooling)
}

// Backend tests share compilation with local tests, but require their own emulator invocation.
tasks.register<Test>("firebaseIntegrationTest") {
    val localTests = tasks.named<Test>("testDebugUnitTest").get()
    testClassesDirs = localTests.testClassesDirs
    classpath = localTests.classpath
    systemProperties(localTests.systemProperties)
    workingDir = localTests.workingDir
    dependsOn(testClassesDirs)
    group = "verification"
    description = "Runs Kotlin repository contracts against the Firebase emulators."
    include("**/integration/firebase/**")
    maxParallelForks = 1
    maxHeapSize = "2g"
    outputs.upToDateWhen { false }
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
    )
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType<Test>().configureEach {
    if (name != "firebaseIntegrationTest") exclude("**/integration/firebase/**")
}

// Keep line and branch reports compatible with the pinned JDK 25 toolchain.
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val debugClasses =
    layout.buildDirectory.dir(
        "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes",
    )
val unitExecution =
    layout.buildDirectory.file(
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
    )

tasks.register<JacocoReport>("unitCoverageReport") {
    dependsOn("testDebugUnitTest")
    executionData(unitExecution)
    val executionFile = unitExecution
    val compiledSources = debugClasses
    setOnlyIf {
        check(executionFile.get().asFile.isFile) { "Unit coverage data is missing" }
        check(
            compiledSources
                .get()
                .file(
                    "io/github/CMPUT301F26stderr/campsbridge/domain/shared/TypesKt.class",
                ).asFile.isFile,
        ) { "The measured production guard is missing" }
        true
    }
    classDirectories.from(
        fileTree(debugClasses) {
            exclude("**/testsupport/**", "**/demo/**")
        },
    )
    sourceDirectories.from("src/main/java")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// Require full line/branch coverage only for these small pure rules.
tasks.register<JacocoCoverageVerification>("pureLogicCoverage") {
    dependsOn("testDebugUnitTest")
    executionData(unitExecution)
    val executionFile = unitExecution
    val compiledSources = debugClasses
    setOnlyIf {
        check(executionFile.get().asFile.isFile) { "Unit coverage data is missing" }
        check(
            compiledSources
                .get()
                .file(
                    "io/github/CMPUT301F26stderr/campsbridge/domain/shared/TypesKt.class",
                ).asFile.isFile,
        ) { "The measured production guard is missing" }
        check(
            compiledSources
                .get()
                .file(
                    "io/github/CMPUT301F26stderr/campsbridge/ui/app/WorkspaceSelectionKt.class",
                ).asFile.isFile,
        ) { "The measured workspace rule is missing" }
        check(
            compiledSources
                .get()
                .file(
                    "io/github/CMPUT301F26stderr/campsbridge/ui/common/images/ImageSlotState.class",
                ).asFile.isFile,
        ) { "The measured image action rules are missing" }
        listOf(
            "ui/common/input/PositiveCountInputKt.class",
            "ui/common/input/PriceInputKt.class",
            "ui/components/calendar/MonthCellsKt.class",
            "ui/profile/logic/OptionalContactInputKt.class",
        ).forEach { path ->
            check(
                compiledSources
                    .get()
                    .file(
                        "io/github/CMPUT301F26stderr/campsbridge/$path",
                    ).asFile.isFile,
            ) {
                "The measured calendar/input rule is missing: $path"
            }
        }
        true
    }
    classDirectories.from(
        fileTree(debugClasses) {
            include(
                "**/domain/shared/TypesKt.class",
                "**/ui/app/WorkspaceSelectionKt.class",
                "**/ui/common/images/ImageSlotState.class",
                "**/ui/common/input/PositiveCountInputKt.class",
                "**/ui/common/input/PriceInputKt.class",
                "**/ui/components/calendar/MonthCellsKt.class",
                "**/ui/profile/logic/OptionalContactInputKt*.class",
            )
        },
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "1.0".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                minimum = "1.0".toBigDecimal()
            }
        }
    }
}
