package dev.antigravity.classevivaexpressive.cie

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.Semaphore
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.Locale

/**
 * An HTTP proxy for HTTPS CONNECT only. It sees a bounded CONNECT header and then copies opaque
 * bytes; TLS still terminates in WebView and the provider, without a local CA or TLS interception.
 * Every HTTP/HTTPS redirect needing another destination must pass the authority check before DNS or
 * an upstream socket connection. No URLs, headers or tunnel bytes are logged or retained.
 * This fences WebView HTTP/HTTPS, not all browser protocols. Authorized provider content is trusted;
 * for example, data-only WebRTC/ICE can use UDP independently of an HTTP proxy.
 */
internal class CieConnectProxy(
  allowedHosts: Set<String> = CieNavigationPolicy.httpsResourceHosts,
  private val connect: (Socket, String, Int) -> Unit = { socket, host, port ->
    socket.connect(InetSocketAddress(host, port), ConnectTimeoutMillis)
  },
  maxTunnels: Int = 16,
) : Closeable {
  private val hosts = allowedHosts.toSet()
  private val closed = AtomicBoolean()
  private val socketLock = Any()
  private val sockets = mutableSetOf<Socket>()
  private val permits = Semaphore(maxTunnels)
  private val threads = ThreadFactory { task -> Thread(task, "cie-network-fence").apply { isDaemon = true } }
  private val handlers = boundedPool(maxTunnels)
  private val uplinks = boundedPool(maxTunnels)
  private val listener = ServerSocket().apply {
    reuseAddress = false
    bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), maxTunnels)
  }
  val port: Int = listener.localPort
  private val acceptor = threads.newThread(::acceptConnections).apply { start() }

  private fun boundedPool(size: Int) = ThreadPoolExecutor(
    size, size, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(size), threads,
  )

  private fun register(socket: Socket): Boolean = synchronized(socketLock) {
    if (closed.get()) { runCatching { socket.close() }; false } else { sockets += socket; true }
  }

  private fun forget(socket: Socket) {
    runCatching { socket.close() }
    synchronized(socketLock) { sockets -= socket }
  }

  private fun acceptConnections() {
    while (!closed.get()) {
      val client = try { listener.accept() } catch (_: Exception) { break }
      if (!register(client)) break
      if (!permits.tryAcquire()) { forget(client); continue }
      try {
        handlers.execute {
          try { tunnel(client) } finally { forget(client); permits.release() }
        }
      } catch (_: RejectedExecutionException) { forget(client); permits.release() }
    }
  }

  private fun tunnel(client: Socket) {
    var upstream: Socket? = null
    try {
      client.soTimeout = HeaderTimeoutMillis
      val host = allowedConnectHost(readHeader(client))
      if (host == null) {
        client.getOutputStream().write(Denied)
        return
      }
      val remote = Socket()
      upstream = remote
      if (!register(remote)) return
      connect(remote, host, 443)
      if (closed.get()) return
      remote.soTimeout = TunnelIdleMillis
      client.soTimeout = TunnelIdleMillis
      client.getOutputStream().write(Connected)
      // At most one uplink and one downlink per admitted tunnel. No executor queue is unbounded.
      uplinks.execute {
        try { relay(client.getInputStream(), remote) }
        catch (_: Exception) { /* Closing a tunnel is a normal terminal event, never a log. */ }
        finally { forget(client); forget(remote) }
      }
      relay(remote.getInputStream(), client)
    } catch (_: Exception) {
      // Errors are deliberately opaque: even a CONNECT header may identify a sensitive flow.
    } finally {
      upstream?.let(::forget)
    }
  }

  private fun relay(source: InputStream, destination: Socket) {
    val buffer = ByteArray(16_384)
    try {
      val output = destination.getOutputStream()
      while (!closed.get()) {
        val count = source.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
      }
    } finally { buffer.fill(0) }
  }

  /** No buffered read: bytes immediately after CRLFCRLF belong to TLS and must remain untouched. */
  private fun readHeader(client: Socket): String? {
    val input = client.getInputStream()
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(HeaderTimeoutMillis.toLong())
    val bytes = ByteArrayOutputStream()
    var ending = 0
    repeat(MaxHeaderBytes) {
      val remaining = deadline - System.nanoTime()
      if (remaining <= 0) return null
      client.soTimeout = TimeUnit.NANOSECONDS.toMillis(remaining).coerceIn(1L, HeaderTimeoutMillis.toLong()).toInt()
      val next = input.read()
      if (next < 0 || next !in 0x20..0x7e && next != 13 && next != 10) return null
      bytes.write(next)
      ending = when {
        ending == 0 && next == 13 -> 1
        ending == 1 && next == 10 -> 2
        ending == 2 && next == 13 -> 3
        ending == 3 && next == 10 -> return bytes.toString(StandardCharsets.US_ASCII.name())
        next == 13 -> 1
        else -> 0
      }
    }
    return null
  }

  private fun allowedConnectHost(header: String?): String? {
    val lines = header?.split("\r\n") ?: return null
    val parts = lines.first().split(' ')
    if (parts.size != 3 || parts[0] != "CONNECT" || parts[2] !in setOf("HTTP/1.0", "HTTP/1.1")) return null
    val authority = parts[1]
    val host = authority.removeSuffix(":443")
    if (authority != "$host:443" || host !in hosts) return null
    val headers = lines.drop(1).dropLast(2)
    if (headers.any { it.isEmpty() || ':' !in it }) return null
    val names = headers.map { it.substringBefore(':').lowercase(Locale.ROOT) }
    if (names.any { it == "content-length" || it == "transfer-encoding" } || names.count { it == "host" } > 1) return null
    val statedHost = headers.firstOrNull { it.substringBefore(':').equals("host", ignoreCase = true) }?.substringAfter(':')?.trim()
    return host.takeIf { statedHost == null || statedHost == authority }
  }

  override fun close() {
    if (!closed.compareAndSet(false, true)) return
    runCatching { listener.close() }
    synchronized(socketLock) { sockets.toList().forEach { runCatching { it.close() } }; sockets.clear() }
    handlers.shutdownNow()
    uplinks.shutdownNow()
    acceptor.interrupt()
  }

  companion object {
    private const val ConnectTimeoutMillis = 5_000
    private const val HeaderTimeoutMillis = 5_000
    private const val TunnelIdleMillis = 60_000
    private const val MaxHeaderBytes = 8_192
    private val Connected = "HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
    private val Denied = "HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.US_ASCII)
  }
}
