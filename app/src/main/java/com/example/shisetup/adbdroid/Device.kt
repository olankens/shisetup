package com.example.shisetup.adbdroid

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Picture
import android.graphics.Point
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import com.flyfishxu.kadb.shell.AdbShellResponse
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

@SuppressLint("SdCardPath")
abstract class Device(
    private val address: String,
    private val port: Int = 5555,
    private val code: String? = null,
    private val context: Context,
) {
    private lateinit var manager: Kadb

    suspend fun getIpAddr(): String? = withContext(IO) {
        for (adapter in listOf("eth0", "wlan0")) {
            val command = "ip -f inet -o addr show $adapter | cut -d \" \" -f 7 | cut -d / -f 1"
            val address = runInvoke(command).output.trim()
            if (address.isNotEmpty()) return@withContext address
        }
        return@withContext null
    }

    suspend fun getLocale(): String = withContext(IO) {
        return@withContext (runInvoke("getprop persist.sys.locale")).output.trim()
    }

    suspend fun getSeated(pkgname: String): Boolean = withContext(IO) {
        return@withContext (runInvoke("pm path '$pkgname'")).output.isNotEmpty()
    }

    suspend fun runAccord(pkgname: String, consent: String) = withContext(IO) {
        val command = "pm grant '$pkgname' android.permission.${consent.uppercase()}"
        runInvoke(command)
    }

    suspend fun runAttach() = withContext(IO) {
        runKeygen()
        if (code != null) Kadb.pair(address, port, code)
        manager = Kadb.create(address, port, connectTimeout = 1000, socketTimeout = 1000).also {
            val command = "connect $address:$port"
            it.shell(command)
        }
    }

    suspend fun runCreate(distant: String) = withContext(IO) {
        runInvoke("mkdir -p \"\$(dirname \"$distant\")\" ; touch \"$distant\"")
    }

    suspend fun runEnable(pkgname: String, enabled: Boolean = true) = withContext(IO) {
        runInvoke("pm ${if (enabled) "enable" else "disable-user --user 0"} \"$pkgname\"")
    }

    suspend fun runEscape() = withContext(IO) {
        repeat(2) {
            runRepeat("keycode_back", repeats = 8)
            runRepeat("keycode_home")
        }
        runRepeat("keycode_wakeup", repeats = 2)
        delay(2000)
    }

    suspend fun runExport(storage: String, distant: String) = withContext(IO) {
        manager.push(File(storage), distant)
    }

    suspend fun runFinish(pkgname: String) = withContext(IO) {
        if (getSeated(pkgname)) {
            runInvoke("sleep 2 ; am force-stop \"$pkgname\" ; sleep 2")
        }
    }

    suspend fun runImport(distant: String): String = withContext(IO) {
        val created = File(context.cacheDir, File(distant).name)
        manager.pull(created, distant)
        created.path
    }

    suspend fun runInsert(content: String, cleared: Boolean = false) = withContext(IO) {
        if (cleared) {
            runRepeat("keycode_move_end")
            // TODO: Try select all + delete instead
            runRepeat("keycode_del", repeats = 100)
        }
        runInvoke("input text '$content'")
    }

    suspend fun runInvoke(command: String): AdbShellResponse = withContext(IO) {
        manager.shell(command)
    }

    suspend fun runKeygen() = withContext(IO) {
        val sharing = context.getSharedPreferences("kadb", Context.MODE_PRIVATE)
        val cert = sharing.getString("cert", null)
        val key = sharing.getString("key", null)
        if (cert.isNullOrEmpty() || key.isNullOrEmpty()) {
            val pair = KadbCert.get()
            sharing.edit()
                .putString("cert", pair.first.decodeToString())
                .putString("key", pair.second.decodeToString()).apply()
            return@withContext
        }
        try {
            KadbCert.set(cert.toByteArray(), key.toByteArray())
        } catch (e: Exception) {
            if (e is CertificateExpiredException || e is CertificateNotYetValidException) {
                val pair = KadbCert.get()
                sharing.edit().putString("cert", pair.first.decodeToString())
                    .putString("key", pair.second.decodeToString()).apply()
            }
            throw e
        }
    }

    suspend fun runLaunch(pkgname: String) = withContext(IO) {
        if (getSeated(pkgname)) {
            runInvoke("sleep 2 ; monkey -p \"$pkgname\" 1 ; sleep 2")
        }
    }

    suspend fun runLocate(pattern: String): Point? = withContext(IO) {
        val element = runScrape(pattern) ?: return@withContext null
        val content = element.attributes.getNamedItem("bounds")?.nodeValue
        val matches = content.let { Regex("[0-9]+").findAll(it.toString()).map(MatchResult::value).toList() }
        return@withContext Point(
            (matches[0].toInt() + matches[2].toInt()) / 2,
            (matches[1].toInt() + matches[3].toInt()) / 2
        )
    }

    suspend fun runLookup(picture: Picture, pattern: Regex): List<MatchResult> = withContext(IO) {
        throw NotImplementedError() // TODO: Implement
    }

    suspend fun runReboot() = withContext(IO) {
        runCatching { manager.shell("reboot") }
        while (true) {
            try {
                runAttach()
                break
            } catch (e: Exception) {
                delay(2000)
            }
        }
        delay(2000)
    }

    suspend fun runRemove(distant: String) = withContext(IO) {
        runInvoke("rm -r $distant")
        return@withContext
    }

    suspend fun runRender(): String = withContext(IO) {
        val command = "uiautomator dump"
        val fetched = "/sdcard/window_dump.xml"
        val pkgname = "com.android.vending"
        while (runInvoke(command).errorOutput.trim().isNotEmpty()) {
            runRemove(fetched)
            runLaunch(pkgname)
            runInvoke(command)
            runFinish(pkgname)
        }
        return@withContext runImport(fetched)
    }

    suspend fun runRepeat(keycode: String, repeats: Int = 1) = withContext(IO) {
        runInvoke("input keyevent \$(printf '${keycode.uppercase()} %.0s' \$(seq 1 $repeats))")
    }

    suspend fun runScrape(pattern: String): Node? = withContext(IO) {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        runRepeat("keycode_dpad_up", 100)
        var fetched = runRender()
        var element: Node? = null
        while (element == null) {
            val scraped = builder.parse(File(fetched))
            val factory = XPathFactory.newInstance()
            val factors = factory.newXPath().evaluate(pattern, scraped, XPathConstants.NODESET) as NodeList
            if (factors.length > 0) element = factors.item(0)
            if (element != null) continue
            runRepeat("keycode_dpad_down", 8)
            val content = File(fetched).readText()
            fetched = runRender()
            if (content == File(fetched).readText()) break
        }
        return@withContext element
    }

    suspend fun runScreen(distant: String = "/sdcard/screenshot.png"): String = withContext(IO) {
        runInvoke("screencap -p $distant")
        return@withContext runImport(distant)
    }

    suspend fun runSearch(pattern: String, maximum: Int = 1): List<String>? = withContext(IO) {
        val results = runInvoke("find $pattern -maxdepth 0 2>/dev/null | head -$maximum")
        val content = results.output.trim()
        return@withContext if (content.isNotEmpty()) content.split("\n") else null
    }

    suspend fun runSelect(pattern: String): Boolean = withContext(IO) {
        val results = runLocate(pattern) ?: return@withContext false
        runInvoke("input tap ${results.x} ${results.y}")
        return@withContext true
    }

    suspend fun runUnpack(archive: String, distant: String) = withContext(IO) {
        if (File(archive).exists()) {
            runInvoke("mkdir -p '$distant'")
            runExport(archive, "$distant/${File(archive).name}")
            runInvoke("cd '$distant' ; unzip -o '${File(archive).name}'")
            runRemove("$distant/${File(archive).name}")
        }
    }

    suspend fun runUpdate(pkgpath: String, options: String = "-r", maximum: Int = 5) = withContext(IO) {
        if (File(pkgpath).exists()) {
            var counter = 0
            // HACK: Inconsistent install depending on Android ROMs
            // INFO: Retrying 5 times works over 99.99999999%
            while (counter++ < maximum) if (runCatching { manager.install(File(pkgpath), options) }.isSuccess) break
        }
    }

    suspend fun runVanish(pkgname: String) = withContext(IO) {
        if (getSeated(pkgname)) {
            runFinish(pkgname)
            manager.uninstall(pkgname)
        }
    }
}