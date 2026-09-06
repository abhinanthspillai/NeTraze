package com.netraze.app.di

import com.netraze.app.data.location.AndroidLocationProvider
import com.netraze.app.data.location.LocationProvider
import com.netraze.app.data.wifi.WifiScanCoordinator
import com.netraze.app.data.wifi.WifiScanRunner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    abstract fun bindLocationProvider(
        androidLocationProvider: AndroidLocationProvider
    ): LocationProvider

    @Binds
    abstract fun bindWifiScanRunner(
        wifiScanCoordinator: WifiScanCoordinator
    ): WifiScanRunner
}
