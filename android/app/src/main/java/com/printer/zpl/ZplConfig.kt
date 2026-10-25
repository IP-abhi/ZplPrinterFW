package com.printer.zpl

data class ZplConfig(
    val widthDots: Int = 812,       // 4.00" at 203 DPI
    val lengthDots: Int = 1218,     // 6.00" at 203 DPI
    val darkness: Int = 30,         // 1 - 30
    val speed: Int = 2,             // 2 - 6 ips
    val shiftX: Int = 0,
    val shiftY: Int = 0,
    val ditherMode: Int = 1,        // 0 = None, 1 = FloydSteinberg, 2 = Atkinson
    val threshold: Int = 140,       // 0 - 255
    val invert: Boolean = false,
    val tracking: Int = 0,          // 0 = Continuous, 1 = WebGap, 2 = BlackMark
    val mediaType: Int = 0          // 0 = DirectThermal, 1 = ThermalTransfer
) {
    companion object {
        fun fromInches(widthIn: Float, lengthIn: Float, dpi: Int = 203): ZplConfig {
            return ZplConfig(
                widthDots = Math.round(widthIn * dpi),
                lengthDots = Math.round(lengthIn * dpi)
            )
        }

        fun fromMm(widthMm: Float, lengthMm: Float, dpi: Int = 203): ZplConfig {
            val dpmm = dpi / 25.4f
            return ZplConfig(
                widthDots = Math.round(widthMm * dpmm),
                lengthDots = Math.round(lengthMm * dpmm)
            )
        }
    }
}

