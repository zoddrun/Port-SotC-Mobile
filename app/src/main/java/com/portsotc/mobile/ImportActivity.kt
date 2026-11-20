package com.portsotc.mobile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.portsotc.mobile.core.GameImporter
import com.portsotc.mobile.core.InstalledGame
import com.portsotc.mobile.disc.DiscValidation
import com.portsotc.mobile.disc.IssueSeverity
import java.util.concurrent.atomic.AtomicBoolean

class ImportActivity : Activity() {

    private lateinit var app: SotcApplication
    private lateinit var importer: GameImporter

    private lateinit var fileNameView: TextView
    private lateinit var statusView: TextView
    private lateinit var detailsView: TextView
    private lateinit var messagesView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressTextView: TextView
    private lateinit var startButton: Button
    private lateinit var cancelButton: Button
    private lateinit var chooseButton: Button

    private var selectedUri: Uri? = null
    private var sourceInfo: GameImporter.SourceInfo? = null
    private var importThread: Thread? = null
    private val cancelFlag = AtomicBoolean(false)
    private var importing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)
        app = application as SotcApplication
        importer = GameImporter(this, app.library, app.paths)

        fileNameView = findViewById(R.id.import_file_name)
        statusView = findViewById(R.id.import_status)
        detailsView = findViewById(R.id.import_details)
        messagesView = findViewById(R.id.import_messages)
        progressBar = findViewById(R.id.import_progress)
        progressTextView = findViewById(R.id.import_progress_text)
        startButton = findViewById(R.id.button_import_start)
        cancelButton = findViewById(R.id.button_import_cancel)
        chooseButton = findViewById(R.id.button_import_choose)

        fileNameView.text = "No image selected"
        statusView.text = "Choose an ISO image to inspect. The file is copied into app storage and validated."
        detailsView.text = "No disc inspected yet."
        messagesView.text = ""

        chooseButton.setOnClickListener { openDocumentPicker() }
        startButton.setOnClickListener { startImport() }
        cancelButton.setOnClickListener {
            if (importing) {
                cancelFlag.set(true)
                statusView.text = "Cancelling import…"
            } else {
                finish()
            }
        }
        updateButtons()
    }

    private fun openDocumentPicker() {
        if (importing) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, REQUEST_OPEN_DOCUMENT)
    }

    @Deprecated("Deprecated in the platform, but this project intentionally avoids AndroidX.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_OPEN_DOCUMENT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // The provider may not offer persistable grants. The URI is still
            // readable for the lifetime of this activity, which is when the copy
            // happens.
        }
        selectedUri = uri
        sourceInfo = importer.querySource(uri)
        val info = sourceInfo
        fileNameView.text = info?.displayName ?: uri.lastPathSegment ?: "selected image"
        val sizeText = info?.sizeBytes?.let { GameImporter.formatBytes(it) } ?: "unknown size"
        statusView.text = "Selected ($sizeText). Start the import to copy and validate the disc."
        detailsView.text = "Serial, boot executable and XFF modules have not been inspected yet."
        messagesView.text = ""
        progressBar.visibility = View.GONE
        progressTextView.visibility = View.GONE
        chooseButton.text = getString(R.string.import_choose)
        updateButtons()
    }

    private fun startImport() {
        val uri = selectedUri ?: return
        if (importing) return
        importing = true
        cancelFlag.set(false)
        updateButtons()
        progressBar.visibility = View.VISIBLE
        progressBar.progress = 0
        progressBar.isIndeterminate = false
        progressTextView.visibility = View.VISIBLE
        progressTextView.text = "0%"
        messagesView.text = ""
        statusView.text = getString(R.string.import_preparing)
        detailsView.text = "Reading ISO9660 volume descriptor…"

        val thread = Thread({
            val result = importer.import(uri, cancelFlag) { progress ->
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        showProgress(progress)
                    }
                }
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    finishImport(result)
                }
            }
        }, "sotc-import")
        importThread = thread
        thread.start()
    }

    private fun showProgress(progress: GameImporter.Progress) {
        statusView.text = progress.message
        val fraction = progress.fraction
        if (fraction == null) {
            progressBar.isIndeterminate = true
            progressTextView.text = GameImporter.formatBytes(progress.processedBytes)
        } else {
            progressBar.isIndeterminate = false
            progressBar.progress = (fraction * 1000f).toInt()
            progressTextView.text = buildString {
                append((fraction * 100f).toInt())
                append("% · ")
                append(GameImporter.formatBytes(progress.processedBytes))
                if (progress.totalBytes > 0) {
                    append(" / ")
                    append(GameImporter.formatBytes(progress.totalBytes))
                }
                append(" · ")
                append(progress.stage.name)
            }
        }
    }

    private fun finishImport(result: GameImporter.Result) {
        importing = false
        progressBar.isIndeterminate = false
        updateButtons()
        when (result) {
            is GameImporter.Result.Success -> showSuccess(result.game, result.validation)
            is GameImporter.Result.Failure -> showFailure(result.message, result.validation)
            GameImporter.Result.Cancelled -> {
                statusView.text = "Import cancelled"
                progressBar.visibility = View.GONE
                progressTextView.visibility = View.GONE
                messagesView.text = "No installed game data was changed. The source image was not modified."
                detailsView.text = "Import cancelled before the new installation was published."
                startButton.isEnabled = selectedUri != null
            }
        }
    }

    private fun showSuccess(game: InstalledGame, validation: DiscValidation) {
        fileNameView.text = game.title
        statusView.text = getString(R.string.import_complete)
        progressBar.progress = 1000
        progressBar.visibility = View.VISIBLE
        progressTextView.text = "100% · ${GameImporter.formatBytes(game.imageSize)}"
        detailsView.text = formatValidation(validation)
        messagesView.text = buildString {
            appendLine("Installed under ${game.directory.name}/")
            appendLine("SHA-256 ${game.sha256}")
            appendLine("Modules ${game.modules.joinToString(", ")}")
            append(
                if (com.portsotc.mobile.core.CoreProbe.isRuntimeInstalled(this@ImportActivity)) {
                    "Recompiled runtime detected. Open the main screen to prepare game data."
                } else {
                    "Game data is ready, but the recompiled runtime is missing from this build."
                }
            )
        }
        chooseButton.text = "IMPORT ANOTHER"
        startButton.isEnabled = false
        Toast.makeText(this, "Disc imported and verified", Toast.LENGTH_LONG).show()
    }

    private fun showFailure(message: String, validation: DiscValidation?) {
        statusView.text = getString(R.string.import_failed)
        progressBar.visibility = View.GONE
        progressTextView.visibility = View.GONE
        detailsView.text = validation?.let { formatValidation(it) }
            ?: "Validation did not produce a disc report."
        messagesView.text = message
        startButton.isEnabled = selectedUri != null
        Toast.makeText(this, "Import failed", Toast.LENGTH_LONG).show()
    }

    private fun formatValidation(validation: DiscValidation): String = buildString {
        appendLine("VALID      ${if (validation.valid) "YES" else "NO"}")
        appendLine("SERIAL     ${validation.serial ?: "-"}")
        appendLine("BOOT       ${validation.bootFile ?: "-"}")
        appendLine("VOLUME     ${validation.volumeLabel}")
        appendLine("IMAGE      ${GameImporter.formatBytes(validation.imageSize)} (${validation.sectorCount} sectors)")
        appendLine("BOOT ELF   ${validation.bootElf?.let { GameImporter.formatBytes(it.size) } ?: "missing"}")
        appendLine("MODULES    ${validation.modules.joinToString(", ") { it.path.substringAfterLast('/') }}")
        appendLine()
        for (issue in validation.issues) {
            val marker = when (issue.severity) {
                IssueSeverity.ERROR -> "ERROR"
                IssueSeverity.WARNING -> "WARN"
                IssueSeverity.INFO -> "INFO"
            }
            appendLine("[$marker] ${issue.code}")
            appendLine("  ${issue.message}")
        }
    }

    private fun updateButtons() {
        startButton.isEnabled = !importing && selectedUri != null
        chooseButton.isEnabled = !importing
        cancelButton.text = if (importing) {
            getString(R.string.import_cancel)
        } else {
            "CLOSE"
        }
    }

    @Deprecated("Deprecated in the platform, but this project intentionally avoids AndroidX.")
    override fun onBackPressed() {
        if (importing) {
            cancelFlag.set(true)
            statusView.text = "Cancelling import…"
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        cancelFlag.set(true)
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_OPEN_DOCUMENT = 1001
    }
}
