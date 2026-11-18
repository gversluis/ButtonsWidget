package nodomain.widget.buttons.data

import android.content.Context
import nodomain.widget.buttons.model.WidgetConfig

object WidgetStorage {
    private const val PREFS_NAME = "grid_widget_prefs"

    fun saveConfig(context: Context, config: WidgetConfig) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("widget_${config.appWidgetId}", config.toJsonString()).apply()
    }

    fun loadConfig(context: Context, appWidgetId: Int): WidgetConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString("widget_$appWidgetId", null)
        return if (raw != null) {
            runCatching { WidgetConfig.fromJsonString(context, appWidgetId, raw) }
                .getOrElse { WidgetConfig(appWidgetId) }
        } else {
            WidgetConfig(appWidgetId)
        }
    }

    fun deleteConfig(context: Context, appWidgetId: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove("widget_$appWidgetId").apply()
    }
}
