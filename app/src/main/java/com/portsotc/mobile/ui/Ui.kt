package com.portsotc.mobile.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.portsotc.mobile.R

/** Small programmatic UI toolkit so screens stay consistent without AndroidX. */
object Ui {

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density).toInt()

    fun title(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(context.getColor(R.color.parchment))
        textSize = 24f
        typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD)
        letterSpacing = 0.04f
        setPadding(0, dp(context, 8f), 0, dp(context, 4f))
    }

    fun section(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(context.getColor(R.color.bone_gold))
        textSize = 11f
        letterSpacing = 0.18f
        setPadding(0, dp(context, 22f), 0, dp(context, 8f))
    }

    fun body(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(context.getColor(R.color.ash))
        textSize = 14f
        setLineSpacing(dp(context, 3f).toFloat(), 1f)
    }

    fun caption(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(context.getColor(R.color.muted))
        textSize = 11f
        setLineSpacing(dp(context, 3f).toFloat(), 1f)
    }

    fun mono(context: Context, text: String = ""): TextView = TextView(context).apply {
        this.text = text
        setTextColor(context.getColor(R.color.ash))
        textSize = 11f
        typeface = android.graphics.Typeface.MONOSPACE
        setLineSpacing(dp(context, 3f).toFloat(), 1f)
    }

    fun card(context: Context): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = context.getDrawable(R.drawable.bg_card)
        val padding = dp(context, 16f)
        setPadding(padding, padding, padding, padding)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(context, 10f)
        }
    }

    fun primaryButton(context: Context, text: String): Button = Button(context).apply {
        this.text = text
        background = context.getDrawable(R.drawable.bg_button_primary)
        setTextColor(context.getColor(R.color.void_black))
        textSize = 13f
        letterSpacing = 0.1f
        isAllCaps = true
        minHeight = dp(context, 52f)
        stateListAnimator = null
    }

    fun secondaryButton(context: Context, text: String): Button = Button(context).apply {
        this.text = text
        background = context.getDrawable(R.drawable.bg_button_secondary)
        setTextColor(context.getColor(R.color.parchment))
        textSize = 13f
        letterSpacing = 0.1f
        isAllCaps = true
        minHeight = dp(context, 50f)
        stateListAnimator = null
    }

    fun spacer(context: Context, heightDp: Float): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(context, heightDp))
    }

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(context.getColor(R.color.stone_edge))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(context, 1f)
        ).apply {
            topMargin = dp(context, 16f)
            bottomMargin = dp(context, 8f)
        }
    }

    fun addToggle(
        parent: LinearLayout,
        context: Context,
        label: String,
        description: String?,
        initial: Boolean,
        onChange: (Boolean) -> Unit
    ): Switch {
        val toggle = Switch(context).apply {
            text = label
            setTextColor(context.getColor(R.color.parchment))
            textSize = 15f
            isChecked = initial
            setPadding(0, dp(context, 6f), 0, dp(context, 6f))
            setOnCheckedChangeListener { _, checked -> onChange(checked) }
        }
        parent.addView(toggle)
        if (description != null) {
            parent.addView(caption(context, description))
        }
        return toggle
    }

    fun addSlider(
        parent: LinearLayout,
        context: Context,
        label: String,
        min: Int,
        max: Int,
        initial: Int,
        formatter: (Int) -> String,
        onChange: (Int) -> Unit
    ): SeekBar {
        val labelView = TextView(context).apply {
            text = "$label · ${formatter(initial)}"
            setTextColor(context.getColor(R.color.parchment))
            textSize = 14f
            setPadding(0, dp(context, 10f), 0, dp(context, 2f))
        }
        val seekBar = SeekBar(context).apply {
            this.max = (max - min).coerceAtLeast(1)
            progress = (initial - min).coerceIn(0, this.max)
            setPadding(0, 0, 0, dp(context, 4f))
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                val value = min + progress
                labelView.text = "$label · ${formatter(value)}"
                if (fromUser) onChange(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        parent.addView(labelView)
        parent.addView(seekBar)
        return seekBar
    }

    fun addButtonRow(parent: LinearLayout, context: Context, vararg buttons: Button) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 12f)
            }
        }
        buttons.forEachIndexed { index, button ->
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (index > 0) params.marginStart = dp(context, 8f)
            row.addView(button, params)
        }
        parent.addView(row)
    }
}
