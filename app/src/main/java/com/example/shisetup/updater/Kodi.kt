package com.example.shisetup.updater

import Updater
import android.annotation.SuppressLint
import android.content.Context
import com.example.shisetup.adbdroid.DeviceLocale
import com.example.shisetup.adbdroid.Shield
import com.example.shisetup.netsense.Netsense.Companion.getFromAddress
import com.example.shisetup.netsense.Netsense.Companion.getFromGithub
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import java.io.IOException
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import javax.xml.xpath.XPathConstants.NODE
import javax.xml.xpath.XPathConstants.NODESET
import javax.xml.xpath.XPathFactory

@SuppressLint("SdCardPath")
open class Kodi(machine: Shield, context: Context) : Updater(machine, context) {
    override val pkgname = "org.xbmc.kodi"
    override val heading = "Kodi"
    open val deposit by lazy { "/sdcard/Android/data/$pkgname/files/.kodi" }
    open val release = "omega"

    ///

    override suspend fun runGather(): File? {
        throw NotImplementedError()
    }

    override suspend fun runRemove() = withContext(IO) {
        super.runRemove()
        machine.runRemove("/sdcard/Android/data/$pkgname")
    }

    override suspend fun runUpdate() = withContext(IO) {
        if (getRecent()) return@withContext
        val fetched = runGather()
        if (fetched == null) return@withContext
        machine.runUpdate(fetched.path)
        setKodiPermissions()
        machine.runLaunch(pkgname)
        delay(10000)
        machine.runFinish(pkgname)
        machine.runRepeat("keycode_home")
    }

    ///

