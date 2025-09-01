package com.example.shisetup.updater

import Updater
import android.content.Context
import com.example.shisetup.adbdroid.Shield
import com.example.shisetup.netsense.Netsense.Companion.getFromAddress
import java.io.File

class SmartTube(machine: Shield, context: Context) : Updater(machine, context) {
    override val pkgname = "com.liskovsoft.smarttubetv.beta"
    override val heading = "SmartTube beta"

    override suspend fun runGather(): File? {
        val address = "https://github.com/yuliskov/SmartTubeNext/releases/download/latest/smarttube_beta.apk"
        return getFromAddress(address, context)
    }
}