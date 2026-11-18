package nodomain.widget.buttons.ui

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import nodomain.widget.buttons.R
import nodomain.widget.buttons.data.WidgetStorage
import nodomain.widget.buttons.model.ButtonType
import nodomain.widget.buttons.model.GridButton
import nodomain.widget.buttons.model.WidgetConfig
import nodomain.widget.buttons.widget.GridAppWidgetProvider

class SettingsActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var config: WidgetConfig
    private lateinit var gridContainer: GridLayout
    private lateinit var widthPicker: SeekBar
    private lateinit var heightPicker: SeekBar

    private var pendingIconCallback: ((Uri) -> Unit)? = null

    private val pickIconLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                pendingIconCallback?.invoke(uri)
            }
        }

    private val exportJsonLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(config.toJsonString().toByteArray())
                }
                Toast.makeText(this, "Layout exported", Toast.LENGTH_SHORT).show()
            }
        }

    private val importJsonLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                runCatching {
                    val json = contentResolver.openInputStream(uri)?.bufferedReader()
                        ?.use { it.readText() } ?: return@runCatching
                    config = WidgetConfig.fromJsonString(this.applicationContext, appWidgetId, json)
                    WidgetStorage.saveConfig(this, config)
                    refreshEditorGrid()
                    Toast.makeText(this, "Layout imported", Toast.LENGTH_SHORT).show()
                }.onFailure {
                    Toast.makeText(this, "Invalid layout file", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        config = WidgetStorage.loadConfig(this, appWidgetId)
        buildDarkModeUi()
        refreshEditorGrid()
    }

    override fun onPause() {
        super.onPause()
        saveAndApplyWidget()
    }

    private fun saveAndApplyWidget() {
        WidgetStorage.saveConfig(this, config)
        val manager = AppWidgetManager.getInstance(this)
        GridAppWidgetProvider.updateAppWidget(this, manager, appWidgetId)
        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, resultValue)
    }


    private fun buildDarkModeUi() {
        setContentView(R.layout.settings)
        val root = findViewById<LinearLayout>(R.id.settings_root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = top + 32.dp)
            insets
        }

        gridContainer = findViewById(R.id.grid_container)

        findViewById<Button>(R.id.done_btn).setOnClickListener {
            saveAndApplyWidget()
            finish()
        }

        widthPicker = findViewById(R.id.width_picker)
        heightPicker = findViewById(R.id.height_picker)

        widthPicker.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    changeGridSize(progress - config.gridWidth, 0)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        heightPicker.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    changeGridSize(0, progress - config.gridHeight)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })



        // Export / Import
        findViewById<Button>(R.id.export_btn).setOnClickListener {
            exportJsonLauncher.launch("widget_layout.json")
        }

        findViewById<Button>(R.id.import_btn).setOnClickListener {
            importJsonLauncher.launch(arrayOf("application/json"))
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun changeGridSize(dW: Int, dH: Int) {
        config.gridWidth = (config.gridWidth + dW)
        config.gridHeight = (config.gridHeight + dH)
        // config.buttons.removeAll { it.row >= config.gridHeight || it.col >= config.gridWidth }  // TODO: do not remove so they are not lost when grid is extended again
        refreshEditorGrid()
    }

    private fun refreshEditorGrid() {
        Log.w("GRID", "${config.gridWidth}x${config.gridHeight}")

        widthPicker.progress = config.gridWidth
        heightPicker.progress = config.gridHeight

        gridContainer.removeAllViews()
        gridContainer.columnCount = config.gridWidth
        gridContainer.rowCount = config.gridHeight

        val occupied = Array(config.gridHeight) { BooleanArray(config.gridWidth) }

        for (r in 0 until config.gridHeight) {
            for (c in 0 until config.gridWidth) {
                if (occupied[r][c]) continue

                val btn = config.buttons.find { it.row == r && it.col == c }
                val rSpan = (btn?.rowSpan ?: 1).coerceAtMost(config.gridHeight - r)
                val cSpan = (btn?.colSpan ?: 1).coerceAtMost(config.gridWidth - c)

                for (rr in r until r + rSpan) {
                    for (cc in c until c + cSpan) {
                        occupied[rr][cc] = true
                    }
                }

                val cellView = layoutInflater.inflate(
                    if (btn != null) R.layout.settings_cell_filled
                    else R.layout.settings_cell_empty,
                    gridContainer,
                    false
                ) as TextView

                var dragTouchX = 0
                var dragTouchY = 0

                if (btn != null) {
                    cellView.text = "${btn.text}\n[${btn.type.name}]"

                    cellView.setOnClickListener {
                        showButtonPropertiesDialog(btn)
                    }

                    cellView.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            dragTouchX = event.x.toInt()
                            dragTouchY = event.y.toInt()
                        }
                        false
                    }

                    cellView.setOnLongClickListener { view ->
                        val clip = ClipData.newPlainText("btn_id", btn.id)

                        val shadowBuilder = object : View.DragShadowBuilder(view) {
                            override fun onProvideShadowMetrics(
                                outShadowSize: Point,
                                outShadowTouchPoint: Point
                            ) {
                                outShadowSize.set(view.width, view.height)
                                outShadowTouchPoint.set(dragTouchX, dragTouchY)
                            }
                        }

                        view.startDragAndDrop(
                            clip,
                            shadowBuilder,
                            btn.id,
                            0
                        )
                        true
                    }

                } else {
                    cellView.setOnClickListener {
                        val newBtn = GridButton(
                            row = r,
                            col = c,
                            text = "",
                            url = ""
                        )

                        config.buttons.add(newBtn)
                        refreshEditorGrid()
                        showButtonPropertiesDialog(newBtn)
                    }
                }

                cellView.setOnDragListener { _, event ->
                    if (event.action == DragEvent.ACTION_DROP) {
                        val draggedId = event.clipData
                            ?.getItemAt(0)?.text?.toString()

                        val draggedBtn = config.buttons.find {
                            it.id == draggedId
                        }

                        if (draggedBtn != null) {
                            draggedBtn.row = r
                            draggedBtn.col = c
                            refreshEditorGrid()
                        }
                    }
                    true
                }

                val params = GridLayout.LayoutParams(
                    GridLayout.spec(r, rSpan, 1f),
                    GridLayout.spec(c, cSpan, 1f)
                ).apply {
                    width = 0
                    height = 0
                    setMargins(6, 6, 6, 6)
                }
                gridContainer.addView(cellView, params)
            }
        }
    }


    private fun showButtonPropertiesDialog(btn: GridButton) {

        val view = layoutInflater.inflate(
            R.layout.settings_cell_settings,
            null
        )

        val radioGroup = view.findViewById<RadioGroup>(R.id.radioGroupType)
        val rbCallUrl = view.findViewById<RadioButton>(R.id.rbCallUrl)
        val rbTextarea = view.findViewById<RadioButton>(R.id.rbTextarea)
        val rbCopy = view.findViewById<RadioButton>(R.id.rbCopy)

        when (btn.type) {
            ButtonType.CALL_URL -> radioGroup.check(rbCallUrl.id)
            ButtonType.TEXTAREA -> radioGroup.check(rbTextarea.id)
            ButtonType.COPY_CLIPBOARD -> radioGroup.check(rbCopy.id)
        }

        var selectedIconUri = btn.iconUri
        val selectedIconPreview = view.findViewById<ImageView>(R.id.selectedIconPreview)

        fun updateIconPreview(uriString: String?) {
            if (uriString.isNullOrEmpty()) {
                selectedIconPreview.visibility = View.GONE
                selectedIconPreview.setImageDrawable(null)
            } else {
                selectedIconPreview.visibility = View.VISIBLE
                selectedIconPreview.setImageURI(Uri.parse(uriString))
            }
        }

        updateIconPreview(selectedIconUri)

        val browseIconBtn = view.findViewById<Button>(R.id.browseIconBtn)
        browseIconBtn.setOnClickListener {
            pendingIconCallback = { uri ->
                selectedIconUri = uri.toString()
                updateIconPreview(selectedIconUri)
            }

            pickIconLauncher.launch(arrayOf("image/*"))
        }

        val textInput = view.findViewById<EditText>(R.id.textInput)
        textInput.setText(btn.text)
        val unicodeRow = view.findViewById<LinearLayout>(R.id.unicodeRow)
        for (i in 0 until unicodeRow.childCount) {
            val pBtn = unicodeRow.getChildAt(i) as Button
            pBtn.setOnClickListener {
                val symbol = pBtn.text.toString()
                val cursor = textInput.selectionStart.coerceAtLeast(0)
                textInput.text.insert(cursor, symbol)
            }
        }

        val urlInput = view.findViewById<EditText>(R.id.urlInput)
        urlInput.setText(btn.url)

        val hSpanInput = view.findViewById<EditText>(R.id.hSpanInput)
        val vSpanInput = view.findViewById<EditText>(R.id.vSpanInput)
        hSpanInput.setText(btn.colSpan.toString())
        vSpanInput.setText(btn.rowSpan.toString())

        AlertDialog.Builder(this)
            .setTitle("Button Properties")
            .setView(view)
            .setPositiveButton("Update") { _, _ ->

                btn.type = when (radioGroup.checkedRadioButtonId) {
                    rbTextarea.id -> ButtonType.TEXTAREA
                    rbCopy.id -> ButtonType.COPY_CLIPBOARD
                    else -> ButtonType.CALL_URL
                }

                btn.iconUri = selectedIconUri
                btn.text = textInput.text.toString()
                btn.url = urlInput.text.toString().trim()

                btn.colSpan = (
                        hSpanInput.text.toString().toIntOrNull() ?: 1
                        ).coerceIn(
                        1,
                        config.gridWidth - btn.col
                    )

                btn.rowSpan = (
                        vSpanInput.text.toString().toIntOrNull() ?: 1
                        ).coerceIn(
                        1,
                        config.gridHeight - btn.row
                    )

                refreshEditorGrid()
            }
            .setNeutralButton("Remove") { _, _ ->

                AlertDialog.Builder(this)
                    .setTitle("Remove Button")
                    .setMessage("Are you sure you want to remove this button?")
                    .setPositiveButton("Yes") { _, _ ->
                        config.buttons.remove(btn)
                        refreshEditorGrid()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

}