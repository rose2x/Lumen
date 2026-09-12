package com.lumen.keyboard

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

data class GifResult(val previewUrl: String, val fullUrl: String)

/**
 * Thin wrapper around the free Tenor v2 search API. Requires the user to
 * supply their own API key in Settings (see README) -- no key is bundled
 * with this project. Networking runs on a background thread; callbacks
 * are always delivered on the main thread.
 */
object TenorApiClient {

    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun search(apiKey: String, query: String, onResult: (List<GifResult>) -> Unit, onError: (String) -> Unit) {
        if (apiKey.isBlank()) {
            onError("No Tenor API key set. Add a free one in Settings.")
            return
        }
        executor.execute {
            try {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val urlStr = "https://tenor.googleapis.com/v2/search" +
                    "?q=$encoded&key=$apiKey&client_key=lumen_keyboard" +
                    "&limit=24&media_filter=tinygif,gif&contentfilter=medium"
                val connection = URL(urlStr).openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.requestMethod = "GET"

                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    postError(onError, "Tenor request failed (HTTP $code). Check your API key.")
                    connection.disconnect()
                    return@execute
                }

                val body = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                val parsed = mutableListOf<GifResult>()
                if (results != null) {
                    for (i in 0 until results.length()) {
                        val item = results.getJSONObject(i)
                        val formats = item.optJSONObject("media_formats") ?: continue
                        val tiny = formats.optJSONObject("tinygif")?.optString("url")
                        val full = formats.optJSONObject("gif")?.optString("url")
                        if (!tiny.isNullOrBlank() && !full.isNullOrBlank()) {
                            parsed.add(GifResult(previewUrl = tiny, fullUrl = full))
                        }
                    }
                }
                postResult(onResult, parsed)
            } catch (e: Exception) {
                postError(onError, "Couldn't reach Tenor: ${e.message ?: "network error"}")
            }
        }
    }

    private fun postResult(onResult: (List<GifResult>) -> Unit, results: List<GifResult>) {
        mainHandler.post { onResult(results) }
    }

    private fun postError(onError: (String) -> Unit, message: String) {
        mainHandler.post { onError(message) }
    }
}
