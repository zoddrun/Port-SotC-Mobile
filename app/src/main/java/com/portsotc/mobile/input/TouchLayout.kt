package com.portsotc.mobile.input

enum class TouchControlKind { BUTTON, DPAD, STICK }

/**
 * One on-screen control. Positions and radius are normalized against the
 * smaller display dimension so a saved layout keeps its proportions across
 * different screen sizes and aspect ratios.
 */
data class TouchControl(
    val id: String,
    val label: String,
    val kind: TouchControlKind,
    var centerX: Float,
    var centerY: Float,
    var radius: Float,
    var alpha: Float = 1f,
    var hidden: Boolean = false,
    val buttonMask: Int = 0,
    val stickLeft: Boolean = true
)

class TouchLayout(val controls: MutableList<TouchControl>) {

    fun byId(id: String): TouchControl? = controls.firstOrNull { it.id == id }

    fun resetToDefaults() {
        val defaults = defaults()
        controls.clear()
        controls.addAll(defaults.controls)
    }

    fun copyOf(): TouchLayout = TouchLayout(
        controls.map { it.copy() }.toMutableList()
    )

    companion object {
        fun defaults(): TouchLayout = TouchLayout(
            mutableListOf(
                TouchControl(
                    id = "dpad",
                    label = "D-PAD",
                    kind = TouchControlKind.DPAD,
                    centerX = 0.28f,
                    centerY = 0.78f,
                    radius = 0.078f
                ),
                TouchControl(
                    id = "lstick",
                    label = "L",
                    kind = TouchControlKind.STICK,
                    centerX = 0.11f,
                    centerY = 0.66f,
                    radius = 0.095f,
                    stickLeft = true
                ),
                TouchControl(
                    id = "rstick",
                    label = "R",
                    kind = TouchControlKind.STICK,
                    centerX = 0.89f,
                    centerY = 0.66f,
                    radius = 0.095f,
                    stickLeft = false
                ),
                TouchControl(
                    id = "cross",
                    label = "\u00D7",
                    kind = TouchControlKind.BUTTON,
                    centerX = 0.76f,
                    centerY = 0.82f,
                    radius = 0.045f,
                    buttonMask = Ps2Buttons.CROSS
                ),
                TouchControl(
                    id = "circle",
                    label = "\u25CB",
                    kind = TouchControlKind.BUTTON,
                    centerX = 0.855f,
                    centerY = 0.72f,
                    radius = 0.045f,
                    buttonMask = Ps2Buttons.CIRCLE
                ),
                TouchControl(
                    id = "square",
                    label = "\u25A1",
                    kind = TouchControlKind.BUTTON,
                    centerX = 0.665f,
                    centerY = 0.72f,
                    radius = 0.045f,
                    buttonMask = Ps2Buttons.SQUARE
                ),
                TouchControl(
                    id = "triangle",
                    label = "\u25B3",
                    kind = TouchControlKind.BUTTON,
                    centerX = 0.76f,
                    centerY = 0.62f,
                    radius = 0.045f,
                    buttonMask = Ps2Buttons.TRIANGLE
                ),
                TouchControl(
                    id = "l2",
                    label = "L2",
                    kind = TouchControlKind.BUTTON,
                    centerX = 0.07f,
                    centerY = 0.24f,
                    radius = 0.04f,
                    buttonMask = Ps2Buttons.L2
                ),
                TouchControl(
                    id = "l1",
                    label = "L1",
                    kind = TouchControlKind.BUTTON,
                    centerX = 0.07f,
                    centerY = 0.14f,
                    radius = 0.04f,
                    buttonMask = Ps2Buttons.L1
                ),
                TouchControl(
                    id = "r2",
                    label = "R2",
                    kind = TouchControlKind.BUTTON,
                    centerX = 0.93f,
                    centerY = 0.24f,
                    radius = 0.04f,
                    buttonMask = Ps2Buttons.R2
                ),
                TouchControl(
                    id = "r1",
                    label = "R1",
                    kind = TouchControlKind.BUTTON,
                    centerX = 0.93f,
                    centerY = 0.14f,
                    radius = 0.04f,
                    buttonMask = Ps2Buttons.R1
                ),
                TouchControl(
                    id = "select",
                    label = "SEL",
                    kind = TouchControlKind.BUTTON,
                    centerX = 0.43f,
                    centerY = 0.94f,
                    radius = 0.027f,
                    buttonMask = Ps2Buttons.SELECT
                ),
                TouchControl(
                    id = "start",
                    label = "STA",
                    kind = TouchControlKind.BUTTON,
                    centerX = 0.53f,
                    centerY = 0.94f,
                    radius = 0.027f,
                    buttonMask = Ps2Buttons.START
                )
            )
        )
    }
}
