package com.example.shisetup.updater

import Updater
import android.content.Context
import com.example.shisetup.adbdroid.Shield
import com.example.shisetup.netsense.Netsense.Companion.getFromGithub
import java.io.File

class Flauncher(machine: Shield, context: Context) : Updater(machine, context) {
    override val pkgname = "placeholder" // TODO: Remove placeholder
    override val heading = "FLauncher"

    override suspend fun runGather(): File? {
        val address = "https://api.github.com/repos/CocoCR300/flauncher/releases/latest"
        return getFromGithub(address, Regex(".*arm64-v8a-release.apk"), context)
    }
}