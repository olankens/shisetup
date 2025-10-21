package com.example.shisetup.updater

import Updater
import android.content.Context
import com.example.shisetup.adbdroid.Shield
import com.example.shisetup.netsense.Netsense.Companion.getFromGithub
import java.io.File

class Animetv(machine: Shield, context: Context) : Updater(machine, context) {
    override val pkgname = "com.amarullz.androidtv.animetvjmto"
    override val heading = "AnimeTV"

    override suspend fun runGather(): File? {
        val address = "https://api.github.com/repos/amarullz/AnimeTV/releases/latest"
        return getFromGithub(address, Regex(".*\\.apk"), context)
    }
}