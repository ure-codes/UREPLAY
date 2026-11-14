package com.example.network

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

data class HotspotServerState(
    val isRunning: Boolean = false,
    val streamUrl: String = "",
    val sharedTitle: String = "",
    val sharedUri: String = "",
    val activeClients: Int = 0,
    val error: String? = null
)

class HotspotStreamServer(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private var currentUri: Uri? = null
    private var currentMimeType: String = "video/mp4"

    private val _state = MutableStateFlow(HotspotServerState())
    val state: StateFlow<HotspotServerState> = _state.asStateFlow()

    fun startSharing(uri: Uri, title: String, mimeType: String = "video/mp4") {
        stopSharing()
        currentUri = uri
        currentMimeType = mimeType.ifBlank { "video/mp4" }

        acceptJob = scope.launch {
            try {
                val socket = ServerSocket(0)
                serverSocket = socket
                val port = socket.localPort
                val hostIp = findLocalIpAddress() ?: "127.0.0.1"
                val url = "http://$hostIp:$port/stream"

                _state.value = HotspotServerState(
                    isRunning = true,
                    streamUrl = url,
                    sharedTitle = title,
                    sharedUri = uri.toString(),
                    error = null
                )

                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch { handleClient(client) }
                }
            } catch (t: Throwable) {
                _state.value = HotspotServerState(
                    isRunning = false,
                    error = t.message ?: "Unable to start Hotspot stream server"
                )
            }
        }
    }

    fun stopSharing() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        currentUri = null
        _state.value = HotspotServerState()
    }

    private fun handleClient(client: Socket) {
        _state.value = _state.value.copy(activeClients = _state.value.activeClients + 1)
        try {
            client.use { socket ->
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val requestLine = reader.readLine() ?: return
                var rangeHeader: String? = null
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isBlank()) break
                    if (line.startsWith("Range:", ignoreCase = true)) {
                        rangeHeader = line.substringAfter(":").trim()
                    }
                }

                val uri = currentUri ?: return
                val totalSize = queryFileSize(uri)
                val out = BufferedOutputStream(socket.getOutputStream())

                if (requestLine.startsWith("HEAD")) {
                    val headers = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: $currentMimeType\r\n")
                        append("Accept-Ranges: bytes\r\n")
                        if (totalSize > 0) append("Content-Length: $totalSize\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    out.write(headers.toByteArray())
                    out.flush()
                    return
                }

                val (startByte, endByte, isPartial) = parseRange(rangeHeader, totalSize)
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    out.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".toByteArray())
                    out.flush()
                    return
                }

                BufferedInputStream(inputStream, 64 * 1024).use { stream ->
                    if (startByte > 0) {
                        var skipped = 0L
                        while (skipped < startByte) {
                            val s = stream.skip(startByte - skipped)
                            if (s <= 0) {
                                if (stream.read() == -1) break
                                skipped++
                            } else {
                                skipped += s
                            }
                        }
                    }

                    val contentLength = if (totalSize > 0 && endByte >= startByte) {
                        endByte - startByte + 1
                    } else {
                        -1L
                    }

                    val headers = buildString {
                        if (isPartial && totalSize > 0) {
                            append("HTTP/1.1 206 Partial Content\r\n")
                            append("Content-Range: bytes $startByte-$endByte/$totalSize\r\n")
                        } else {
                            append("HTTP/1.1 200 OK\r\n")
                        }
                        append("Content-Type: $currentMimeType\r\n")
                        append("Accept-Ranges: bytes\r\n")
                        if (contentLength >= 0) append("Content-Length: $contentLength\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                    out.write(headers.toByteArray())

                    val buffer = ByteArray(64 * 1024)
                    var remaining = if (contentLength >= 0) contentLength else Long.MAX_VALUE
                    while (remaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = stream.read(buffer, 0, toRead)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        if (contentLength >= 0) remaining -= read
                    }
                    out.flush()
                }
            }
        } catch (_: Throwable) {
            // Client disconnected
        } finally {
            _state.value = _state.value.copy(
                activeClients = (_state.value.activeClients - 1).coerceAtLeast(0)
            )
        }
    }

    private fun queryFileSize(uri: Uri): Long {
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (afd.length >= 0) return@runCatching afd.length
            }
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) return@runCatching c.getLong(idx)
                }
            }
            -1L
        }.getOrDefault(-1L)
    }

    private fun parseRange(rangeHeader: String?, totalSize: Long): Triple<Long, Long, Boolean> {
        if (rangeHeader.isNullOrBlank() || !rangeHeader.startsWith("bytes=") || totalSize <= 0) {
            return Triple(0L, (totalSize - 1).coerceAtLeast(0L), false)
        }
        val spec = rangeHeader.removePrefix("bytes=").substringBefore(",").trim()
        val parts = spec.split("-")
        val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val end = parts.getOrNull(1)?.toLongOrNull()?.coerceAtMost(totalSize - 1) ?: (totalSize - 1)
        return Triple(start.coerceIn(0L, totalSize - 1), end.coerceAtLeast(start), true)
    }

    private fun findLocalIpAddress(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
            val candidates = mutableListOf<String>()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses.toList()) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (iface.name.contains("wlan", ignoreCase = true) ||
                            iface.name.contains("ap", ignoreCase = true) ||
                            iface.name.contains("swlan", ignoreCase = true)
                        ) {
                            return ip
                        }
                        candidates.add(ip)
                    }
                }
            }
            candidates.firstOrNull()
        }.getOrNull()
    }
}
