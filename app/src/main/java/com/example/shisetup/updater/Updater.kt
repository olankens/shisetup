import android.content.Context
import com.example.shisetup.adbdroid.Shield
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

abstract class Updater(val machine: Shield, val context: Context) {
    init {
        CoroutineScope(IO).launch {
            machine.runAttach()
        }
    }

    abstract val pkgname: String
    abstract val heading: String

    abstract suspend fun runGather(): File?

    suspend fun getRecent(): Boolean = withContext(IO) {
        val command = "dumpsys package '$pkgname' | grep lastUpdateTime | sed s/.*[=]\\s*// | head -1"
        val scraped = machine.runInvoke(command).output.trim()
        if (scraped.isEmpty()) return@withContext false
        val results = LocalDateTime.parse(scraped, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val updated = ChronoUnit.DAYS.between(results, LocalDateTime.now()) <= 5
        return@withContext updated
    }

    suspend fun getSeated(): Boolean = withContext(IO) {
        return@withContext machine.getSeated(pkgname)
    }

    open suspend fun runRemove() = withContext(IO) {
        if (getSeated()) {
            machine.runVanish(pkgname)
        }
    }

    open suspend fun runUpdate() = withContext(IO) {
        if (!getRecent()) {
            val fetched = runGather()
            fetched?.let {
                machine.runUpdate(it.path)
            }
        }
    }

    suspend fun setPip(enabled: Boolean = true) = withContext(IO) { // TODO: Rename method
        machine.setPictureInPicture(heading, enabled)
    }

}
