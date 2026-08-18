package com.kit.prototype

import android.app.Application
import androidx.work.Configuration

class KitApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build()
}
