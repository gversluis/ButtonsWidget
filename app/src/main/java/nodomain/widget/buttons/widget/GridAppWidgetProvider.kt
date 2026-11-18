package nodomain.widget.buttons.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Html
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import nodomain.widget.buttons.R
import nodomain.widget.buttons.data.WidgetStorage
import nodomain.widget.buttons.model.ButtonType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class GridAppWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_BUTTON_CLICK = "nodomain.widget.buttons.ACTION_BUTTON_CLICK"
        const val ACTION_FLASH_RESET = "nodomain.widget.buttons.ACTION_FLASH_RESET"
        const val ACTION_POLL_TEXTAREAS = "nodomain.widget.buttons.ACTION_POLL_TEXTAREAS"
        const val EXTRA_BUTTON_ID = "extra_button_id"
        const val FLASH_DURATION = 250L

        private var flashingButtonId: String? = null

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val config = WidgetStorage.loadConfig(context, appWidgetId)
            // TODO: Optimisation, Do not removeAllViews until we are sure there are updates...
            val rootViews = RemoteViews(context.packageName, R.layout.widget)
            rootViews.removeAllViews(R.id.widget_layout)

            rootViews.setInt(R.id.widget_layout,"setColumnCount",config.gridWidth)
            rootViews.setInt(R.id.widget_layout,"setRowCount",config.gridHeight)

            val occupied = Array(config.gridHeight) { BooleanArray(config.gridWidth) { false } }

            for (y in 0 until config.gridHeight) {
                for (x in 0 until config.gridWidth) {
                    if (occupied[y][x]) continue
                    val btn = config.buttons.find { it.row == y && it.col == x }
                    if (btn == null) {
                        rootViews.addView(R.id.widget_layout, RemoteViews(context.packageName, R.layout.widget_tile_empty))
                        Log.d("Tile", "$x,$y Added widget_tile_empty 1x1")
                    } else {
                        val layoutName = "widget_tile_${btn.colSpan}x${btn.rowSpan}"
                        val layoutId = context.resources.getIdentifier(layoutName,"layout",context.packageName)
                        val tileView = RemoteViews(context.packageName, if (layoutId != 0) layoutId else R.layout.widget_tile_1x1)
                        Log.d("Tile", "$x,$y Added $layoutName")
                        for (yOccupied in 0 until btn.rowSpan ) {
                            for (xOccupied in 0 until btn.colSpan ) {
                                occupied[y+yOccupied][x+xOccupied] = true
                            }
                        }

                        val isFlashing = (btn.id == flashingButtonId)
                        tileView.setInt(
                            R.id.cell_container,
                            "setBackgroundResource",
                            if (isFlashing) R.drawable.bg_button_flash else R.drawable.bg_button_dark
                        )


                        val displayText = when (btn.type) {
                            ButtonType.TEXTAREA -> btn.cachedResponse.ifEmpty { btn.text.ifEmpty { "Waiting..." } }
                            else -> btn.text
                        }
                        if (btn.type == ButtonType.TEXTAREA || displayText.isNotEmpty()) {
                            tileView.setTextViewText(R.id.cell_text, displayText)
                            tileView.setViewVisibility(R.id.cell_text, View.VISIBLE)
                        } else {
                            tileView.setViewVisibility(R.id.cell_text, View.GONE)
                        }


                        if (!btn.iconUri.isNullOrEmpty()) {
                            runCatching {
                                val bitmap = WidgetIconCache.getIcon(
                                    context,
                                    btn.iconUri.toString(),
                                    96
                                )
                                if (bitmap != null) {
                                    tileView.setImageViewBitmap(R.id.cell_icon, bitmap)
                                    tileView.setViewVisibility(R.id.cell_icon, View.VISIBLE)
                                }

                            }
                        } else {
                            tileView.setViewVisibility(R.id.cell_icon, View.GONE)
                        }

                        val clickIntent = Intent(context, GridAppWidgetProvider::class.java).apply {
                            action = ACTION_BUTTON_CLICK
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                            putExtra(EXTRA_BUTTON_ID, btn.id)
                        }
                        val pendingIntent = PendingIntent.getBroadcast(
                            context,
                            btn.id.hashCode(),
                            clickIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        tileView.setOnClickPendingIntent(R.id.cell_container, pendingIntent)
                        rootViews.addView(R.id.widget_layout, tileView)
                    }
                }
            }
            // Log.d("Tiles", occupied.contentDeepToString())
            appWidgetManager.updateAppWidget(appWidgetId, rootViews)
            scheduleMinutePolling(context)
        }

        fun scheduleMinutePolling(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, GridAppWidgetProvider::class.java).apply {
                action = ACTION_POLL_TEXTAREAS
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                9901,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 60_000L,
                60_000L,
                pendingIntent
            )
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(
            context,
            appWidgetManager,
            appWidgetId,
            newOptions
        )
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val manager = AppWidgetManager.getInstance(context)
        when (intent.action) {
            ACTION_BUTTON_CLICK -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                val buttonId = intent.getStringExtra(EXTRA_BUTTON_ID) ?: return
                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

                flashingButtonId = buttonId
                updateAppWidget(context, manager, appWidgetId)

                Handler(Looper.getMainLooper()).postDelayed({
                    flashingButtonId = null
                    updateAppWidget(context, manager, appWidgetId)
                }, FLASH_DURATION)

                val config = WidgetStorage.loadConfig(context, appWidgetId)
                val btn = config.buttons.find { it.id == buttonId } ?: return
                Log.d("ONRECEIVE", "Click $btn")

                when (btn.type) {
                    ButtonType.CALL_URL, ButtonType.TEXTAREA -> {
                        if (btn.url.isNotBlank()) {
                            CoroutineScope(Dispatchers.IO).launch {
                                val responseText = executeHttpGet(btn.url)
                                // Update all TEXTAREA buttons with the called URL response
                                val freshConfig = WidgetStorage.loadConfig(context, appWidgetId)
                                var updated = false
                                freshConfig.buttons.filter { it.type == ButtonType.TEXTAREA }
                                    .forEach { ta ->
                                        ta.cachedResponse = responseText
                                        updated = true
                                    }
                                if (updated) {
                                    WidgetStorage.saveConfig(context, freshConfig)
                                    Handler(Looper.getMainLooper()).post {
                                        updateAppWidget(context, manager, appWidgetId)
                                    }
                                }
                            }
                        }
                    }

                    ButtonType.COPY_CLIPBOARD -> {
                        val textareaContent = config.buttons
                            .firstOrNull { it.type == ButtonType.TEXTAREA }
                            ?.cachedResponse
                            .orEmpty()
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "Buttons Widget Result",
                                textareaContent
                            )
                        )
                    }
                }
            }

            ACTION_POLL_TEXTAREAS -> {
                val ids = manager.getAppWidgetIds(
                    ComponentName(
                        context,
                        GridAppWidgetProvider::class.java
                    )
                )
                CoroutineScope(Dispatchers.IO).launch {
                    for (id in ids) {
                        val config = WidgetStorage.loadConfig(context, id)
                        var changed = false
                        for (btn in config.buttons) {
                            if (btn.type == ButtonType.TEXTAREA && btn.url.isNotBlank()) {
                                btn.cachedResponse = executeHttpGet(btn.url)
                                changed = true
                            }
                        }
                        if (changed) {
                            WidgetStorage.saveConfig(context, config)
                            Handler(Looper.getMainLooper()).post {
                                updateAppWidget(context, manager, id)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            WidgetStorage.deleteConfig(context, id)
        }
    }

    private fun executeHttpGet(urlString: String): String {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            val code = conn.responseCode
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val html = if (body.isNotBlank()) body.trim() else "HTTP $code"
            // TODO: Save as span instead so we can have a little text formatting?
            Html.fromHtml(
                html
                    .replace(Regex("(?is)<head[^>]*>.*?</head>"),"")
                    .replace(Regex("(?is)<style[^>]*>.*?</style>"),"")
                    .replace(Regex("(?is)<script[^>]*>.*?</script>"),"")
                , Html.FROM_HTML_MODE_LEGACY
            ).toString().trim()
        } catch (e: Exception) {
            "Err: ${e.localizedMessage ?: "Failed"}"
        }
    }
}