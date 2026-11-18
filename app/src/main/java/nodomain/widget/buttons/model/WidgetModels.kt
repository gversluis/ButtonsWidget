package nodomain.widget.buttons.model

import android.content.Context
import nodomain.widget.buttons.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ButtonType {
    CALL_URL,
    TEXTAREA,
    COPY_CLIPBOARD
}

data class GridButton(
    val id: String = UUID.randomUUID().toString(),
    var row: Int,
    var col: Int,
    var rowSpan: Int = 1,
    var colSpan: Int = 1,
    var type: ButtonType = ButtonType.CALL_URL,
    var text: String = "Btn",
    var iconUri: String? = null,
    var url: String = "",
    var cachedResponse: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("row", row)
        put("col", col)
        put("rowSpan", rowSpan)
        put("colSpan", colSpan)
        put("type", type.name)
        put("text", text)
        put("iconUri", iconUri ?: "")
        put("url", url)
        put("cachedResponse", cachedResponse)
    }

    companion object {
        fun fromJson(obj: JSONObject): GridButton = GridButton(
            id = obj.optString("id", UUID.randomUUID().toString()),
            row = obj.optInt("row", 0),
            col = obj.optInt("col", 0),
            rowSpan = obj.optInt("rowSpan", 1).coerceAtLeast(1),
            colSpan = obj.optInt("colSpan", 1).coerceAtLeast(1),
            type = runCatching { ButtonType.valueOf(obj.optString("type")) }.getOrDefault(ButtonType.CALL_URL),
            text = obj.optString("text", ""),
            iconUri = obj.optString("iconUri").takeIf { it.isNotEmpty() },
            url = obj.optString("url", ""),
            cachedResponse = obj.optString("cachedResponse", "")
        )
    }
}

data class WidgetConfig(
    val appWidgetId: Int,
    var gridWidth: Int = 1,
    var gridHeight: Int = 1,
    val buttons: MutableList<GridButton> = mutableListOf()
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("gridWidth", gridWidth)
        root.put("gridHeight", gridHeight)
        val arr = JSONArray()
        buttons.forEach { arr.put(it.toJson()) }
        root.put("buttons", arr)
        return root.toString(2)
    }

    companion object {
        fun fromJsonString(context: Context, appWidgetId: Int, jsonStr: String): WidgetConfig {
            val root = JSONObject(jsonStr)
            val w = root.optInt("gridWidth", 1).coerceIn(1, context.resources.getInteger(R.integer.max_grid_width))
            val h = root.optInt("gridHeight", 1).coerceIn(1, context.resources.getInteger(R.integer.max_grid_height))
            val arr = root.optJSONArray("buttons") ?: JSONArray()
            val list = mutableListOf<GridButton>()
            for (i in 0 until arr.length()) {
                list.add(GridButton.fromJson(arr.getJSONObject(i)))
            }
            return WidgetConfig(appWidgetId, w, h, list)
        }
    }
}
