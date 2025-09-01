package com.example.shisetup.netsense

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import org.json.JSONObject
import java.io.File
import java.net.URI

class Netsense {
    companion object {
        suspend fun getFromAddress(address: String, context: Context): File? = withContext(IO) {
            val fetcher = OkHttpClient()
            val request = Request.Builder().url(address).header("User-Agent", "Mozilla/5.0").build()
            val content = fetcher.newCall(request).execute()
            if (content.body != null) {
                val fetched = File(context.cacheDir, getSuggestedFilename(address))
                if (!fetched.exists()) {
                    content.body?.source().use { input ->
                        fetched.sink().buffer().use { output ->
                            input?.let { output.writeAll(it) }
                        }
                    }
                }
                return@withContext fetched
            }
            return@withContext null
        }

        suspend fun getFromDropbox(address: String, context: Context): File? = withContext(IO) {
            val changed = Uri.parse(address).buildUpon().clearQuery().appendQueryParameter("dl", "1").build().toString()
            getFromAddress(changed, context)
        }

        suspend fun getFromGithub(address: String, pattern: Regex, context: Context): File? = withContext(IO) {
            val fetcher = OkHttpClient()
            val request = Request.Builder().url(address).header("User-Agent", "Mozilla/5.0").build()
            val content = fetcher.newCall(request).execute()
            if(content.body != null) {
                val scraped = content.body?.string()
                if (scraped != null) {
                    val factors = JSONObject(scraped).getJSONArray("assets")
                    val members = mutableListOf<String>()
                    for (i in 0 until factors.length()) {
                        val element = factors.getJSONObject(i)
                        members.add(element.getString("browser_download_url"))
                    }
                    val matched = members.firstOrNull { pattern.matches(it) }
                    if (matched != null) {
                        return@withContext getFromAddress(matched, context)
                    }
                }
            }
            return@withContext null
        }

        private suspend fun getSuggestedFilename(address: String): String = withContext(IO) {
            val manager = OkHttpClient()
            val request = Request.Builder().url(address).build()
            val scraped = manager.newCall(request).execute()
            val headers = scraped.headers
            if (headers["content-disposition"] != null) {
                val pattern = "filename=(\"|\'|)([^\"\']*)"
                val content = headers["content-disposition"].toString()
                Regex(pattern).find(content)?.groupValues?.get(2)
            }
            File(URI(address).path).name ?: ""
        }
    }
}