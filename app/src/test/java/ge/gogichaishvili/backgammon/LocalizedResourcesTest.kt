package ge.gogichaishvili.backgammon

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizedResourcesTest {
    private fun entries(locale: String, name: String): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val nodes = factory.newDocumentBuilder().parse(File("src/main/res/$locale/$name.xml"))
            .documentElement.childNodes
        return buildMap {
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE)
                    put(node.attributes.getNamedItem("name").nodeValue, node.textContent)
            }
        }
    }
    @Test fun englishAndGeorgianHaveIdenticalKeysAndFormatArguments() {
        val format = Regex("%[1-9][0-9]*\\$[sd]")
        listOf("strings", "game").forEach { file ->
            val en = entries("values", file); val ka = entries("values-ka", file)
            assertEquals(en.keys, ka.keys)
            en.forEach { (key, value) ->
                assertEquals("$file/$key", format.findAll(value).map { it.value }.toList(),
                    format.findAll(ka.getValue(key)).map { it.value }.toList())
            }
        }
    }
    @Test fun gameplayAndActionLabelsContainGeorgianText() {
        val ka = entries("values-ka", "game")
        listOf("game_select_action", "game_move_action", "game_select_control", "game_move_control",
            "game_new_title", "game_choose_die", "match_retry").forEach {
            assertTrue(it, Regex("[ა-ჰ]").containsMatchIn(ka.getValue(it)))
        }
    }
}
