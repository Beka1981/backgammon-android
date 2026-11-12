package ge.gogichaishvili.backgammon.domain.auth

import java.net.URI
import java.net.URLDecoder

enum class EmailFlow { CONFIRM, RECOVERY }
data class AuthCallback(val flow: EmailFlow, val code: String) {
    // Never allow accidental logging of the authorization code.
    override fun toString() = "AuthCallback(flow=$flow, code=[redacted])"
    companion object {
        const val confirmUrl = "ge.gogichaishvili.backgammon://auth/confirm"
        const val recoveryUrl = "ge.gogichaishvili.backgammon://auth/recovery"
        fun parse(url: String, expectedScheme: String = "ge.gogichaishvili.backgammon"): AuthCallback? = runCatching {
            if (url.length > 4096) return null
            val uri = URI(url)
            if (uri.scheme != expectedScheme || uri.host != "auth" ||
                uri.userInfo != null || uri.port != -1 || uri.rawFragment != null) return null
            val flow = when (uri.path) {
                "/confirm" -> EmailFlow.CONFIRM
                "/recovery" -> EmailFlow.RECOVERY
                else -> return null
            }
            val pairs = (uri.rawQuery ?: return null).split('&').map {
                val pair = it.split('=', limit = 2)
                if (pair.size != 2) return null
                URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
            }
            if (pairs.size != 1 || pairs.single().first != "code") return null
            val code = pairs.single().second
            if (!Regex("[A-Za-z0-9._~-]{1,2048}").matches(code)) return null
            AuthCallback(flow, code)
        }.getOrNull()
    }
}
