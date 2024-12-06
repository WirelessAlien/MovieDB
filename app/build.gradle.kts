/*
 *     This file is part of "ShowCase" formerly Movie DB. <https://github.com/WirelessAlien/MovieDB>
 *     forked from <https://notabug.org/nvb/MovieDB>
 *
 *     Copyright (C) 2024  WirelessAlien <https://github.com/WirelessAlien>
 *
 *     ShowCase is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ShowCase is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with "ShowCase".  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wirelessalien.android.moviedb"
    compileSdk = 34

    flavorDimensions.add("version")
    productFlavors {
        create("foss") {
            dimension = "version"
            applicationId = "com.wirelessalien.android.moviedb"
        }
        create("full") {
            dimension = "version"
            applicationId = "com.wirelessalien.android.moviedb.full"
            versionNameSuffix = "-full"
        }
    }

    defaultConfig {
        applicationId = "com.wirelessalien.android.moviedb"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            isDebuggable = false
        }
        getByName("debug") {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes.add("META-INF/DEPENDENCIES")
    }
}

tasks.register("printVersionName") {
    doLast {
        println(android.defaultConfig.versionName)
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.13.0-alpha08")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("com.google.android.flexbox:flexbox:3.0.0")
    implementation("androidx.work:work-runtime:2.9.1")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.paging:paging-runtime:3.2.1")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation ("de.hdodenhof:circleimageview:3.1.0")
    implementation("com.squareup.picasso:picasso:2.8")

    //for Google Sign In
    "fullImplementation"("com.google.android.gms:play-services-auth:21.2.0")
    "fullImplementation"("com.google.apis:google-api-services-drive:v3-rev197-1.25.0")
    "fullImplementation"("com.google.http-client:google-http-client-gson:1.40.0")
    "fullImplementation"("androidx.credentials:credentials:1.3.0")
    "fullImplementation"("androidx.credentials:credentials-play-services-auth:1.3.0")
    "fullImplementation"("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    "fullImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}