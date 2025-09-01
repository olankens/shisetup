package com.example.shisetup.updater

import Updater
import android.content.Context
import com.example.shisetup.adbdroid.Shield
import com.example.shisetup.netsense.Netsense.Companion.getFromGithub
import java.io.File

class Artemis(machine: Shield, context: Context) : Updater(machine, context) {
    override val pkgname = "com.limelight.noir"
    override val heading = "Artemis"

    override suspend fun runGather(): File? {
        val address = "https://api.github.com/repos/ClassicOldSong/moonlight-android/releases/latest"
        return getFromGithub(address, Regex(".*game-release.apk"), context)
    }
}