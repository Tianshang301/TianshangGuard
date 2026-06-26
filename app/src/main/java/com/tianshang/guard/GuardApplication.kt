package com.tianshang.guard

import android.app.Application
import com.tianshang.guard.core.ml.MlEngine
import com.tianshang.guard.core.ml.ModelType
import com.tianshang.guard.data.local.database.DomainCategory
import com.tianshang.guard.data.local.database.DomainEntity
import com.tianshang.guard.data.repository.RuleRepository
import com.tianshang.guard.di.appModule
import org.json.JSONArray
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.get
import java.io.File
import kotlinx.coroutines.runBlocking

class GuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        android.util.Log.i("GuardApp", "onCreate start")
        startKoin {
            androidContext(this@GuardApplication)
            modules(appModule)
        }
        loadOnnxModel()
        loadBuiltinRules()
        android.util.Log.i("GuardApp", "onCreate done")
    }

    private fun loadOnnxModel() {
        try {
            val mlEngine: MlEngine = get(MlEngine::class.java)

            val urlModelFile = File(cacheDir, "url_phishing.onnx")
            if (!urlModelFile.exists()) {
                assets.open("model/url_phishing.onnx").use { input ->
                    urlModelFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            mlEngine.loadModel(urlModelFile.absolutePath, ModelType.URL)

            val chineseModelFile = File(cacheDir, "chinese_phishing.onnx")
            if (!chineseModelFile.exists()) {
                assets.open("model/chinese_phishing.onnx").use { input ->
                    chineseModelFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            mlEngine.loadModel(chineseModelFile.absolutePath, ModelType.CHINESE)

            val englishModelFile = File(cacheDir, "english_phishing.onnx")
            if (!englishModelFile.exists()) {
                assets.open("model/english_phishing.onnx").use { input ->
                    englishModelFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            mlEngine.loadModel(englishModelFile.absolutePath, ModelType.ENGLISH)

            val japaneseModelFile = File(cacheDir, "japanese_phishing.onnx")
            if (!japaneseModelFile.exists()) {
                assets.open("model/japanese_phishing.onnx").use { input ->
                    japaneseModelFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            mlEngine.loadModel(japaneseModelFile.absolutePath, ModelType.JAPANESE)

            val smsModelFile = File(cacheDir, "sms_phishing.onnx")
            if (!smsModelFile.exists()) {
                assets.open("model/sms_phishing.onnx").use { input ->
                    smsModelFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            mlEngine.loadModel(smsModelFile.absolutePath, ModelType.SMS)
        } catch (e: Exception) {
            android.util.Log.e("GuardApp", "Failed to load ONNX model", e)
        }
    }

    private fun loadBuiltinRules() {
        try {
            val ruleRepository: RuleRepository = get(RuleRepository::class.java)
            runBlocking {
                val existingCount = ruleRepository.getKnownDomains().size
                if (existingCount > 0) return@runBlocking

                val blacklistJson = assets.open("rules/blacklist.json").bufferedReader().readText()
                val whitelistJson = assets.open("rules/whitelist.json").bufferedReader().readText()

                val blacklist = JSONArray(blacklistJson)
                for (i in 0 until blacklist.length()) {
                    val entry = blacklist.getJSONObject(i)
                    ruleRepository.addToBlacklist(entry.getString("domain"))
                }

                val whitelist = JSONArray(whitelistJson)
                for (i in 0 until whitelist.length()) {
                    val entry = whitelist.getJSONObject(i)
                    ruleRepository.addToWhitelist(entry.getString("domain"))
                }

                android.util.Log.i("GuardApp", "Loaded ${blacklist.length()} blacklist + ${whitelist.length()} whitelist domains")
            }
        } catch (e: Exception) {
            android.util.Log.e("GuardApp", "Failed to load builtin rules", e)
        }
    }
}
