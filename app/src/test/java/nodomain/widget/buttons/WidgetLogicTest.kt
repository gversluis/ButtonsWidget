package nodomain.widget.buttons

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import nodomain.widget.buttons.model.ButtonType
import nodomain.widget.buttons.model.GridButton
import nodomain.widget.buttons.model.WidgetConfig
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetLogicTest {

    @Test
    fun testDefaultConfigIs1x1() {
        val cfg = WidgetConfig(appWidgetId = 42)
        assertEquals(1, cfg.gridWidth)
        assertEquals(1, cfg.gridHeight)
        assertEquals(0, cfg.buttons.size)
    }

    @Test
    fun testExportAndImportRoundTrip() {
        val original = WidgetConfig(appWidgetId = 10, gridWidth = 3, gridHeight = 2).apply {
            buttons.add(
                GridButton(
                    id = "btn-1",
                    row = 0,
                    col = 0,
                    rowSpan = 1,
                    colSpan = 2,
                    type = ButtonType.CALL_URL,
                    text = "⚡ Power",
                    url = "https://example.org"
                )
            )
            buttons.add(
                GridButton(
                    id = "btn-2",
                    row = 1,
                    col = 0,
                    rowSpan = 1,
                    colSpan = 3,
                    type = ButtonType.TEXTAREA,
                    text = "Status",
                    url = "https://example.org",
                    cachedResponse = "OK"
                )
            )
        }

        val json = original.toJsonString()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val restored = WidgetConfig.fromJsonString(context, 99, json)

        assertEquals(3, restored.gridWidth)
        assertEquals(2, restored.gridHeight)
        assertEquals(2, restored.buttons.size)
        assertEquals(ButtonType.CALL_URL, restored.buttons[0].type)
        assertEquals(2, restored.buttons[0].colSpan)
        assertEquals("OK", restored.buttons[1].cachedResponse)
        assertNull(restored.buttons[0].iconUri)
    }
}