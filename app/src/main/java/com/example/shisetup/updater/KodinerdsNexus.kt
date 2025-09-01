package com.example.shisetup.updater

import android.content.Context
import com.example.shisetup.adbdroid.Shield
import com.example.shisetup.netsense.Netsense.Companion.getFromAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class KodinerdsNexus(machine: Shield, context: Context) : Kodi(machine, context) {
    override val pkgname = "net.kodinerds.maven.kodi20"
    override val heading = "Kodinerds"
    override val release = "nexus"

    override suspend fun runGather(): File? = withContext(Dispatchers.IO) {
        val fetcher = OkHttpClient()
        val baseurl = "https://repo.kodinerds.net"
        val website = "$baseurl/index.php?action=list&scope=cat&item=Binary%20(arm64-v8a)"
        val request = Request.Builder().url(website).header("User-Agent", "mozilla/5.0").build()
        val execute = fetcher.newCall(request).execute()
        if (!execute.isSuccessful) return@withContext null
        val content = execute.body?.string()
        val pattern = Regex("aktuelle.*download=(.*Nexus.apk)(?=\")")
        val address = pattern.find(content ?: "")?.groups?.get(1)?.value
        return@withContext getFromAddress("$baseurl/$address", context)
    }
}