/*
 * SPDX-FileCopyrightText: 2021 Eric Neidhardt
 * SPDX-License-Identifier: MIT
 */
val groupId = "com.github.ericneid"
val libraryName = "ktx-location-services"
val libraryVersion = "0.2.0"

project.ext.set("PUBLISH_GROUP_ID", groupId)
project.ext.set("PUBLISH_VERSION", libraryVersion)
project.ext.set("PUBLISH_ARTIFACT_ID", libraryName)
project.ext.set("PROJECT_URL",     "https://github.com/EricNeid/ktx-location-services")
project.ext.set("PROJECT_SCM_URL", "scm:git:http://github.com/EricNeid/ktx-location-services.git")
project.ext.set("PROJECT_DESCRIPTION",
"""
KTX-location-services is a simple wrapper around some of Androids Location APIs.
"It provides access to basic functionalities (ie. location and bearing) by using
"kotlin coroutines.
""".trimIndent()
)


plugins {
	id("com.android.library")
	kotlin("android")
	id("org.jetbrains.dokka") version "1.9.20"
}

apply {
	from("${rootProject.projectDir}/scripts/publish-mavencentral.gradle")
}

android {
	namespace = "org.neidhardt.ktxlocationservice"
	compileSdk = 35

	defaultConfig {
		minSdk = 21
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
	}
	kotlinOptions {
		jvmTarget = JavaVersion.VERSION_1_8.toString()
		freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
	}
	buildTypes {
		getByName("release") {
			isMinifyEnabled = false
		}
	}
	testOptions {
		unitTests.isReturnDefaultValues = true
	}
}


repositories {
	google()
	mavenCentral()
}

val coroutineVersion = "1.8.1"
dependencies {
	// google play services
	compileOnly("com.google.android.gms:play-services-location:21.3.0")

	// kotlin coroutines
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutineVersion")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutineVersion")

	// testing
	testImplementation("com.google.android.gms:play-services-location:21.3.0")
	testImplementation("junit:junit:4.13.2")
	testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutineVersion")
	androidTestImplementation("com.google.android.gms:play-services-location:21.3.0")
	androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutineVersion")
	androidTestImplementation("androidx.test:runner:1.6.2")
	androidTestImplementation("androidx.test:rules:1.6.1")
}
