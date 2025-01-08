<!--
SPDX-FileCopyrightText: 2021 Eric Neidhardt
SPDX-License-Identifier: CC-BY-4.0
-->
# About

KTX-location-services is a simple wrapper around some of Androids Location APIs.
It provides access to basic functionalities (ie. location and bearing) by using
kotlin coroutines.

Currently supported:

* GoogleLocationService - using GoogleFusedLocationProvider for Location updates
* AndroidLocationService - using LocationManager for Location updates
* BearingSensorService - using accelerometer and magnetic sensor for bearing updates

## Gradle

```gradle
repositories {
    mavenCentral()
}

dependencies {
    implementation("com.github.ericneid:ktx-location-services:0.2.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
```

## Usage

```kotlin
// create service instances in application
// you can use lazy loading, because applicationContext is only available after onCreate
val locationServiceGoogle: GoogleLocationService by lazy { GoogleLocationService(applicationContext) }

val locationServiceAndroid: AndroidLocationService by lazy { AndroidLocationService(applicationContext) }

val bearingService: BearingSensorService by lazy { BearingSensorService(applicationContext) }
```

```kotlin
// use suitable scope
viewModelScope.launch {
    locationService.getLocationUpdatesAsFlow()
    .catch { error -> 
        // handle error
    }
    .collect { location ->
        myLocationConsumer.onLocationChanged(location)
    }
}

viewModelScope.launch { 
    bearingService.getBearingUpdatesFromRotation()
   .catch { error ->
        // handle error
    }
    .collect { bearing ->
        myBearingConsumer.onBearingChanged(bearing.azimuth)
    }
}
```

## Question or comments

Please feel free to open a new issue:
<https://github.com/EricNeid/ktx-location-services/issues>
