package com.hypernite.rest.updater

import java.io.*
import java.net.URL
import java.util.logging.Logger

object ResourceManager {

    fun getResource(path: String): URL? = this::class.java.classLoader.getResource(path)

    fun getResourceAsStream(path: String): InputStream? = this::class.java.classLoader.getResourceAsStream(path)

    fun extractHtml(path: String) {
        val stream = this::class.java.classLoader.getResourceAsStream(path) ?: let {
            Logger.getGlobal().warning("$path not exist, skip saving")
            return
        }
        val folder = File("public").also { it.mkdirs() }
        val outFile = File(folder, path)
        if (outFile.exists()) return
        val reader = InputStreamReader(stream, Charsets.UTF_8)
        val writer = FileWriter(outFile)
        reader.readLines().forEach { writer.write("$it\n") }
        writer.close()
        reader.close()
    }
}