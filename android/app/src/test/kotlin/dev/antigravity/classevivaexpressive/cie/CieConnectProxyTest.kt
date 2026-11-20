package dev.antigravity.classevivaexpressive.cie

import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class CieConnectProxyTest {
  @Test fun deniedHostPortHttpAndMalformedHeadersNeverOpenAnUpstreamConnection() {
    val connections = AtomicInteger()
    CieConnectProxy(connect = { _, _, _ -> connections.incrementAndGet(); error("Must not connect") }).use { proxy ->
      val headers = listOf(
        "CONNECT evil.example:443 HTTP/1.1\r\n\r\n",
        "CONNECT web.spaggiari.eu.evil.example:443 HTTP/1.1\r\n\r\n",
        "CONNECT www.google.com:80 HTTP/1.1\r\n\r\n",
        "CONNECT fonts.gstatic.com:444 HTTP/1.1\r\n\r\n",
        "CONNECT web.spaggiari.eu:0443 HTTP/1.1\r\n\r\n",
        "CONNECT web.spaggiari.eu:70000 HTTP/1.1\r\n\r\n",
        "CONNECT user@web.spaggiari.eu:443 HTTP/1.1\r\n\r\n",
        "CONNECT https://web.spaggiari.eu:443 HTTP/1.1\r\n\r\n",
        "CONNECT 127.0.0.1:443 HTTP/1.1\r\n\r\n",
        "CONNECT [::1]:443 HTTP/1.1\r\n\r\n",
        "GET http://www.google.com/ HTTP/1.1\r\nHost: www.google.com\r\n\r\n",
        "POST http://evil.example/ HTTP/1.1\r\nContent-Length: 7\r\n\r\nmarker!",
        "GET http://web.spaggiari.eu.evil.example/ HTTP/1.1\r\n\r\n",
        "GET http://user@eid.istruzione.it/ HTTP/1.1\r\n\r\n",
        "GET https://eid.istruzione.it/ HTTP/1.1\r\n\r\n",
        "PUT http://eid.istruzione.it/ HTTP/1.1\r\n\r\n",
        "GET http://eid.istruzione.it/ HTTP/1.1\r\nHost: evil.example\r\n\r\n",
        "POST http://eid.istruzione.it/ HTTP/1.1\r\nContent-Length: 1\r\nTransfer-Encoding: chunked\r\n\r\n",
        "CONNECT web.spaggiari.eu:443 HTTP/1.1\r\nHost: evil.example:443\r\n\r\n",
        "CONNECT web.spaggiari.eu:443 HTTP/1.1\r\nHost: web.spaggiari.eu:443\r\nHost: web.spaggiari.eu:443\r\n\r\n",
        "CONNECT web.spaggiari.eu:443 HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n",
        "CONNECT web.spaggiari.eu:443 HTTP/1.1\r\nContent-Length: 0\r\n\r\n",
      )
      headers.forEach { header ->
        client(proxy).use { connection ->
          connection.getOutputStream().write(header.toByteArray(US_ASCII))
          assertTrue(readHeader(connection.getInputStream()).startsWith("HTTP/1.1 403"))
        }
      }
      assertEquals(0, connections.get())
    }
  }

  @Test fun everyResourceHostIsPermittedOnlyOn443AndTunnelBytesRemainOpaque() {
    for (host in CieNavigationPolicy.httpsResourceHosts) {
      ServerSocket(0).use { upstream ->
        val calls = AtomicInteger()
        val input = byteArrayOf(0, -1, 13, 10, 42, 0, 7)
        val output = byteArrayOf(7, 0, 42, 10, 13, -1, 0)
        val done = CountDownLatch(1)
        val received = ByteArray(input.size)
        val server = thread(isDaemon = true) {
          upstream.accept().use { remote ->
            remote.soTimeout = 2_000
            readExactly(remote.getInputStream(), received)
            remote.getOutputStream().write(output)
            done.countDown()
          }
        }
        CieConnectProxy(connect = { socket, requestedHost, port ->
          assertEquals(host, requestedHost)
          assertEquals(443, port)
          calls.incrementAndGet()
          socket.connect(InetSocketAddress("127.0.0.1", upstream.localPort), 2_000)
        }).use { proxy ->
          client(proxy).use { connection ->
            // TLS may immediately follow the header; the proxy must not consume any of it.
            connection.getOutputStream().write("CONNECT $host:443 HTTP/1.1\r\nHost: $host:443\r\n\r\n".toByteArray(US_ASCII) + input)
            assertTrue(readHeader(connection.getInputStream()).startsWith("HTTP/1.1 200"))
            val relayed = ByteArray(output.size)
            readExactly(connection.getInputStream(), relayed)
            assertArrayEquals(output, relayed)
            assertTrue(done.await(2, TimeUnit.SECONDS))
            assertArrayEquals(input, received)
            assertEquals(1, calls.get())
          }
        }
        server.join(2_000)
      }
    }
  }

  @Test fun federationHostsMayUseAnotherHttpsPort() {
    val ports = mutableListOf<Int>()
    ServerSocket(0).use { upstream ->
      thread(isDaemon = true) { runCatching { upstream.accept().use { it.getInputStream().read() } } }
      CieConnectProxy(connect = { socket, host, port ->
        assertEquals("eid.istruzione.it", host)
        ports += port
        socket.connect(InetSocketAddress("127.0.0.1", upstream.localPort), 2_000)
      }).use { proxy ->
        client(proxy).use { connection ->
          connection.getOutputStream().write("CONNECT eid.istruzione.it:8443 HTTP/1.1\r\n\r\n".toByteArray(US_ASCII))
          assertTrue(readHeader(connection.getInputStream()).startsWith("HTTP/1.1 200"))
        }
      }
    }
    assertEquals(listOf(8443), ports)
  }

  @Test fun plainHttpToAFederationHostIsForwardedInOriginFormWithItsBody() {
    ServerSocket(0).use { upstream ->
      val received = CountDownLatch(1)
      var request = ""
      thread(isDaemon = true) {
        upstream.accept().use { remote ->
          remote.soTimeout = 2_000
          val header = readHeader(remote.getInputStream())
          val body = ByteArray(5).also { readExactly(remote.getInputStream(), it) }
          request = header + String(body, US_ASCII)
          received.countDown()
          remote.getOutputStream().write("HTTP/1.1 302 Found\r\nLocation: https://web.spaggiari.eu/x\r\nContent-Length: 0\r\n\r\n".toByteArray(US_ASCII))
        }
      }
      var target: Pair<String, Int>? = null
      CieConnectProxy(connect = { socket, host, port ->
        target = host to port
        socket.connect(InetSocketAddress("127.0.0.1", upstream.localPort), 2_000)
      }).use { proxy ->
        client(proxy).use { connection ->
          connection.getOutputStream().write(
            "POST http://eid.istruzione.it/acs?x=1 HTTP/1.1\r\nHost: eid.istruzione.it\r\nProxy-Connection: keep-alive\r\nContent-Length: 5\r\n\r\nA=b&c".toByteArray(US_ASCII),
          )
          assertTrue(readHeader(connection.getInputStream()).startsWith("HTTP/1.1 302"))
        }
        assertTrue(received.await(2, TimeUnit.SECONDS))
      }
      assertEquals("eid.istruzione.it" to 80, target)
      assertTrue(request, request.startsWith("POST /acs?x=1 HTTP/1.1\r\nHost: eid.istruzione.it\r\n"))
      assertFalse(request, request.contains("Proxy-Connection", ignoreCase = true))
      assertTrue(request, request.contains("Connection: close\r\n\r\nA=b&c"))
    }
  }

  @Test fun aRedirectedDestinationNeedsItsOwnAllowedConnectAndCannotReuseAuthorization() {
    val calls = AtomicInteger()
    ServerSocket(0).use { upstream ->
      val done = CountDownLatch(1)
      thread(isDaemon = true) {
        upstream.accept().use { remote ->
          remote.getOutputStream().write("HTTP/1.1 307 Temporary Redirect\r\nLocation: https://evil.example/next\r\n\r\n".toByteArray(US_ASCII))
          done.countDown()
        }
      }
      CieConnectProxy(connect = { socket, _, _ ->
        calls.incrementAndGet()
        socket.connect(InetSocketAddress("127.0.0.1", upstream.localPort), 2_000)
      }).use { proxy ->
        client(proxy).use { first ->
          first.getOutputStream().write("CONNECT web.spaggiari.eu:443 HTTP/1.1\r\n\r\n".toByteArray(US_ASCII))
          assertTrue(readHeader(first.getInputStream()).contains("200"))
          assertTrue(readHeader(first.getInputStream()).contains("307"))
        }
        client(proxy).use { redirected ->
          redirected.getOutputStream().write("CONNECT evil.example:443 HTTP/1.1\r\n\r\n".toByteArray(US_ASCII))
          assertTrue(readHeader(redirected.getInputStream()).contains("403"))
        }
        assertTrue(done.await(2, TimeUnit.SECONDS))
        assertEquals(1, calls.get())
      }
    }
  }

  @Test fun stoppingTheFenceClosesPendingHeadersAndBothSidesOfActiveTunnels() {
    ServerSocket(0).use { upstream ->
      val remoteClosed = CountDownLatch(1)
      thread(isDaemon = true) {
        upstream.accept().use { remote ->
          remote.soTimeout = 2_000
          if (remote.getInputStream().read() == -1) remoteClosed.countDown()
        }
      }
      val proxy = CieConnectProxy(connect = { socket, _, _ -> socket.connect(InetSocketAddress("127.0.0.1", upstream.localPort), 2_000) })
      client(proxy).use { active ->
        client(proxy).use { pending ->
          active.getOutputStream().write("CONNECT web.spaggiari.eu:443 HTTP/1.1\r\n\r\n".toByteArray(US_ASCII))
          assertTrue(readHeader(active.getInputStream()).contains("200"))
          pending.getOutputStream().write("CONNECT ".toByteArray(US_ASCII))
          proxy.close()
          assertClosed(active.getInputStream())
          assertClosed(pending.getInputStream())
          assertTrue(remoteClosed.await(2, TimeUnit.SECONDS))
        }
      }
      assertTrue(runCatching { client(proxy).close() }.isFailure)
      proxy.close() // Idempotent shutdown, no listener/server remains while idle.
    }
  }

  private fun client(proxy: CieConnectProxy) = Socket().apply {
    soTimeout = 2_000
    connect(InetSocketAddress("127.0.0.1", proxy.port), 2_000)
  }

  private fun assertClosed(input: InputStream) {
    try { assertEquals(-1, input.read()) } catch (_: SocketException) {
      // Closing an admitted socket or a connection still in the accept backlog may send RST.
    }
  }

  private fun readExactly(input: InputStream, bytes: ByteArray) {
    var offset = 0
    while (offset < bytes.size) {
      val count = input.read(bytes, offset, bytes.size - offset)
      check(count > 0) { "Unexpected end of fixture stream" }
      offset += count
    }
  }

  private fun readHeader(input: InputStream): String {
    val result = StringBuilder()
    while (!result.endsWith("\r\n\r\n") && result.length < 8_192) {
      val next = input.read()
      check(next >= 0) { "Unexpected end of fixture header" }
      result.append(next.toChar())
    }
    return result.toString()
  }
}
