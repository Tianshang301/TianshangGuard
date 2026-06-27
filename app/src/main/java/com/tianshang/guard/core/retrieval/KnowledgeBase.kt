package com.tianshang.guard.core.retrieval

import android.content.Context
import com.tianshang.guard.core.util.SecureLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class KnowledgeBase(private val context: Context) {

    private val bm25Engine = Bm25Engine()
    private val ioScope = CoroutineScope(Dispatchers.IO)

    fun loadAsync() {
        ioScope.launch {
            try {
                // L-6: Use .use{} to ensure InputStream is closed on exception
                context.assets.open("knowledge_base/index.bin").use { inputStream ->
                    val success = bm25Engine.loadFromAssets(inputStream)
                    if (success) {
                        SecureLog.i("KnowledgeBase", "Loaded BM25 index: ${bm25Engine.getDocCount()} documents")
                    } else {
                        SecureLog.e("KnowledgeBase", "Failed to load BM25 index")
                    }
                }
            } catch (e: Exception) {
                SecureLog.e("KnowledgeBase", "Failed to open index file", e)
            }
        }
    }

    /**
     * Add a feedback document to the dynamic index.
     * This allows user feedback to influence future BM25 queries.
     */
    fun addFeedback(text: String, isPhishing: Boolean) {
        bm25Engine.addFeedbackDocument(text, isPhishing)
        SecureLog.i("KnowledgeBase", "Added feedback document, total feedback docs: ${bm25Engine.getFeedbackDocCount()}")
    }

    /**
     * Clear all feedback documents from the dynamic index.
     */
    fun clearFeedback() {
        bm25Engine.clearFeedbackIndex()
        SecureLog.i("KnowledgeBase", "Cleared feedback index")
    }

    fun query(text: String, topK: Int = 10): Bm25Engine.RetrievalResult {
        return bm25Engine.query(text, topK)
    }

    fun isReady(): Boolean = bm25Engine.isReady()

    fun getDocCount(): Int = bm25Engine.getDocCount()
}
