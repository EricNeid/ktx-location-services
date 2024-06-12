/*
 * SPDX-FileCopyrightText: 2021 Eric Neidhardt
 * SPDX-License-Identifier: MIT
 */
buildscript {
	repositories {
		google()
		mavenCentral()
		gradlePluginPortal()
	}
	dependencies {
		classpath("com.android.tools.build:gradle:8.2.2")
		classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")

		classpath("io.codearte.gradle.nexus:gradle-nexus-staging-plugin:0.22.0")
		classpath("com.jaredsburrows:gradle-license-plugin:0.8.90")
	}
}

plugins {
	id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

tasks.register("clean", Delete::class) {
	delete(rootProject.buildDir)
}