package com.shuttermute

import android.app.Application
import com.shuttermute.privilege.AdbClient

class ShutterMuteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdbClient.ensureIdentity(this)
    }
}
