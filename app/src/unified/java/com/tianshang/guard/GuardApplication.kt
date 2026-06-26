package com.tianshang.guard

import android.app.Application
import com.tianshang.guard.core.ml.MlEngine
import com.tianshang.guard.core.ml.ModelType
import com.tianshang.guard.core.retrieval.KnowledgeBase
import com.tianshang.guard.data.repository.RuleRepository
import com.tianshang.guard.di.appModule
import org.json.JSONArray
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.get
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.runBlocking

class GuardApplication : Application() {

    private val modelsLoading = AtomicBoolean(false)

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

            // URL model: load immediately (VPN needs it)
            val urlModelFile = extractModelToCache("url_phishing.onnx")
            mlEngine.loadModel(urlModelFile, ModelType.URL)

            android.util.Log.i("GuardApp", "Unified: loaded URL model, loading others in background")

            // Load BM25 knowledge base
            val knowledgeBase: KnowledgeBase = get(KnowledgeBase::class.java)
            knowledgeBase.loadAsync()

            // Load remaining models in background thread
            Thread({
                if (modelsLoading.compareAndSet(false, true)) {
                    try {
                        // Chinese + SMS models
                        val chineseModelFile = extractModelToCache("chinese_phishing.onnx")
                        mlEngine.loadModel(chineseModelFile, ModelType.CHINESE)

                        val smsModelFile = extractModelToCache("sms_phishing.onnx")
                        mlEngine.loadModel(smsModelFile, ModelType.SMS)

                        // English model
                        val englishModelFile = extractModelToCache("english_phishing.onnx")
                        mlEngine.loadModel(englishModelFile, ModelType.ENGLISH)

                        android.util.Log.i("GuardApp", "Unified: loaded CHINESE, SMS, ENGLISH models")
                    } catch (e: Exception) {
                        android.util.Log.e("GuardApp", "Failed to load models in background", e)
                    } finally {
                        modelsLoading.set(false)
                    }
                }
            }, "ModelLoader").start()
        } catch (e: Exception) {
            android.util.Log.e("GuardApp", "Failed to load URL model", e)
        }
    }

    private fun extractModelToCache(modelName: String): String {
        val modelFile = File(cacheDir, modelName)
        if (!modelFile.exists()) {
            assets.open("model/$modelName").use { input ->
                modelFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return modelFile.absolutePath
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
