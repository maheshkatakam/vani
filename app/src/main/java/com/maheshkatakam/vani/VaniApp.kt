package com.maheshkatakam.vani

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.maheshkatakam.vani.ai.ModelResolver
import com.maheshkatakam.vani.ai.VaniEngine
import com.maheshkatakam.vani.core.ApiKeyStore
import com.maheshkatakam.vani.core.HistoryStore
import com.maheshkatakam.vani.core.SettingsRepo

class VaniApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Vani.init(this)
    }
}

/** App-wide singletons. */
object Vani {
    lateinit var app: Application
        private set
    lateinit var settings: SettingsRepo
        private set
    lateinit var keys: ApiKeyStore
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var models: ModelResolver
        private set
    lateinit var engine: VaniEngine
        private set

    private val main = Handler(Looper.getMainLooper())

    fun init(application: Application) {
        app = application
        settings = SettingsRepo(application)
        keys = ApiKeyStore(application)
        history = HistoryStore(application)
        models = ModelResolver(application)
        engine = VaniEngine(keys, models)
    }

    fun toast(message: String, long: Boolean = true) {
        main.post { Toast.makeText(app, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() }
    }

    fun copy(text: String) {
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Vani", text))
    }

    fun appLabel(pkg: String): String = runCatching {
        val pm = app.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