    suspend fun runRpc(payload: Map<String, Any>): Response = withContext(IO) {
        val command = "netstat -an | grep 8080 | grep -i listen"
        val present = machine.runInvoke(command).output.trim().isNotEmpty()
        if (!present) throw Exception("Instance is not running, please ensure to launch it")

        val address = machine.getIpAddr()
        val client = OkHttpClient()
        val body = JSONObject(payload).toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("http://$address:8080/jsonrpc")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()

        return@withContext try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw Exception("Error during RPC request: ${e.message}")
        }
    }

    suspend fun runRpcLaunch(addonid: String) {
        runRpc(
            mapOf(
                "jsonrpc" to "2.0",
                "method" to "Addons.ExecuteAddon",
                "params" to mapOf(
                    "addonid" to addonid
                ),
                "id" to 1
            )
        )
    }

    suspend fun runRpcSelect() {
        runRpc(
            mapOf(
                "jsonrpc" to "2.0",
                "method" to "Input.Select",
                "params" to mapOf<String, Any>(),
                "id" to 1
            )
        )
    }

    suspend fun setRpcSetting(setting: String, payload: Any) {
        runRpc(
            mapOf(
                "jsonrpc" to "2.0",
                "method" to "Settings.SetSettingValue",
                "params" to mapOf("setting" to setting, "value" to payload),
                "id" to 1
            )
        )
    }

    suspend fun setXmlSetting(distant: String, pattern: String, payload: String, adjunct: Boolean = true) = withContext(IO) {
        if (hasKodiWebserver()) throw Exception("Instance is running, please ensure to finish it")

        val fetched = machine.runImport(distant)
        val content = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(fetched))
        val compile = XPathFactory.newInstance().newXPath().compile(pattern)
        val nodelst = compile.evaluate(content, NODESET) as NodeList

        val element: Element
        if (nodelst.length > 0) {
            element = nodelst.item(0) as Element
        } else {
//            // Create the element if it doesn't exist
//            val parentPath = pattern.substringBeforeLast("/")
//            val parentCompile = XPathFactory.newInstance().newXPath().compile(parentPath)
//            val parentNode = parentCompile.evaluate(content, NODE) as Node
//            element = content.createElement(pattern.substringAfterLast("/"))
//            parentNode.appendChild(element)
            val root = content.documentElement
            element = content.createElement("setting")
            element.setAttribute("id", pattern.substringAfterLast("@id='").removeSuffix("']"))
            root.appendChild(element)
        }

        element.textContent = payload
        if (adjunct) element.setAttribute("default", "false")

        val writing = StringWriter()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(content), StreamResult(writing))
        File(fetched).writeText(writing.toString())
        machine.runExport(fetched, distant)
    }


    suspend fun setXmlSettingOld(distant: String, pattern: String, payload: String, adjunct: Boolean = true) = withContext(IO) {
        if (hasKodiWebserver()) throw Exception("Instance is running, please ensure to finish it")

        val fetched = machine.runImport(distant)
        val content = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(fetched))
        val compile = XPathFactory.newInstance().newXPath().compile(pattern)
        val nodelst = compile.evaluate(content, NODESET) as NodeList
        if (nodelst.length > 0) {
            val element = nodelst.item(0) as Element
            element.textContent = payload
            if (adjunct) element.setAttribute("default", "false")
        }
        val writing = StringWriter()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(content), StreamResult(writing))
        File(fetched).writeText(writing.toString())
        machine.runExport(fetched, distant)
    }

    ///

    suspend fun hasKodiAddon(payload: String): Boolean {
        val command = "test -d '$deposit/addons/$payload'"
        val results = machine.runInvoke(command)
        return results.exitCode == 0
    }

    suspend fun hasKodiWebserver(): Boolean {
        val command = "netstat -an | grep 8080 | grep -i listen"
        val results = machine.runInvoke(command)
        return results.output.trim().isNotEmpty()
    }

    suspend fun setKodiAddonEnabled(payload: String, enabled: Boolean = true) {
        runRpc(
            mapOf(
                "jsonrpc" to "2.0",
                "method" to "Addons.SetAddonEnabled",
                "params" to mapOf("addonid" to payload, "enabled" to enabled),
                "id" to 1
            )
        )
    }

    suspend fun setKodiAddonInstall(payload: String) {
        runRpc(
            mapOf(
                "jsonrpc" to "2.0",
                "method" to "Addons.Install",
                "params" to mapOf("addonid" to payload),
                "id" to 1
            )
        )
    }

    suspend fun setKodiAfr(enabled: Boolean = false) {
        setRpcSetting("videoplayer.adjustrefreshrate", if (enabled) 2 else 0)
    }

    suspend fun setKodiAfrDelay(seconds: Double = 0.0) {
        if (seconds < 0 || seconds > 20) return
        setRpcSetting("videoscreen.delayrefreshchange", (seconds * 10).toInt())
    }

    suspend fun setKodiAudioPassthrough(enabled: Boolean = false) {
        val distant = "$deposit/userdata/guisettings.xml"
        val payload = if (enabled) listOf("true", "10") else listOf("false", "1")
        setXmlSetting(distant, "//*[@id='audiooutput.channels']", payload[1])
        setXmlSetting(distant, "//*[@id='audiooutput.passthrough']", payload[0])
        setXmlSetting(distant, "//*[@id='audiooutput.dtshdpassthrough']", payload[0])
        setXmlSetting(distant, "//*[@id='audiooutput.dtspassthrough']", payload[0])
        setXmlSetting(distant, "//*[@id='audiooutput.eac3passthrough']", payload[0])
        setXmlSetting(distant, "//*[@id='audiooutput.truehdpassthrough']", payload[0])
    }

    suspend fun setKodiDependency(payload: String, imposed: Boolean = false) = withContext(IO) {
        val present = hasKodiAddon(payload)
        if (!imposed && present) return@withContext
        val fetcher = OkHttpClient()
        // INFO: https://mirrors.kodi.tv/addons/piers/
        val baseurl = "https://mirrors.kodi.tv/addons/$release"
        val website = "$baseurl/$payload/?C=M&O=D"
        val request = Request.Builder().url(website).header("User-Agent", "Mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string()
        // INFO: https://mirrors.kodi.tv/addons/piers/inputstream.adaptive+android-aarch64/
        val pattern = Regex("href=\"${payload.substringBefore("+")}-(.*?)(?=.zip\" )")
        val matchResult = content?.let { pattern.findAll(it).lastOrNull() }
        val version = matchResult?.groups?.get(1)?.value
        val address = "$baseurl/$payload/${payload.substringBefore("+")}-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")
    }

    suspend fun setKodiEnableKeymapFix(enabled: Boolean = false) {
        val distant = "$deposit/userdata/keymaps/keyboard.xml"
        machine.runRemove(distant)
        if (!enabled) return
        val configs = File(context.cacheDir, "keyboard.xml")
        configs.writeText(
            """
                <keymap>
                    <fullscreenvideo>
                        <keyboard>
                            <back>Stop</back>
                            <backspace>Stop</backspace>
                        </keyboard>
                        <remote>
                            <back>Stop</back>
                            <backspace>Stop</backspace>
                        </remote>
                    </fullscreenvideo>
                </keymap>
            """.trimIndent()
        )
        machine.runExport(configs.path, distant)
    }

    suspend fun setKodiEnablePreferDefaultAudio(enabled: Boolean = false) {
        setRpcSetting("videoplayer.preferdefaultflag", enabled)
    }

    suspend fun setKodiEnableShowParentFolder(enabled: Boolean = false) {
        setRpcSetting("filelists.showparentdiritems", enabled)
    }

    suspend fun setKodiEnableUnknownSources(enabled: Boolean = false) {
        val distant = "$deposit/userdata/guisettings.xml"
        setXmlSetting(distant, "//*[@id='addons.unknownsources']", if (enabled) "true" else "false")
    }

    suspend fun setKodiEnableUpdateFromAnyRepositories(enabled: Boolean = false) {
        setRpcSetting("addons.updatemode", if (enabled) 1 else 0)
    }

    suspend fun setKodiFavourite(heading: String, variant: String, starter: String, picture: String? = null) {
        runRpc(
            mapOf(
                "jsonrpc" to "2.0",
                "method" to "Favourites.AddFavourite",
                "params" to mapOf(
                    "title" to heading,
                    "type" to "window",
                    "window" to variant,
                    "windowparameter" to starter,
                    "thumbnail" to (picture ?: "")
                ),
                "id" to 1
            )
        )
    }

    suspend fun setKodiKeyboardList(payload: List<String>) {
        setRpcSetting("locale.keyboardlayouts", payload)
        setRpcSetting("locale.activekeyboardlayout", payload)
    }

    suspend fun setKodiLanguageForAudio(payload: String) {
        setRpcSetting("locale.audiolanguage", payload)
    }

    suspend fun setKodiLanguageForSubtitles(payload: String) {
        setRpcSetting("locale.subtitlelanguage", payload)
    }

    suspend fun setKodiLanguageForSystem(payload: String) {
        setKodiDependency("resource.language.$payload")
        setRpcSetting("locale.language", "resource.language.$payload")
    }

    suspend fun setKodiLanguageListForDownloadedSubtitles(payload: List<String>) {
        setRpcSetting("subtitles.languages", payload)
    }

    suspend fun setKodiPermissions() {
        machine.runAccord(pkgname, "read_external_storage")
        machine.runAccord(pkgname, "write_external_storage")
        machine.runEscape()
        machine.setLocale(DeviceLocale.EN_US)
        machine.runInvoke("am start -n com.android.tv.settings/com.android.tv.settings.MainSettings")
        machine.runSelect("//*[@text='Apps']")
        machine.runSelect("//*[@text='See all apps']")
        machine.runSelect("//*[@text='$heading']")
        machine.runSelect("//*[@text='Permissions']")
        machine.runRepeat("keycode_dpad_down")
        machine.runRepeat("keycode_enter")
        machine.runSelect("//*[@text='Allow all the time']") // Old one
        machine.runSelect("//*[@text='Allow management of all files']") // New one
        machine.runRepeat("keycode_tab", repeats = 2)
        machine.runRepeat("keycode_enter")
        machine.runRepeat("keycode_back")
        machine.runRepeat("keycode_tab")
        machine.runRepeat("keycode_enter")
        machine.runSelect("//*[@text='Allow only while using the app']")
        machine.runRepeat("keycode_home")
    }

    suspend fun setKodiSettingLevel(payload: String) {
        val distant = "$deposit/userdata/guisettings.xml"
        setXmlSetting(distant, "//general/settinglevel", payload)
    }

    suspend fun setKodiSubtitleBorderSize(payload: Int = 25) {
        if (payload < 0 || payload > 100) return
        setRpcSetting("subtitles.bordersize", payload)
    }

    suspend fun setKodiSubtitleColor(payload: String = "FFFFFFFF") {
        setRpcSetting("subtitles.colorpick", payload)
    }

    suspend fun setKodiSubtitleServiceForMovies(payload: String) {
        setRpcSetting("subtitles.movie", payload)
    }

    suspend fun setKodiSubtitleServiceForSeries(payload: String) {
        setRpcSetting("subtitles.tv", payload)
    }

    suspend fun setKodiTvShowSelectFirstUnwatchedItem(enabled: Boolean = false) {
        setRpcSetting("videolibrary.tvshowsselectfirstunwatcheditem", if (enabled) 2 else 0)
    }

    suspend fun setKodiWebserver(enabled: Boolean = false, secured: Boolean = true) = withContext(IO) {
        val distant = "$deposit/userdata/guisettings.xml"
        setXmlSetting(distant, "//*[@id='services.webserver']", if (enabled) "true" else "false")
        setXmlSetting(distant, "//*[@id='services.esenabled']", if (enabled) "true" else "false")
        setXmlSetting(distant, "//*[@id='services.webserverauthentication']", if (secured) "true" else "false")
        if (!enabled) return@withContext
        machine.runLaunch(pkgname)
        machine.runLaunch(pkgname)
        val command = "netstat -an | grep 8080 | grep -i listen"
        while ((machine.runInvoke(command).output.trim()).isEmpty()) {
            delay(1000) // Wait for 1 second before retrying
        }
    }

    ///

    suspend fun setEstuaryColor(payload: String) {
        val distant = "$deposit/userdata/guisettings.xml"
        setXmlSetting(distant, "//*[@id='lookandfeel.skincolors']", payload)
    }

    suspend fun setEstuaryMenuList(enabled: Boolean = true) {
        setEstuaryFavourites(enabled)
        setEstuaryGames(enabled)
        setEstuaryMovie(enabled)
        setEstuaryMusic(enabled)
        setEstuaryMusicVideo(enabled)
        setEstuaryPictures(enabled)
        setEstuaryPrograms(enabled)
        setEstuaryRadio(enabled)
        setEstuaryTv(enabled)
        setEstuaryTvShow(enabled)
        setEstuaryVideos(enabled)
        setEstuaryWeather(enabled)
    }

    suspend fun setEstuaryMenu(payload: String, enabled: Boolean = true) {
        val replace = payload.replaceFirst("homemenuno", "")
        val distant = "$deposit/userdata/addon_data/skin.estuary/settings.xml"
        setXmlSetting(distant, "//*[@id='homemenuno$replace']", (!enabled).toString())
    }

    suspend fun setEstuaryFavourites(enabled: Boolean = true) {
        setEstuaryMenu("favbutton", enabled)
    }

    suspend fun setEstuaryGames(enabled: Boolean = true) {
        setEstuaryMenu("gamesbutton", enabled)
    }

    suspend fun setEstuaryMovie(enabled: Boolean = true) {
        setEstuaryMenu("moviebutton", enabled)
    }

    suspend fun setEstuaryMusic(enabled: Boolean = true) {
        setEstuaryMenu("musicbutton", enabled)
    }

    suspend fun setEstuaryMusicVideo(enabled: Boolean = true) {
        setEstuaryMenu("musicvideobutton", enabled)
    }

    suspend fun setEstuaryPictures(enabled: Boolean = true) {
        setEstuaryMenu("picturesbutton", enabled)
    }

    suspend fun setEstuaryPrograms(enabled: Boolean = true) {
        setEstuaryMenu("programsbutton", enabled)
    }

    suspend fun setEstuaryRadio(enabled: Boolean = true) {
        setEstuaryMenu("radiobutton", enabled)
    }

    suspend fun setEstuaryTv(enabled: Boolean = true) {
        setEstuaryMenu("tvbutton", enabled)
    }

    suspend fun setEstuaryTvShow(enabled: Boolean = true) {
        setEstuaryMenu("tvshowbutton", enabled)
    }

    suspend fun setEstuaryVideos(enabled: Boolean = true) {
        setEstuaryMenu("videosbutton", enabled)
    }

    suspend fun setEstuaryWeather(enabled: Boolean = true) {
        setEstuaryMenu("weatherbutton", enabled)
    }

    ///

    suspend fun setLocalFavourites() {
        setKodiFavourite("[B]ONGOING[/B]\n ", "10025", "videodb://inprogresstvshows", "DefaultInProgressShows.png")
        setKodiFavourite("[B]TVSHOWS[/B]\n ", "10025", "videodb://tvshows/titles/", "DefaultTVShows.png")
        setKodiFavourite("[B]THEATER[/B]\n ", "10025", "videodb://movies/titles/", "DefaultMovies.png")
        setKodiFavourite("[B]PLUGINS[/B]\n ", "10025", "addons://sources/video/", "DefaultAddSource.png")
    }

    ///

    suspend fun setCocoscraperAddon() = withContext(IO) {
        val payload = "script.module.cocoscrapers"
        if (hasKodiAddon(payload)) return@withContext
        setCocoscraperAddonDependencies()
        setCocoscraperAddonRepository()
        val fetcher = OkHttpClient().newBuilder().followRedirects(true).build()
        val baseurl = "https://github.com/CocoJoe2411/repository.cocoscrapers"
        val website = "$baseurl/tree/main/zips/script.module.cocoscrapers"
        val pattern = Regex("script.module.cocoscrapers-([\\d.]+).zip")
        val request = Request.Builder().url(website).addHeader("User-Agent", "mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string() ?: throw Exception("Failed to fetch content")
        val version = pattern.findAll(content).lastOrNull()?.groups?.get(1)?.value
        val address = "$baseurl/raw/main/zips/script.module.cocoscrapers/script.module.cocoscrapers-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(payload, enabled = true)
        machine.runRepeat("keycode_home")
    }

    suspend fun setCocoscraperAddonDependencies() {
        setKodiDependency("script.module.requests")
        setKodiDependency("script.module.urllib3")
    }

    suspend fun setCocoscraperAddonRepository() {
        val payload = "repository.cocoscrapers"
        if (hasKodiAddon(payload)) return
        val fetcher = OkHttpClient().newBuilder().followRedirects(true).build()
        val baseurl = "https://github.com/CocoJoe2411/repository.cocoscrapers"
        val website = "$baseurl/tree/main/zips/repository.cocoscrapers"
        val pattern = Regex("repository.cocoscrapers-([\\d.]+).zip")
        val request = Request.Builder().url(website).addHeader("User-Agent", "mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string()
        val matchResult = content?.let { pattern.findAll(it).lastOrNull() }
        val version = matchResult?.groups?.get(1)?.value
        val address = "$baseurl/raw/main/zips/repository.cocoscrapers/repository.cocoscrapers-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(payload, enabled = true)
        machine.runRepeat("keycode_home")
    }

    ///

    suspend fun setCuminationAddon() = withContext(IO) {
        val payload = "plugin.video.cumination"
        if (hasKodiAddon(payload)) return@withContext
        setCuminationAddonDependencies()
        setCuminationAddonRepository()
        val fetcher = OkHttpClient.Builder().followRedirects(true).build()
        val website = "https://github.com/dobbelina/repository.dobbelina/tree/master/plugin.video.cumination"
        val pattern = Regex("plugin.video.cumination-([\\d.]+).zip")
        val request = Request.Builder().url(website).addHeader("User-Agent", "mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string()
        val version = content?.let { pattern.find(it)?.groupValues?.get(1) }
        val address =
            "https://github.com/dobbelina/repository.dobbelina/raw/refs/heads/master/plugin.video.cumination/plugin.video.cumination-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(payload, enabled = true)
        setCuminationFavourites()
        machine.runRepeat("keycode_home")
    }

    suspend fun setCuminationAddonDependencies() {
        setKodiDependency("script.module.chardet")
        setKodiDependency("script.module.six")
        setKodiDependency("script.module.kodi-six")
        setKodiDependency("script.module.resolveurl")
        setKodiDependency("script.module.resolveurl.xxx")
        setKodiDependency("script.common.plugin.cache")
        setKodiDependency("script.module.websocket")
        setKodiDependency("script.module.inputstreamhelper")
        setKodiDependency("script.module.requests")
        setKodiDependency("script.module.urllib3")

    }

    suspend fun setCuminationAddonRepository() {
        val payload = "repository.dobbelina"
        if (hasKodiAddon(payload)) return
        val fetcher = OkHttpClient().newBuilder().followRedirects(true).build()
        val website = "https://dobbelina.github.io/"
        val pattern = Regex("repository.dobbelina-([\\d.]+).zip")
        val request = Request.Builder().url(website).addHeader("User-Agent", "mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string()
        val matchResult = content?.let { pattern.findAll(it).lastOrNull() }
        val version = matchResult?.groups?.get(1)?.value
        val address = "https://github.com/dobbelina/repository.dobbelina/raw/refs/heads/master/repository.dobbelina-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(payload, enabled = true)
        machine.runRepeat("keycode_home")
    }

    suspend fun setCuminationFavourites() {
        val picture = "$deposit/addons/plugin.video.cumination/icon.png"
        setKodiFavourite(
            "[B]CUMINATION[/B]\n ",
            "10025",
            "plugin://plugin.video.cumination",
            ""
        )
    }

    ///

    suspend fun setOtakuAddon() = withContext(IO) {
        val addonid = "plugin.video.otaku"
        if (hasKodiAddon(addonid)) return@withContext
        setOtakuAddonDependencies()
        setOtakuAddonRepository()
        val fetcher = OkHttpClient.Builder().followRedirects(true).build()
        val baseurl = "https://github.com/Goldenfreddy0703"
        val website = "$baseurl/Otaku"
        val pattern = Regex("$addonid-([\\d.]+).zip")
        val request = Request.Builder().url(website).addHeader("User-Agent", "mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string()
        val version = content?.let { pattern.find(it)?.groupValues?.get(1) }
        val address = "$baseurl/repository.otaku/raw/master/repo/zips/$addonid/$addonid-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(addonid, enabled = true)
        setOtakuFavourites()
        runRpcLaunch(addonid)
        delay(5000)
        runRpcSelect()
        delay(5000)
        machine.runRepeat("keycode_home")
    }

    suspend fun setOtakuAddonDependencies() {
        setKodiDependency("script.module.beautifulsoup4")
        setKodiDependency("script.module.dateutil")
        setKodiDependency("script.module.distutils")
        setKodiDependency("script.module.inputstreamhelper")
        setKodiDependency("script.module.six")
        setKodiDependency("script.module.kodi-six")
        setKodiDependency("script.module.soupsieve")
        setKodiDependency("inputstream.adaptive+android-aarch64")

        setOtakuAddonContext()
        setOtakuAddonThemepak()
        setOtakuAddonMappings()
    }

    suspend fun setOtakuAddonContext() = withContext(IO) {
        val addonid = "context.otaku"
        if (hasKodiAddon(addonid)) return@withContext
        val fetcher = OkHttpClient.Builder().followRedirects(true).build()
        val baseurl = "https://github.com/Goldenfreddy0703"
        val website = "$baseurl/Otaku"
        val pattern = Regex("$addonid-([\\d.]+).zip")
        val request = Request.Builder().url(website).addHeader("User-Agent", "mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string()
        val version = content?.let { pattern.find(it)?.groupValues?.get(1) }
        val address = "$baseurl/repository.otaku/raw/master/repo/zips/$addonid/$addonid-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(addonid, enabled = true)
        machine.runRepeat("keycode_home")
    }

    suspend fun setOtakuAddonThemepak() = withContext(IO) {
        val addonid = "script.otaku.themepak"
        if (hasKodiAddon(addonid)) return@withContext
        val fetcher = OkHttpClient.Builder().followRedirects(true).build()
        val baseurl = "https://github.com/Goldenfreddy0703"
        val website = "$baseurl/Otaku"
        val pattern = Regex("$addonid-([\\d.]+).zip")
        val request = Request.Builder().url(website).addHeader("User-Agent", "mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string()
        val version = content?.let { pattern.find(it)?.groupValues?.get(1) }
        val address = "$baseurl/repository.otaku/raw/master/repo/zips/$addonid/$addonid-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(addonid, enabled = true)
        machine.runRepeat("keycode_home")
    }

    suspend fun setOtakuAddonMappings() = withContext(IO) {
        val addonid = "script.otaku.mappings"
        if (hasKodiAddon(addonid)) return@withContext
        val fetcher = OkHttpClient.Builder().followRedirects(true).build()
        val baseurl = "https://github.com/Goldenfreddy0703"
        val website = "$baseurl/Otaku"
        val pattern = Regex("$addonid-([\\d.]+).zip")
        val request = Request.Builder().url(website).addHeader("User-Agent", "mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string()
        val version = content?.let { pattern.find(it)?.groupValues?.get(1) }
        val address = "$baseurl/repository.otaku/raw/master/repo/zips/$addonid/$addonid-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(addonid, enabled = true)
        machine.runRepeat("keycode_home")
    }

    suspend fun setOtakuAddonRepository() = withContext(IO) {
        val payload = "repository.otaku"
        if (hasKodiAddon(payload)) return@withContext
        val fetcher = OkHttpClient().newBuilder().followSslRedirects(true).followRedirects(true).build()
        val website = "https://github.com/Goldenfreddy0703/repository.otaku"
        val pattern = Regex("repository.otaku-([\\d.]+).zip")
        val request = Request.Builder().url(website).addHeader("User-Agent", "mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string()
        val matchResult = content?.let { pattern.findAll(it).lastOrNull() }
        val version = matchResult?.groups?.get(1)?.value
        val address = "https://github.com/Goldenfreddy0703/repository.otaku/raw/refs/heads/master/repository.otaku-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(payload, enabled = true)
        machine.runRepeat("keycode_home")
    }

    suspend fun setOtakuAlldebridToken(payload: String) {
        val distant = "$deposit/userdata/addon_data/plugin.video.otaku/settings.xml"
        setXmlSetting(distant, "//*[@id='alldebrid.enabled']", "true")
        setXmlSetting(distant, "//*[@id='alldebrid.apikey']", payload)
    }

    suspend fun setOtakuFavourites() {
        val picture = "$deposit/addons/plugin.video.otaku/logo.png"
        setKodiFavourite(
            "[B]OTAKU[/B]\n ",
            "10025",
            "plugin://plugin.video.otaku",
            ""
        )
    }

    ///

    // TODO: Test it thoroughly


    ///

    suspend fun setPovAddon() = withContext(IO) {
        val payload = "plugin.video.pov"
        if (hasKodiAddon(payload)) return@withContext
        setPovAddonDependencies()
        setPovAddonRepository()
        val fetcher = OkHttpClient.Builder().followRedirects(true).build()
        val website = "https://codeberg.org/kodifitzwell/repo/src/branch/master/plugin.video.pov"
        val pattern = Regex("plugin.video.pov-([\\d.]+).zip")
        val request = Request.Builder().url(website).addHeader("User-Agent", "mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string()
        val version = content?.let { pattern.find(it)?.groupValues?.get(1) }
        val address = "https://codeberg.org/kodifitzwell/repo/raw/branch/master/plugin.video.pov/plugin.video.pov-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(payload, enabled = true)
        setPovFavourites()
        machine.runRepeat("keycode_home")
    }

    suspend fun setPovAddonDependencies() {
        setKodiDependency("script.module.requests")
        setKodiDependency("script.module.urllib3")
        setCocoscraperAddon()
    }

    suspend fun setPovAddonRepository() {
        val payload = "repository.kodifitzwell"
        if (hasKodiAddon(payload)) return
        val fetcher = OkHttpClient().newBuilder().followRedirects(true).build()
        val website = "https://codeberg.org/kodifitzwell/repo/src/branch/master/packages"
        val pattern = Regex("repository.kodifitzwell-([\\d.]+).zip")
        val request = Request.Builder().url(website).addHeader("User-Agent", "mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string()
        val matchResult = content?.let { pattern.findAll(it).lastOrNull() }
        val version = matchResult?.groups?.get(1)?.value
        val address = "https://codeberg.org/kodifitzwell/repo/raw/branch/master/packages/repository.kodifitzwell-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(payload, enabled = true)
        machine.runRepeat("keycode_home")
    }

    suspend fun setPovAlldebridToken(payload: String) {
        val distant = "$deposit/userdata/addon_data/plugin.video.pov/settings.xml"
        setXmlSetting(distant, "//*[@id='ad.enabled']", "true")
        setXmlSetting(distant, "//*[@id='ad.token']", payload)
        setXmlSetting(distant, "//*[@id='provider.tidebrid']", "true")
        setXmlSetting(distant, "//*[@id='tidebrid.debrid']", "1")
        setXmlSetting(distant, "//*[@id='provider.mfdebrid']", "true")
        setXmlSetting(distant, "//*[@id='mfdebrid.packs']", "true")
    }

    suspend fun setPovFavourites() {
        val picture = "$deposit/addons/plugin.video.pov/resources/media/pov.png"
        setKodiFavourite(
            "[B]POV[/B]\n ",
            "10025",
            "plugin://plugin.video.pov",
            ""
        )
    }

    ///

    suspend fun setUmbrellaAddon() = withContext(IO) {
        val payload = "plugin.video.umbrella"
        if (hasKodiAddon(payload)) return@withContext
        setUmbrellaAddonDependencies()
        setUmbrellaAddonRepository()
        val fetcher = OkHttpClient.Builder().followRedirects(true).build()
        val baseurl = "https://github.com/umbrellaplug/umbrellaplug.github.io"
        val website = "$baseurl/tree/master/nexus/zips/plugin.video.umbrella"
        val pattern = Regex("plugin.video.umbrella-([\\d.]+).zip")
        val request = Request.Builder().url(website).addHeader("User-Agent", "mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string()
        val version = content?.let { pattern.find(it)?.groupValues?.get(1) }
        val address = "$baseurl/raw/master/nexus/zips/plugin.video.umbrella/plugin.video.umbrella-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(payload, enabled = true)
        setUmbrellaFavourites()
        machine.runRepeat("keycode_home")
    }

    suspend fun setUmbrellaAddonDependencies() {
        setKodiDependency("script.module.certifi")
        setKodiDependency("script.module.chardet")
        setKodiDependency("script.module.idna")
        setKodiDependency("script.module.requests")
        setKodiDependency("script.module.urllib3")
        setCocoscraperAddon()
    }

    suspend fun setUmbrellaAddonRepository() {
        val payload = "repository.umbrella"
        if (hasKodiAddon(payload)) return
        val fetcher = OkHttpClient().newBuilder().followRedirects(true).build()
        val website = "https://umbrellaplug.github.io"
        val pattern = Regex("href=\"repository.umbrella-([\\d.]+).zip\"")
        val request = Request.Builder().url(website).addHeader("User-Agent", "mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string() ?: throw Exception("Failed to fetch content")
        val version = pattern.findAll(content).lastOrNull()?.groups?.get(1)?.value
        if (version == null) throw Exception("Umbrella version not found")
        val address = "$website/repository.umbrella-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(payload, enabled = true)
        machine.runRepeat("keycode_home")
    }

    suspend fun setUmbrellaAlldebridToken(payload: String) {
        val distant = "$deposit/userdata/addon_data/plugin.video.umbrella/settings.xml"
        setXmlSetting(distant, "//*[@id='alldebrid.enable']", "true")
        setXmlSetting(distant, "//*[@id='alldebridtoken']", payload)
    }

    suspend fun setUmbrellaExternalProvider() {
        val distant = "$deposit/userdata/addon_data/plugin.video.umbrella/settings.xml"
        setXmlSetting(distant, "//*[@id='provider.external.enabled']", "true")
        setXmlSetting(distant, "//*[@id='external_provider.name']", "cocoscrapers")
        setXmlSetting(distant, "//*[@id='external_provider.module']", "script.module.cocoscrapers")
    }

    suspend fun setUmbrellaFavourites() {
        val picture = "$deposit/addons/plugin.video.umbrella/icon.png"
        setKodiFavourite(
            "[B]UMBRELLA[/B]\n ",
            "10025",
            "plugin://plugin.video.umbrella",
            ""
        )
    }

    ///

    suspend fun setVstreamAddon() = withContext(IO) {
        val payload = "plugin.video.vstream"
        if (hasKodiAddon(payload)) return@withContext
        setVstreamAddonDependencies()
        setVstreamAddonRepository()
        val address = "https://api.github.com/repos/Kodi-vStream/venom-xbmc-addons/releases/latest"
        val archive = getFromGithub(address, Regex(".*.zip"), context)
        archive?.let { machine.runUnpack(it.path, "$deposit/addons") }

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(payload, enabled = true)
        setVstreamFavourites()
        machine.runRepeat("keycode_home")
    }

    suspend fun setVstreamAddonDependencies() {
        setKodiDependency("script.module.certifi")
        setKodiDependency("script.module.chardet")
        setKodiDependency("script.module.idna")
        setKodiDependency("script.module.pyqrcode")
        setKodiDependency("script.module.requests")
        setKodiDependency("script.module.urllib3")
    }

    suspend fun setVstreamAddonRepository() = withContext(IO) {
        val payload = "repository.vstream"
        if (hasKodiAddon(payload)) return@withContext
        val fetcher = OkHttpClient()
        val website = "https://kodi-vstream.github.io/repo"
        val request = Request.Builder().url(website).header("User-Agent", "Mozilla/5.0").build()
        val content = fetcher.newCall(request).execute().body?.string()
        val pattern = Regex("href=\"repository.vstream-([\\d.]+).zip\"")
        val matchResult = content?.let { pattern.findAll(it).lastOrNull() }
        val version = matchResult?.groups?.get(1)?.value
        val address = "$website/repository.vstream-$version.zip"
        val archive = getFromAddress(address, context)
        machine.runUnpack(archive!!.path, "$deposit/addons")

        runRpc(mapOf("jsonrpc" to "2.0", "method" to "Application.Quit", "params" to emptyMap<String, Any>(), "id" to 1))
        delay(5000)
        machine.runFinish(pkgname)
        setKodiWebserver(enabled = true, secured = false)
        setKodiAddonEnabled(payload, enabled = true)
        machine.runRepeat("keycode_home")
    }

    suspend fun setVstreamAlldebridToken(payload: String) {
        val distant = "$deposit/userdata/addon_data/plugin.video.vstream/settings.xml"
        setXmlSetting(distant, "//*[@id='hoster_alldebrid_premium']", "true")
        setXmlSetting(distant, "//*[@id='hoster_alldebrid_token']", payload)
    }

    suspend fun setVstreamEnableActivateSubtitles(enabled: Boolean = false) {
        val distant = "$deposit/userdata/addon_data/plugin.video.vstream/settings.xml"
        setXmlSetting(distant, "//*[@id='srt-view']", if (enabled) "true" else "false", adjunct = false)
    }

    suspend fun setVstreamFavourites() {
        setKodiFavourite(
            "[B]HISTORIQUE[/B]\n ",
            "10025",
            "plugin://plugin.video.vstream/?function=getWatched&sFav=getWatched&site=cWatched&title=Toutes les catégories",
            ""
        )
        setKodiFavourite(
            "[B]FILMS[/B]\n ",
            "10025",
            "plugin://plugin.video.vstream/?function=showMenuFilms&sFav=showMenuFilms&site=pastebin&siteUrl=https://pastebin.com/raw/&numPage=1&sMedia=film&title=Films",
            ""
        )
        setKodiFavourite(
            "[B]SÉRIES[/B]\n ",
            "10025",
            "plugin://plugin.video.vstream/?function=showMenuTvShows&sFav=showMenuTvShows&site=pastebin&title=Séries",
            ""
        )
        setKodiFavourite(
            "[B]ANIMÉS[/B]\n ",
            "10025",
            "plugin://plugin.video.vstream/?function=showMenuMangas&sFav=showMenuMangas&site=pastebin&title=Animes",
            ""
        )
        setKodiFavourite(
            "[B]MARQUE-PAGES[/B]\n ",
            "10025",
            "plugin://plugin.video.vstream/?function=getBookmarks&sFav=getBookmarks&site=cFav&title=Mes marque-pages",
            ""
        )
        setKodiFavourite(
            "[B]CONCERTS[/B]\n ",
            "10025",
            "plugin://plugin.video.vstream/?function=showMedia&pasteID=4&sFav=showMedia&site=pastebin&title=Parcourir le contenu",
            ""
        )
        setKodiFavourite(
            "[B]DESSINS ANIMÉS[/B]\n ",
            "10025",
            "plugin://plugin.video.vstream/?function=showMedia&pasteID=5&sFav=showMedia&site=pastebin&title=Parcourir le contenu",
            ""
        )
        setKodiFavourite(
            "[B]DOCUMENTAIRES[/B]\n ",
            "10025",
            "plugin://plugin.video.vstream/?function=showMedia&pasteID=3&sFav=showMedia&site=pastebin&title=Parcourir le contenu",
            ""
        )
    }

    suspend fun setVstreamPastebinCodes() = withContext(IO) {
        val distant = "$deposit/userdata/addon_data/plugin.video.vstream/settings.xml"
        val fetched = machine.runImport(distant) ?: return@withContext
        val content = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(fetched))
        val factors = mapOf(
            "pastebin_label_1" to "Films",
            "pastebin_id_1" to "1eda79b1",
            "pastebin_label_2" to "Séries",
            "pastebin_id_2" to "6730992c",
            "pastebin_label_3" to "Documentaires",
            "pastebin_id_3" to "00c0fdec",
            "pastebin_label_4" to "Concerts",
            "pastebin_id_4" to "017cea8a",
            "pastebin_label_5" to "Cartoons",
            "pastebin_id_5" to "fff5fe57",
            "pastebin_label_6" to "Animés",
            "pastebin_id_6" to "e227f398",
            // "pastebin_label_7" to "Québec",
            // "pastebin_id_7" to "5da625b7"
        )
        factors.forEach { (key, value) ->
            val factory = XPathFactory.newInstance().newXPath()
            val results = factory.compile("//*[@id='$key']").evaluate(content, NODESET) as NodeList
            if (results.length == 0) {
                val element = content.documentElement
                val setting = content.createElement("setting")
                setting.setAttribute("id", key)
                setting.textContent = value
                element.appendChild(setting)
            } else {
                val setting = results.item(0) as Element
                setting.textContent = value
            }
        }
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(content), StreamResult(File(fetched)))
        machine.runExport(fetched, distant)
    }

    suspend fun setVstreamPastebinUrl(payload: String = "https://paste.lesalkodiques.com/raw/") {
        val distant = "$deposit/userdata/addon_data/plugin.video.vstream/settings.xml"
        setXmlSetting(distant, "//*[@id='pastebin_url']", payload)
    }

    suspend fun setVstreamTmdbBackdropQuality(payload: String) {
        val distant = "$deposit/userdata/addon_data/plugin.video.vstream/settings.xml"
        setXmlSetting(distant, "//*[@id='backdrop_tmdb']", payload)
    }

    suspend fun setVstreamTmdbPosterQuality(payload: String) {
        val distant = "$deposit/userdata/addon_data/plugin.video.vstream/settings.xml"
        setXmlSetting(distant, "//*[@id='poster_tmdb']", payload)
    }

}