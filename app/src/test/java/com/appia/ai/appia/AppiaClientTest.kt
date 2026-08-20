package com.appia.ai.appia

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppiaClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: AppiaClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val config = AppiaConfig(
            serverUrl = server.url("/").toString().trimEnd('/'),
            userId = "uid-1",
            authToken = "token-1"
        )
        client = AppiaClient(config, OkHttpClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueue(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    @Test
    fun `listRooms parses subscriptions with unread counts`() = runTest {
        enqueue(
            """{"update":[
                {"rid":"r1","name":"general","t":"c","unread":3},
                {"rid":"r2","name":"bob","t":"d","unread":0},
                {"rid":"r3","name":"team","t":"p","unread":1}
            ],"success":true}"""
        )
        val rooms = client.listRooms()
        assertEquals(3, rooms.size)
        assertEquals(AppiaRoom("r1", "general", "c", 3), rooms[0])
        assertEquals(AppiaRoom("r2", "bob", "d", 0), rooms[1])
        assertEquals(AppiaRoom("r3", "team", "p", 1), rooms[2])

        val request = server.takeRequest()
        assertEquals("token-1", request.getHeader("X-Auth-Token"))
        assertEquals("uid-1", request.getHeader("X-User-Id"))
        assertTrue(request.path!!.startsWith("/api/v1/subscriptions.get"))
    }

    @Test
    fun `readHistory picks endpoint by room type and sorts by timestamp`() = runTest {
        enqueue(
            """{"messages":[
                {"msg":"later","u":{"username":"alice"},"ts":{"${'$'}date":2000}},
                {"msg":"earlier","u":{"username":"bob"},"ts":{"${'$'}date":1000}}
            ],"success":true}"""
        )
        val messages = client.readHistory(AppiaRoom("r1", "bob", "d", 2), 10)
        assertEquals(2, messages.size)
        assertEquals("earlier", messages[0].text)
        assertEquals("bob", messages[0].sender)
        assertEquals(1000L, messages[0].timestamp)
        assertEquals("later", messages[1].text)
        assertTrue(server.takeRequest().path!!.startsWith("/api/v1/im.history?roomId=r1"))

        enqueue("""{"messages":[],"success":true}""")
        client.readHistory(AppiaRoom("r2", "team", "p", 0), 5)
        assertTrue(server.takeRequest().path!!.startsWith("/api/v1/groups.history?roomId=r2"))

        enqueue("""{"messages":[],"success":true}""")
        client.readHistory(AppiaRoom("r3", "general", "c", 0), 5)
        assertTrue(server.takeRequest().path!!.startsWith("/api/v1/channels.history?roomId=r3"))
    }

    @Test
    fun `readMessages without target picks unread rooms sorted desc max 5`() = runTest {
        val rooms = (1..7).joinToString(",") { i ->
            """{"rid":"r$i","name":"room$i","t":"c","unread":${i}}"""
        } + """,{"rid":"r0","name":"empty","t":"c","unread":0}"""
        enqueue("""{"update":[$rooms],"success":true}""")
        repeat(5) { i ->
            enqueue("""{"messages":[{"msg":"m$i","u":{"username":"u"},"ts":{"${'$'}date":$i}}],"success":true}""")
        }
        val result = client.readMessages(null, 10)
        assertEquals(listOf("room7", "room6", "room5", "room4", "room3"), result.keys.map { it.name })
    }

    @Test
    fun `readMessages with target matches room name case-insensitively`() = runTest {
        enqueue(
            """{"update":[
                {"rid":"r1","name":"General","t":"c","unread":0},
                {"rid":"r2","name":"bob","t":"d","unread":0}
            ],"success":true}"""
        )
        enqueue("""{"messages":[{"msg":"hi","u":{"username":"bob"},"ts":{"${'$'}date":1}}],"success":true}""")
        val result = client.readMessages("#general", 10)
        assertEquals(listOf("General"), result.keys.map { it.name })
        assertEquals("hi", result.values.single()[0].text)
    }

    @Test
    fun `readMessages returns empty when target not found`() = runTest {
        enqueue("""{"update":[{"rid":"r1","name":"general","t":"c","unread":1}],"success":true}""")
        assertTrue(client.readMessages("@nobody", 10).isEmpty())
    }

    @Test
    fun `readMessages returns empty when no unread`() = runTest {
        enqueue("""{"update":[{"rid":"r1","name":"general","t":"c","unread":0}],"success":true}""")
        assertTrue(client.readMessages(null, 10).isEmpty())
    }

    @Test
    fun `http error throws ApiException with server error text`() = runTest {
        enqueue("""{"success":false,"error":"Invalid token"}""", code = 401)
        val e = try {
            client.listRooms()
            null
        } catch (ex: AppiaClient.ApiException) {
            ex
        }
        assertTrue(e!!.message!!.contains("401"))
        assertTrue(e.message!!.contains("Invalid token"))
    }
}
