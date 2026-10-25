package com.printer.zpl.transport

interface IPrinterTransport {
    val endpointName: String
    val isOpen: Boolean

    suspend fun open(): Boolean
    fun close()
    suspend fun write(data: ByteArray): Boolean
    suspend fun read(maxBytes: Int = 1024, timeoutMs: Int = 1000): ByteArray
}

