package ge.gogichaishvili.backgammon.auth

import ge.gogichaishvili.backgammon.domain.auth.*
import ge.gogichaishvili.backgammon.data.auth.SupabaseConfiguration
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AuthValidationTest {
    @Test fun emailWhitespaceIsTrimmedButMalformedEmailsRejected() {
        assertTrue(AuthValidation.email(" fixture@example.test "))
        listOf("", "invalid", "@example.test", "a@b", "a b@example.test", "a@example .test")
            .forEach { assertFalse(it, AuthValidation.email(it)) }
    }
    @Test fun passwordIsNotTrimmedAndClientFloorIsOnlyForNewPasswords() {
        assertNull(AuthValidation.registration("a@example.test", "long phrase  ", "long phrase  "))
        assertEquals(AuthIssue.PASSWORD_MISMATCH,
            AuthValidation.registration("a@example.test", "long phrase  ", "long phrase"))
        assertNull(AuthValidation.login("a@example.test", "old"))
        assertEquals(AuthIssue.WEAK_PASSWORD,
            AuthValidation.registration("a@example.test", "a".repeat(129), "a".repeat(129)))
    }
    @Test fun onlyExactPkcePathsAndSingleCodeAreAccepted() {
        assertEquals(EmailFlow.CONFIRM, AuthCallback.parse(AuthCallback.confirmUrl + "?code=fixture-123")?.flow)
        assertEquals(EmailFlow.RECOVERY, AuthCallback.parse(AuthCallback.recoveryUrl + "?code=fixture-123")?.flow)
        assertFalse(AuthCallback.parse(AuthCallback.confirmUrl + "?code=private-code").toString().contains("private-code"))
        listOf(
            "https://example.test/auth?code=x",
            "ge.gogichaishvili.backgammon://evil/confirm?code=x",
            "ge.gogichaishvili.backgammon://user@auth/confirm?code=x",
            AuthCallback.confirmUrl + "?code=x&code=y",
            AuthCallback.confirmUrl + "?access_token=fake",
            AuthCallback.confirmUrl + "#access_token=fake",
            AuthCallback.confirmUrl + "?code=x&redirect_to=https://example.test",
            AuthCallback.confirmUrl + "?code=",
            AuthCallback.confirmUrl + "?code=%0A",
            AuthCallback.confirmUrl + "?code=" + "a".repeat(4097)
        ).forEach { assertNull(it.substringBefore('?'), AuthCallback.parse(it)) }
    }
    @Test fun missingMalformedAndPrivilegedConfigurationIsRejected() {
        val publicFixture = "sb_publishable_" + "fixture".repeat(4)
        assertNotNull(SupabaseConfiguration.parse("https://example.supabase.co", publicFixture))
        listOf("", "http://example.supabase.co", "https://user:pass@example.supabase.co",
            "https://example.supabase.co/auth/v1", "https://example.supabase.co?query=x",
            "https://example.supabase.co#fragment", "https://example.supabase.co:8080")
            .forEach { assertNull(SupabaseConfiguration.parse(it, publicFixture)) }
        listOf("", "sb_secret_" + "fixture".repeat(4), "service_role", "eyJfixture")
            .forEach { assertNull(SupabaseConfiguration.parse("https://example.supabase.co", it)) }
        assertFalse(SupabaseConfiguration.parse("https://example.supabase.co", publicFixture).toString().contains(publicFixture))
    }
    @Test fun englishAndGeorgianAuthResourcesHaveParity() {
        fun entries(language: String): Map<String, String> {
            val factory = DocumentBuilderFactory.newInstance().apply {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            val doc = factory.newDocumentBuilder().parse(File("src/main/res/" + language + "/auth.xml"))
            val nodes = doc.getElementsByTagName("string")
            return (0 until nodes.length).associate {
                val node = nodes.item(it)
                node.attributes.getNamedItem("name").nodeValue to node.textContent
            }
        }
        val english = entries("values"); val georgian = entries("values-ka")
        assertEquals(english.keys, georgian.keys)
        georgian.forEach { (key, value) -> assertTrue(key, value.any { it in '\u10A0'..'\u10FF' }) }
    }
}
