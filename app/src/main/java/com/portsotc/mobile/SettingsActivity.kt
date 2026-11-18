package com.portsotc.mobile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import com.portsotc.mobile.core.CoreProbe
import com.portsotc.mobile.core.Settings
import com.portsotc.mobile.ui.Ui

/** Settings index. Each category opens its own screen. */
class SettingsActivity : Activity() {

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        container = findViewById(R.id.settings_container)
        buildUi()
    }

    private fun buildUi() {
        container.removeAllViews()
        container.addView(Ui.title(this, getString(R.string.settings_title)))
        container.addView(
            Ui.caption(
                this,
                "Settings apply immediately and persist across sessions."
            )
        )

        for (category in SettingsCategory.entries) {
            container.addView(
                Ui.categoryRow(this, category.title, category.summary) {
                    startActivity(SettingsCategoryActivity.intent(this, category))
                }
            )
        }

        container.addView(Ui.section(this, "BUILD"))
        val build = Ui.card(this)
        build.addView(Ui.mono(this, CoreProbe.buildSummary(this)))
        build.addView(
            Ui.caption(
                this,
                "Shadow of the Colossus Mobile. This application contains no game data, " +
                    "BIOS files, or copyrighted assets."
            )
        )
        container.addView(build)
    }
}
