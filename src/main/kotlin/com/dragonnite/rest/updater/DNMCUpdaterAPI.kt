package com.dragonnite.rest.updater

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.http.UnauthorizedResponse
import org.apache.commons.io.FileUtils
import org.eclipse.jetty.http.HttpStatus
import java.io.File
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.logging.Logger


fun log(string: String) {
    println(string)
}

val yamlMapper: ObjectMapper by lazy {
    ObjectMapper(YAMLFactory.builder()
        .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER).build())
        .registerModule(KotlinModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}


fun main() {
    // setup
    System.setProperty("file.encoding", "UTF-8")

    val app = Javalin.create().start(8080)
    val cfgStream = ResourceManager.getResourceAsStream("config.yml")
            ?: throw IllegalStateException("config.yml not exist")
    val cfg = File("config.yml")
    if (!cfg.exists()){
        Files.copy(cfgStream, cfg.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    val auth = yamlMapper.readValue(cfg, Auth::class.java)

    ResourceManager.extractHtml("index.html")
    ResourceManager.extractHtml("login.html")
    ResourceManager.extractHtml("plugins.html")

    File("uploads").also { if (!it.exists()) it.mkdir() }

    // init
    VersionManager.version.versions

    // routing

    fun passCtx(ctx: Context): Boolean {
        val basicAuth = ctx.cookie("token")?.let {
            String(Base64.getDecoder().decode(it.toByteArray()))
        }?.split(":", limit = 2)
                ?: listOf("", "")
        return basicAuth[0] == auth.username && basicAuth[1] == auth.password
    }

    app.config.accessManager { handler, ctx, _ ->
        if (ctx.method() == "GET") {
            if (passCtx(ctx)) {
                if (ctx.path() == "/login") {
                    ctx.redirect("/")
                } else {
                    handler.handle(ctx)
                }
            } else {
                if (ctx.path() != "/") {
                    handler.handle(ctx)
                }else{
                    ctx.status(401).result("Unauthorized")
                    ctx.redirect("/login", HttpStatus.PERMANENT_REDIRECT_308)
                }

            }
        } else {
            if (ctx.path() == "/auth") {
                handler.handle(ctx)
                return@accessManager
            }
            if (passCtx(ctx)) {
                handler.handle(ctx)
            } else {
                throw UnauthorizedResponse("unauthorized")
            }
        }
    }


    // get
    app.get("/") { ctx -> ctx.html(renderHtml("index.html")) }
    app.get("/login") { ctx -> ctx.html(renderHtml("login.html")) }
    app.get("/plugins") {ctx ->
        val gui = ctx.queryParam("gui", Boolean::class.java, "false").value ?: false
        if (gui){
            ctx.html(renderHtml("plugins.html"))
        }else{
            ctx.json(VersionManager.version.versions)
        }
    }
    app.get("/plugin/:name") { ctx ->
        ctx.contentType("application/json")
        val name = ctx.pathParam("name")
        val version = VersionManager.version.versions[name]
        version?.also { ctx.json(mapOf("version" to version)) } ?: throw NotFoundResponse("cannot found plugin $name")
    }

    app.get("/logout") {ctx ->
        ctx.removeCookie("token")
        ctx.redirect("/login")
    }

    // post

    app.post("/auth") { ctx ->
        val map = parseFormData(ctx.body())
        val username = map["username"]
        val password = map["password"]
        if (auth.username == username && password == auth.password) {
            ctx.cookie("token", Base64.getEncoder().encodeToString("$username:$password".toByteArray()), maxAge = 86400)
            ctx.redirect("/")
        } else {
            ctx.redirect("/login?success=false")
        }
    }

    app.post("/upload") { ctx ->
        val files = ctx.uploadedFile("files")?.takeUnless { it.size == 0L }
        files?.also { (stream, _, _, _, ext) ->
            val tmp = File.createTempFile("tmp", ext)
            FileUtils.copyInputStreamToFile(stream, tmp)
            val pfpair = VersionManager.parse(tmp)
            if (pfpair.first == null){
                ctx.redirect("/?success=false&reason=${URLEncoder.encode(pfpair.second.reason, "UTF-8")}")
            }else{
                val pf = pfpair.first!!
                log("plugin name: ${pf.name}")
                log("plugin version: ${pf.version}")
                VersionManager.version.versions[pf.name] = pf.version
                VersionManager.saveVersions()
                ctx.redirect("/?success=true")
            }
        } ?: run {
            ctx.redirect("/?success=false&reason")
        }
    }

}

private fun renderHtml(path: String, folder: File = File("public")): String {
    val html = File(folder, "/$path")
    if (!html.exists()) return "<p>404 Not Found</p>";
    return Files.readAllLines(html.toPath(), Charsets.UTF_8).joinToString("\n")
}


data class PluginFile(
        val name: String,
        val version: String
)

data class Auth(
        val username: String,
        val password: String
)

private fun parseFormData(form: String): Map<String, String> {
    val str = URLDecoder.decode(form, "UTF-8")
    if (str.isEmpty()) return mapOf()
    val map: MutableMap<String, String> = HashMap()
    val arr = str.split("&")
    for (s in arr) {
        val a = s.split("=")
        if (a.size != 2) continue
        map[a[0]] = a[1]
    }
    return map
}
