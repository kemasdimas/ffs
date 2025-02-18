package doist.ffs

import doist.ffs.endpoints.AuthScheme
import doist.ffs.env.ENV_DEVICE_IP
import doist.ffs.env.ENV_DEVICE_LOCALE
import doist.ffs.env.ENV_DEVICE_NAME
import doist.ffs.env.ENV_DEVICE_OS
import doist.ffs.env.ENV_INTERNAL_ROLLOUT_ID
import doist.ffs.env.ENV_USER_EMAIL
import doist.ffs.env.ENV_USER_ID
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// This can and should be multiplatform, but building fails due to a coroutines version mismatch.
// See: https://youtrack.jetbrains.com/issue/KT-50222
class ClientTest {
    class MockClient(liveUpdates: Boolean = true) :
        Client<Unit>(TOKEN, "https://doist.com", "/dummy", liveUpdates) {
        override val data = Unit

        override fun updateData(response: String) = Unit

        override fun isEnabled(name: String, default: Boolean) = default

        override fun all() = emptyMap<String, Boolean>()
    }

    private val engine = MockEngine {
        // Respond with error by default so that SSE terminates.
        respondError(HttpStatusCode.InternalServerError)
    }

    @Test
    fun apiToken() = runTest {
        MockClient().initialize(engine).join()
        val request = engine.requestHistory.last()
        assertEquals("${AuthScheme.Token} $TOKEN", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun params() = runTest {
        MockClient().initialize(engine).join()
        val request = engine.requestHistory.last()
        assertContains(request.url.parameters.names(), "env")
    }

    @Test
    fun env() = runTest {
        MockClient().apply {
            setRolloutId(ROLLOUT_ID)
            setUserId(USER_ID)
            setUserEmail(USER_EMAIL)
            setDeviceName(DEVICE_NAME)
            setDeviceOs(DEVICE_OS)
            setDeviceLocale(DEVICE_LOCALE)
            setDeviceIp(DEVICE_IP)
            putNumber(KEY_RANDOM_NUMBER, RANDOM_NUMBER)
            putBoolean(KEY_RANDOM_BOOLEAN, RANDOM_BOOLEAN)
            putListString(KEY_RANDOM_LIST, RANDOM_LIST)
        }.initialize(engine).join()
        val request = engine.requestHistory.last()
        val env = Json.decodeFromString<JsonObject>(request.url.parameters["env"]!!)
        assertEquals(env[ENV_INTERNAL_ROLLOUT_ID], JsonPrimitive(ROLLOUT_ID))
        assertEquals(env[ENV_USER_ID], JsonPrimitive(USER_ID))
        assertEquals(env[ENV_USER_EMAIL], JsonPrimitive(USER_EMAIL))
        assertEquals(env[ENV_DEVICE_NAME], JsonPrimitive(DEVICE_NAME))
        assertEquals(env[ENV_DEVICE_OS], JsonPrimitive(DEVICE_OS))
        assertEquals(env[ENV_DEVICE_LOCALE], JsonPrimitive(DEVICE_LOCALE))
        assertEquals(env[ENV_DEVICE_IP], JsonPrimitive(DEVICE_IP))
        assertEquals(env[KEY_RANDOM_NUMBER], JsonPrimitive(RANDOM_NUMBER))
        assertEquals(env[KEY_RANDOM_BOOLEAN], JsonPrimitive(RANDOM_BOOLEAN))
        assertEquals(env[KEY_RANDOM_LIST], JsonArray(RANDOM_LIST.map { JsonPrimitive(it) }))
    }

    @Test
    fun liveUpdatesFlag() = runTest {
        MockClient().initialize(engine).join()
        var request = engine.requestHistory.last()
        assertTrue(
            request.headers[HttpHeaders.Accept]!!.contains(ContentType.Text.EventStream.toString())
        )

        MockClient(liveUpdates = true).initialize(engine).join()
        request = engine.requestHistory.last()
        assertTrue(
            request.headers[HttpHeaders.Accept]!!.contains(ContentType.Text.EventStream.toString())
        )

        MockClient(liveUpdates = false).initialize(engine).join()
        request = engine.requestHistory.last()
        assertFalse(
            request.headers[HttpHeaders.Accept]!!.contains(ContentType.Text.EventStream.toString())
        )
    }

    companion object {
        const val TOKEN = "123456789abcdef"

        const val ROLLOUT_ID = "a-random-string"

        const val USER_ID = "1"
        const val USER_EMAIL = "test@test.test"

        const val DEVICE_NAME = "Pixel 6 Pro"
        const val DEVICE_OS = "Android 12"
        const val DEVICE_LOCALE = "en-US"
        const val DEVICE_IP = "10.0.0.0"

        const val RANDOM_NUMBER = 42
        const val RANDOM_BOOLEAN = true
        val RANDOM_LIST = listOf("a", "b", "c")

        const val KEY_RANDOM_NUMBER = "random_number"
        const val KEY_RANDOM_BOOLEAN = "random_boolean"
        const val KEY_RANDOM_LIST = "random_list"
    }
}
