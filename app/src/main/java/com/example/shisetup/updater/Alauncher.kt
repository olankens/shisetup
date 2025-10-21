package com.example.shisetup.updater

import Updater
import android.content.Context
import com.example.shisetup.adbdroid.DeviceLocale
import com.example.shisetup.adbdroid.Shield
import com.example.shisetup.netsense.Netsense.Companion.getFromAddress
import com.example.shisetup.netsense.Netsense.Companion.getFromGithub
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class Alauncher(machine: Shield, context: Context) : Updater(machine, context) {
    override val pkgname = "org.mywire.alauncher"
    override val heading = "aLauncher"

    override suspend fun runGather(): File? {
        val address = "https://api.github.com/repos/4v3ngR/aLauncher/releases/latest"
        return getFromGithub(address, Regex(".*arm64-v8a-release.apk"), context)
    }

    override suspend fun runUpdate() = withContext(IO) {
        if (!getRecent()) {
            runRemove()
            val fetched = runGather()
            if (fetched != null) {
                machine.runUpdate(fetched.path)
                machine.runAccord(pkgname, "read_external_storage")
                machine.runAccord(pkgname, "write_external_storage")
                machine.runEnable("com.google.android.leanbacklauncher.recommendations", false)
                machine.runEnable("com.google.android.leanbacklauncher", false)
                machine.runEnable("com.google.android.tvlauncher", false)
                machine.runEnable("com.google.android.tvrecommendations", false)
                machine.runRepeat("keycode_home")
                val visible = machine.runSelect("//*[@text='aLauncher']")
                if (visible) {
                    machine.runRepeat("keycode_dpad_down", 99)
                    machine.runRepeat("keycode_dpad_right", 99)
                    machine.runRepeat("keycode_enter", 99)
                    delay(5000)
                }
            }
        }
    }

    suspend fun runRevealSettings() = withContext(IO) {
        machine.runRepeat("keycode_dpad_up", 99)
        machine.runRepeat("keycode_enter")
    }

    suspend fun runVanishCategories() = withContext(IO) {
        machine.runEscape()
        machine.setLocale(DeviceLocale.EN_US)
        runRevealSettings()
        machine.runSelect("//*[@content-desc='Categories']")
        val element = machine.runScrape("//*[@content-desc='Add Category']/parent::*")
        var counter = element?.childNodes?.item(1)?.childNodes?.length ?: 0
        if (counter > 0) {
            while (counter > 0) {
                machine.runRepeat("keycode_dpad_up", counter * 2)
                machine.runRepeat("keycode_dpad_right", 9)
                machine.runRepeat("keycode_enter")
                machine.runRepeat("keycode_dpad_down", 9)
                machine.runRepeat("keycode_enter")
                machine.runRepeat("keycode_back", 2)
                runRevealSettings()
                machine.runSelect("//*[@content-desc='Categories']")
                counter -= 1
            }
        }
        machine.runRepeat("keycode_back", 2)
    }

    suspend fun setCategory(payload: String, bigness: Int) = withContext(IO) {
        if (bigness !in listOf(80, 90, 100, 110, 120, 130, 140, 150)) return@withContext
        machine.runEscape()
        machine.setLocale(DeviceLocale.EN_US)
        runRevealSettings()
        machine.runSelect("//*[@content-desc='Categories']")
        machine.runSelect("//*[@content-desc='Add Category']")
        delay(2000)
        machine.runInsert(payload)
        machine.runRepeat("keycode_enter")
        machine.runRepeat("keycode_dpad_up", 99)
        machine.runRepeat("keycode_dpad_right", 9)
        machine.runRepeat("keycode_enter")
        machine.runRepeat("keycode_dpad_down", 4)
        machine.runRepeat("keycode_enter")
        machine.runSelect("//*[@content-desc='$bigness']")
        machine.runRepeat("keycode_back", 3)
    }

    suspend fun setApplicationByIndex(payload: String, section: Int, adapted: Boolean = true) = withContext(IO) {
        machine.runEscape()
        machine.setLocale(DeviceLocale.EN_US)
        runRevealSettings()
        machine.runSelect("//*[@content-desc='Applications']")
        if (!adapted) machine.runSelect("//*[@content-desc='Tab 2 of 3']")
        machine.runSelect("//*[@content-desc='$payload']/node[1]")
        machine.runRepeat("keycode_dpad_up", 99)
        machine.runRepeat("keycode_dpad_down", section - 1)
        machine.runRepeat("keycode_enter")
        machine.runRepeat("keycode_back", 2)
    }

    suspend fun setApplicationByTitle(payload: String, section: String, adapted: Boolean = true) = withContext(IO) {
        machine.runEscape()
        machine.setLocale(DeviceLocale.EN_US)
        runRevealSettings()
        machine.runSelect("//*[@content-desc='Applications']")
        if (!adapted) machine.runSelect("//*[@content-desc='Tab 2 of 3']")
        machine.runSelect("//*[@content-desc='$payload']/node[1]")
        machine.runSelect("//*[@content-desc='$section']")
        machine.runRepeat("keycode_back", 2)
    }

    suspend fun setWallpaper(payload: String) = withContext(IO) {
        machine.runEscape()
        machine.setLocale(DeviceLocale.EN_US)
        runRevealSettings()
        machine.runEnable("com.android.documentsui", enabled = true)
        machine.runSelect("//*[@content-desc='Wallpaper']")
        machine.runSelect("//*[@content-desc='Custom']")
        val fetched = getFromAddress(payload, context)
        if (fetched == null) return@withContext
        val distant = "/sdcard/Download/${File(fetched.path).name}"
        machine.runExport(fetched.path, distant)
        machine.runSelect("//*[@content-desc='Show roots']")
        machine.runRepeat("keycode_dpad_down", 2)
        machine.runRepeat("keycode_enter")
        machine.runSelect("//*[@content-desc='Search']")
        delay(2000)
        machine.runInsert(File(fetched.path).nameWithoutExtension)
        delay(2000)
        machine.runRepeat("keycode_enter")
        delay(2000)
        machine.runRepeat("keycode_enter")
        delay(4000)
        machine.runRepeat("keycode_back", 2)
    }

}