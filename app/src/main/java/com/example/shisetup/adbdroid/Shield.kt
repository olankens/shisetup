package com.example.shisetup.adbdroid

import android.content.Context
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class Shield(
    address: String,
    port: Int = 5555,
    code: String? = null,
    context: Context,
) : Device(address, port, code, context) {
    suspend fun setBloatware(enabled: Boolean) = withContext(IO) {
        val factors = listOf(
            // Nvidia bloatware
            "android.autoinstalls.config.nvidia",
            "com.nvidia.benchmarkblocker",
            "com.nvidia.beyonder.server",
            "com.nvidia.developerwidget",
            "com.nvidia.diagtools",
            "com.nvidia.enhancedlogging",
            "com.nvidia.factorybundling",
            "com.nvidia.feedback",
            "com.nvidia.hotwordsetup",
            "com.nvidia.NvAccSt",
            "com.nvidia.NvCPLUpdater",
            "com.nvidia.ocs",
            "com.nvidia.ota",
            "com.nvidia.shield.appselector",
            "com.nvidia.shield.ask",
            "com.nvidia.shield.nvcustomize",
            "com.nvidia.SHIELD.Platform.Analyser",
            "com.nvidia.shield.registration",
            "com.nvidia.shield.registration",
            "com.nvidia.shield.remote.server",
            "com.nvidia.shield.remotediagnostic",
            "com.nvidia.shieldbeta",
            "com.nvidia.shieldtech.hooks",
            "com.nvidia.shieldtech.proxy",
            "com.nvidia.stats",
            "com.nvidia.tegrazone3",
            // Android bloatware
            "com.android.gallery3d",
            "com.android.dreams.basic",
            "com.android.printspooler",
            "com.android.feedback",
            "com.android.keychain",
            "com.android.cts.priv.ctsshim",
            "com.android.cts.ctsshim",
            "com.android.providers.calendar",
            "com.android.providers.contacts",
            "com.android.se",
            "com.android.tv",
            // "com.android.vending",
            // Google bloatware
            "com.google.android.speech.pumpkin",
            "com.google.android.tts",
            "com.google.android.videos",
            "com.google.android.tvrecommendations",
            "com.google.android.syncadapters.calendar",
            "com.google.android.backuptransport",
            "com.google.android.partnersetup",
            "com.google.android.inputmethod.korean",
            "com.google.android.inputmethod.pinyin",
            "com.google.android.apps.inputmethod.zhuyin",
            "com.google.android.tv",
            "com.google.android.tv.frameworkpackagestubs",
            "com.google.android.tv.bugreportsender",
            "com.google.android.leanbacklauncher.recommendations",
            "com.google.android.tvlauncher",
            "com.google.android.feedback",
            "com.google.android.leanbacklauncher",
            // Extra bloatware
            "com.amazon.amazonvideo.livingroom",
            "com.google.android.play.games",
            "com.google.android.youtube.tv",
            "com.google.android.youtube.tvmusic",
            "com.netflix.ninja",
            "com.plexapp.android",
            "com.plexapp.mediaserver.smb",
        )
        val command = if (enabled) "cmd package install-existing" else "pm uninstall -k --user 0"
        for (pkgname in factors) {
            runInvoke("$command $pkgname")
            delay(200)
        }
    }

    suspend fun setGooglePlay() = withContext(IO) {
        // INFO: Uninstall google play and reinstall it to remove netflix button trigger
        runInvoke("pm uninstall -k --user 0 com.android.vending")
        delay(200)
        runInvoke("cmd package install-existing com.android.vending")
    }

    suspend fun setLocale(dialect: DeviceLocale) = withContext(IO) {
        var current = getLocale()
        if (current.isEmpty()) {
            runInvoke("am start -n com.android.tv.settings/.system.LanguageActivity")
            runRepeat("keycode_dpad_up", repeats = 99)
            runRepeat("keycode_enter")
            delay(5000)
        }
        current = getLocale()
        if (current != dialect.compact) {
            runInvoke("am start -n com.android.tv.settings/.system.LanguageActivity")
            runSelect("//*[@text='${dialect.content}']")
            runRepeat("keycode_enter")
            delay(5000)
        }
        runRepeat("keycode_home")
    }

    suspend fun setPictureInPicture(payload: String, enabled: Boolean) = withContext(IO) {
        setLocale(DeviceLocale.EN_US)
        runInvoke("am start -n com.android.tv.settings/com.android.tv.settings.MainSettings")
        runSelect("//*[@text='Apps']")
        runSelect("//*[@text='Special app access']")
        runSelect("//*[@text='Picture-in-picture']")
        delay(5000)
        val pattern = "//*[@text='$payload']/parent::*/following-sibling::*/node"
        val element = runScrape(pattern)
        if (element != null) {
            val checked = element.attributes.getNamedItem("checked").nodeValue == "true"
            val correct = (checked && enabled) || (!checked && !enabled)
            if (!correct) {
                runSelect("//*[@text='$payload']")
            }
        }
        runRepeat("keycode_home")
    }

    suspend fun setResolution(payload: ShieldResolution) = withContext(IO) {
        setLocale(DeviceLocale.EN_US)
        runInvoke("am start -n com.android.tv.settings/com.android.tv.settings.MainSettings")
        runSelect("//*[@text=\"Device Preferences\"]")
        runSelect("//*[@text=\"Display & Sound\"]")
        runSelect("//*[@text=\"Resolution\"]")
        val shaping = "//*[contains(@text, '%s') and contains(@text, '%s') and contains(@text, '%s')]"
        val factors = listOf(payload.payload[0], payload.payload[1], if (payload.payload[2] as Boolean) "Vision" else "Hz")
        val factor1 = shaping.format(*factors.toTypedArray()) + "/parent::*/parent::*/node[1]"
        runCatching {
            val target1 = runScrape(factor1)
            if (target1 != null) {
                if (target1.attributes.getNamedItem("checked").nodeValue == "true") {
                    runRepeat("keycode_back")
                } else {
                    runSelect(factor1)
                    runRepeat("keycode_dpad_right", repeats = 5)
                    runRepeat("keycode_dpad_up", repeats = 5)
                    runRepeat("keycode_enter")
                }
                runSelect("//*[@text=\"Advanced display settings\"]")
                val factor2 = "//*[@text=\"Match content color space\"]/parent::*/following-sibling::*/node"
                val target2 = runScrape(factor2)
                if (target2 != null) {
                    val checked = target2.attributes.getNamedItem("checked").nodeValue == "true"
                    val correct = (checked && payload.payload[2] as Boolean) || (!checked && !(payload.payload[2] as Boolean))
                    if (!correct) runSelect(factor2)
                }
            }
        }
        runRepeat("keycode_home")
    }

    suspend fun setUpscaling(payload: ShieldUpscaling) = withContext(IO) {
        setLocale(DeviceLocale.EN_US)
        runInvoke("am start -n com.android.tv.settings/com.android.tv.settings.MainSettings")
        runSelect("//*[@text='Device Preferences']")
        runSelect("//*[@text='Display & Sound']")
        runSelect("//*[@text='AI upscaling']")
        runSelect("//*[@text='${payload.payload[0]}']")
        if (payload.payload[1].isNotEmpty()) runSelect("//*[@text='${payload.payload[1]}']")
        runRepeat("keycode_home")
    }
}