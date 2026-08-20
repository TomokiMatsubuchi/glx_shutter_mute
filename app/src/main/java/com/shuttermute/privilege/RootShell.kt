package com.shuttermute.privilege

import java.io.File

internal object RootShell {

    fun isAvailable(): Boolean {
        val markers = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/bin/.ext/su",
        )
        return markers.any { File(it).canExecute() || File(it).exists() }
    }

    fun exec(command: String): String? = runCatching {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { reader ->
            val output = reader.readText()
            val code = process.waitFor()
            output.takeIf { code == 0 }
        }
    }.getOrNull()
}
