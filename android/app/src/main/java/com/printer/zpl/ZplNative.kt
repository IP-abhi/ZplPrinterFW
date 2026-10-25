package com.printer.zpl

import android.graphics.Bitmap

object ZplNative {
    init {
        System.loadLibrary("zplnative")
    }

    external fun encodeBitmapNative(bitmap: Bitmap, config: ZplConfig): ByteArray?

    external fun generateRulerNative(config: ZplConfig): ByteArray

    external fun generateBandsNative(config: ZplConfig): ByteArray

    external fun generateTestLabelNative(config: ZplConfig): ByteArray

    external fun queryIdentityNative(): ByteArray

    external fun calibrateSensorNative(): ByteArray
}

