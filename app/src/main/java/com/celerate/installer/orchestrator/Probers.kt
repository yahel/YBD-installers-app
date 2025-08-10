package com.celerate.installer.orchestrator

import android.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import javax.net.SocketFactory

sealed class ProbeResult {
    data class Up(val latencyMs: Long) : ProbeResult()
    data class Down(val reason: String) : ProbeResult()
}

suspend fun probeHttp(url: HttpUrl, client: OkHttpClient): ProbeResult = withContext(Dispatchers.IO) {
    val start = System.nanoTime()
    val req = Request.Builder().url(url).head().build()
    return@withContext try {
        client.newCall(req).execute().use { resp ->
            val tookMs = (System.nanoTime() - start) / 1_000_000
            if (resp.isSuccessful || resp.code in 200..499) {
                ProbeResult.Up(tookMs)
            } else ProbeResult.Down("HTTP ${'$'}{resp.code}")
        }
    } catch (e: Exception) {
        ProbeResult.Down(e.message ?: "error")
    }
}

suspend fun probeTcp(host: String, port: Int, network: Network): ProbeResult = withContext(Dispatchers.IO) {
    val sf: SocketFactory = network.socketFactory
    val start = System.nanoTime()
    return@withContext try {
        sf.createSocket().use { s -> s.connect(InetSocketAddress(host, port), 1500) }
        val tookMs = (System.nanoTime() - start) / 1_000_000
        ProbeResult.Up(tookMs)
    } catch (e: Exception) {
        ProbeResult.Down(e.message ?: "connect error")
    }
}
