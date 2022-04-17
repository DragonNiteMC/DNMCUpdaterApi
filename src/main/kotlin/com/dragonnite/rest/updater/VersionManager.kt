package com.dragonnite.rest.updater

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import java.io.File
import java.net.URL
import java.net.URLClassLoader

object VersionManager {

    private val jsonMapper: ObjectMapper by lazy {
        ObjectMapper()
                .registerModule(KotlinModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    enum class REASON(val reason: String) { NOT_EXIST("檔案為空"), NOT_JAR("檔案不是 .jar"), NO_PLUGIN_YML("檔案沒有包含 plugin.yml 或 bungee.yml"), SUCCESS("上傳成功") }

    fun parse(jar: File): Pair<PluginFile?, REASON> {
        if (!jar.exists()) return null to REASON.NOT_EXIST
        if (jar.extension != "jar") return null to REASON.NOT_JAR
        val child = URLClassLoader(Array<URL>(1) { jar.toURI().toURL() }, this::class.java.classLoader)
        val stream = child.getResourceAsStream("plugin.yml") ?: let { child.getResourceAsStream("bungee.yml") } ?: return null to REASON.NO_PLUGIN_YML
        val pf = yamlMapper.readValue(stream, PluginFile::class.java)
        return pf to REASON.SUCCESS
    }

    private val versionJson = File("uploads/version.json")

    data class Versions(val versions: MutableMap<String, String>)

    val version: Versions by lazy { getVersions() }

    private fun getVersions(): Versions {
        if (!versionJson.exists()) {
            return Versions(mutableMapOf()).also { jsonMapper.writeValue(versionJson, it) }
        }
        return jsonMapper.readValue(versionJson, Versions::class.java)
    }

    fun saveVersions() {
        jsonMapper.writeValue(versionJson, version)
    }
}