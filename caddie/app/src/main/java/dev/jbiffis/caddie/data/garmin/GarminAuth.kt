package dev.jbiffis.caddie.data.garmin

import android.content.SharedPreferences
import org.json.JSONObject
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class GarminAuthException(message: String) : Exception(message)

/**
 * Garmin Connect authentication: SSO login → OAuth1 token → OAuth2 bearer.
 *
 * This is the same flow the Garmin Connect mobile app (and the `garth` Python
 * library) uses. The OAuth1 token lives for about a year and is stored in app
 * prefs; short-lived OAuth2 bearers are minted from it on demand. Credentials
 * are only ever sent to sso.garmin.com.
 *
 * Accounts with two-factor authentication are not supported yet.
 */
class GarminAuth(private val prefs: SharedPreferences) {

    companion object {
        private const val UA = "com.garmin.android.apps.connectmobile"
        private const val SSO = "https://sso.garmin.com/sso"
        private const val SSO_EMBED = "$SSO/embed"
        private const val CONNECT_API = "https://connectapi.garmin.com"
        // Public consumer credentials, published the same way garth obtains them
        private const val CONSUMER_URL = "https://thegarth.s3.amazonaws.com/oauth_consumer.json"
    }

    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)

    val isLoggedIn: Boolean get() = prefs.contains("oauth1_token")
    val username: String? get() = prefs.getString("username", null)

    fun logout() {
        prefs.edit().clear().apply()
    }

    /** Blocking. Throws [GarminAuthException] with a user-readable message on failure. */
    fun login(email: String, password: String) {
        val (consumerKey, consumerSecret) = consumer()

        // 1. Prime SSO cookies
        get("$SSO_EMBED?id=gauth-widget&embedWidget=true&gauthHost=$SSO")

        // 2. Fetch the sign-in page for the CSRF token
        val signinParams = "?id=gauth-widget&embedWidget=true&gauthHost=$SSO_EMBED" +
            "&service=$SSO_EMBED&source=$SSO_EMBED" +
            "&redirectAfterAccountLoginUrl=$SSO_EMBED&redirectAfterAccountCreationUrl=$SSO_EMBED"
        val signinPage = get("$SSO/signin$signinParams")
        val csrf = Regex("name=\"_csrf\"\\s+value=\"([^\"]+)\"").find(signinPage)?.groupValues?.get(1)
            ?: throw GarminAuthException("Could not read Garmin sign-in page (CSRF token missing)")

        // 3. Post credentials
        val form = "username=${enc(email)}&password=${enc(password)}&embed=true&_csrf=${enc(csrf)}"
        val loginResponse = post("$SSO/signin$signinParams", form, referer = "$SSO/signin$signinParams")
        if (loginResponse.contains("MFA", ignoreCase = false) && loginResponse.contains("verifyMFA")) {
            throw GarminAuthException("This account uses two-factor auth, which isn't supported yet")
        }
        val ticket = Regex("embed\\?ticket=([^\"]+)\"").find(loginResponse)?.groupValues?.get(1)
            ?: Regex("ticket=([\\w-]+)").find(loginResponse)?.groupValues?.get(1)
            ?: throw GarminAuthException(
                if (loginResponse.contains("locked", true)) "Garmin says the account is locked"
                else "Sign-in failed — check email and password"
            )

        // 4. Exchange the SSO ticket for a long-lived OAuth1 token
        val preauthUrl = "$CONNECT_API/oauth-service/oauth/preauthorized"
        val query = mapOf(
            "ticket" to ticket,
            "login-url" to SSO_EMBED,
            "accepts-mfa-tokens" to "true",
        )
        val authHeader = oauth1Header("GET", preauthUrl, query, consumerKey, consumerSecret, null, null)
        val tokenResponse = get(preauthUrl + "?" + query.entries.joinToString("&") { "${it.key}=${enc(it.value)}" }, authHeader)
        val oauth1Token = Regex("oauth_token=([^&]+)").find(tokenResponse)?.groupValues?.get(1)
            ?: throw GarminAuthException("OAuth token exchange failed")
        val oauth1Secret = Regex("oauth_token_secret=([^&]+)").find(tokenResponse)?.groupValues?.get(1)
            ?: throw GarminAuthException("OAuth token exchange failed (no secret)")

        prefs.edit()
            .putString("oauth1_token", oauth1Token)
            .putString("oauth1_secret", oauth1Secret)
            .putString("username", email)
            .remove("oauth2_token")
            .apply()

        // 5. Mint the first bearer now so problems surface at login time
        bearer(forceRefresh = true)
    }

    /** Returns a valid OAuth2 bearer token, refreshing via the stored OAuth1 token if needed. */
    fun bearer(forceRefresh: Boolean = false): String {
        if (!isLoggedIn) throw GarminAuthException("Not signed in to Garmin Connect")
        if (!forceRefresh) {
            val cached = prefs.getString("oauth2_token", null)
            val expiry = prefs.getLong("oauth2_expiry", 0)
            if (cached != null && System.currentTimeMillis() < expiry - 60_000) return cached
        }
        val (consumerKey, consumerSecret) = consumer()
        val token = prefs.getString("oauth1_token", null)!!
        val secret = prefs.getString("oauth1_secret", null)!!
        val url = "$CONNECT_API/oauth-service/oauth/exchange/user/2.0"
        val header = oauth1Header("POST", url, emptyMap(), consumerKey, consumerSecret, token, secret)
        val body = post(url, "", authHeader = header)
        val json = JSONObject(body)
        val access = json.optString("access_token", "")
        if (access.isEmpty()) throw GarminAuthException("Garmin session expired — sign in again")
        prefs.edit()
            .putString("oauth2_token", access)
            .putLong("oauth2_expiry", System.currentTimeMillis() + json.optLong("expires_in", 3600) * 1000)
            .apply()
        return access
    }

    private fun consumer(): Pair<String, String> {
        prefs.getString("consumer_key", null)?.let { k ->
            prefs.getString("consumer_secret", null)?.let { s -> return k to s }
        }
        val json = JSONObject(get(CONSUMER_URL))
        val key = json.getString("consumer_key")
        val secret = json.getString("consumer_secret")
        prefs.edit().putString("consumer_key", key).putString("consumer_secret", secret).apply()
        return key to secret
    }

    // ---- HTTP helpers ----------------------------------------------------------

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 20000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", UA)
        val uri = URL(url).toURI()
        val cookieHeader = cookies.cookieStore.get(uri).joinToString("; ") { "${it.name}=${it.value}" }
        if (cookieHeader.isNotEmpty()) conn.setRequestProperty("Cookie", cookieHeader)
        return conn
    }

    private fun collect(conn: HttpURLConnection, url: String): String {
        conn.headerFields["Set-Cookie"]?.forEach { header ->
            runCatching { cookies.put(URL(url).toURI(), mapOf("Set-Cookie" to listOf(header))) }
        }
        val stream = if (conn.responseCode in 200..399) conn.inputStream else conn.errorStream
        val body = stream?.use { it.readBytes().decodeToString() } ?: ""
        if (conn.responseCode !in 200..399) {
            throw GarminAuthException("Garmin returned HTTP ${conn.responseCode}")
        }
        return body
    }

    private fun get(url: String, authHeader: String? = null): String {
        val conn = open(url)
        authHeader?.let { conn.setRequestProperty("Authorization", it) }
        return collect(conn, url)
    }

    private fun post(url: String, form: String, referer: String? = null, authHeader: String? = null): String {
        val conn = open(url)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        referer?.let { conn.setRequestProperty("Referer", it) }
        authHeader?.let { conn.setRequestProperty("Authorization", it) }
        conn.outputStream.use { it.write(form.toByteArray()) }
        return collect(conn, url)
    }

    // ---- OAuth1 signing --------------------------------------------------------

    private fun enc(s: String): String =
        URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

    private fun oauth1Header(
        method: String,
        url: String,
        query: Map<String, String>,
        consumerKey: String,
        consumerSecret: String,
        token: String?,
        tokenSecret: String?,
    ): String {
        val oauth = sortedMapOf(
            "oauth_consumer_key" to consumerKey,
            "oauth_nonce" to (System.nanoTime().toString() + (1000..9999).random()),
            "oauth_signature_method" to "HMAC-SHA1",
            "oauth_timestamp" to (System.currentTimeMillis() / 1000).toString(),
            "oauth_version" to "1.0",
        )
        token?.let { oauth["oauth_token"] = it }

        val allParams = (query + oauth).toSortedMap()
        val paramString = allParams.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        val baseString = "${method.uppercase()}&${enc(url)}&${enc(paramString)}"
        val signingKey = "${enc(consumerSecret)}&${enc(tokenSecret ?: "")}"

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(signingKey.toByteArray(), "HmacSHA1"))
        val signature = android.util.Base64.encodeToString(
            mac.doFinal(baseString.toByteArray()), android.util.Base64.NO_WRAP,
        )
        oauth["oauth_signature"] = signature
        return "OAuth " + oauth.entries.joinToString(", ") { "${enc(it.key)}=\"${enc(it.value)}\"" }
    }
}
