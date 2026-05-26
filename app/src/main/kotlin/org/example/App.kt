package org.example

import io.github.cdimascio.dotenv.dotenv
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

val dotenv = dotenv()

val BOT_TOKEN = dotenv["TOKEN_TESTE"]
val BASE_URL = "https://api.telegram.org/bot$BOT_TOKEN"

val client = HttpClient.newHttpClient()


fun get(endpoint: String): String {
    val request = HttpRequest.newBuilder()
        .uri(URI.create("$BASE_URL/$endpoint"))
        .GET()
        .build()
    return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
}

fun post(endpoint: String, body: String): String {
    val request = HttpRequest.newBuilder()
        .uri(URI.create("$BASE_URL/$endpoint"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
    return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
}



fun extrairLong(json: String, campo: String): Long? =
    Regex(""""$campo"\s*:\s*(-?\d+)""").find(json)?.groupValues?.get(1)?.toLongOrNull()

fun extrairString(json: String, campo: String): String? =
    Regex(""""$campo"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(json)?.groupValues?.get(1)

fun extrairObjetos(json: String, campo: String): List<String> {
    val inicio = json.indexOf(""""$campo"""")
    if (inicio == -1) return emptyList()

    val arrayStart = json.indexOf('[', inicio)
    if (arrayStart == -1) return emptyList()

    val objetos = mutableListOf<String>()
    var depth = 0
    var objStart = -1
    var i = arrayStart

    while (i < json.length) {
        when (json[i]) {
            '[', '{' -> {
                if (json[i] == '{' && depth == 1) objStart = i
                depth++
            }
            '}', ']' -> {
                depth--
                if (json[i] == '}' && depth == 1 && objStart != -1) {
                    objetos.add(json.substring(objStart, i + 1))
                    objStart = -1
                }
                if (depth == 0) return objetos
            }
        }
        i++
    }
    return objetos
}


fun sendMessage(chatId: Long, text: String) {
    val safeText = text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    post("sendMessage", """{"chat_id":$chatId,"text":"$safeText"}""")
}

fun getUpdates(offset: Long): String =
    get("getUpdates?offset=$offset&timeout=30")



fun limparCpf(cpf: String): String =
    cpf.replace(Regex("[^0-9]"), "")

fun validarCpf(cpf: String): Boolean {
    val n = limparCpf(cpf)
    if (n.length != 11) return false
    if (n.all { it == n[0] }) return false

    val d1 = ((0..8).sumOf { n[it].digitToInt() * (10 - it) } * 10 % 11) % 10
    if (d1 != n[9].digitToInt()) return false

    val d2 = ((0..9).sumOf { n[it].digitToInt() * (11 - it) } * 10 % 11) % 10
    return d2 == n[10].digitToInt()
}

fun formatarCpf(cpf: String): String {
    val n = limparCpf(cpf)
    return if (n.length == 11)
        "${n.substring(0, 3)}.${n.substring(3, 6)}.${n.substring(6, 9)}-${n.substring(9)}"
    else cpf
}

fun respostaCpf(cpfRaw: String): String {
    val fmt = formatarCpf(cpfRaw)
    return if (validarCpf(cpfRaw))
        "CPF $fmt é VÁLIDO ✅."
    else
        "CPF $fmt é INVÁLIDO ❌."
}

fun processarMensagem(msgJson: String) {
    val chatId = extrairLong(msgJson, "id") ?: return
    val texto = extrairString(msgJson, "text") ?: return

    when {
        texto.startsWith("/start") -> sendMessage(
            chatId,
            "Olá! Sou o bot validador de CPF.\n\nEnvie um CPF diretamente ou use:\n/validar 000.000.000-00"
        )

        texto.startsWith("/validar") -> {
            val cpfRaw = texto.removePrefix("/validar").trim()
            if (cpfRaw.isBlank()) {
                sendMessage(chatId, "⚠️ Uso: /validar 000.000.000-00")
            } else {
                sendMessage(chatId, respostaCpf(cpfRaw))
            }
        }

        limparCpf(texto).length == 11 -> sendMessage(chatId, respostaCpf(texto))

        else -> sendMessage(chatId, "❓ Envie um CPF (com ou sem formatação) ou use /validar 000.000.000-00")
    }
}

fun main() {
    println("Bot rodando... (Ctrl+C para parar)")
    var offset = 0L

    while (true) {
        try {
            val response = getUpdates(offset)
            val updates = extrairObjetos(response, "result")

            for (update in updates) {
                val updateId = extrairLong(update, "update_id") ?: continue
                val messageJson = extrairObjetos(update, "message").firstOrNull()
                    ?: extrairObjetos(update, "message").firstOrNull()

                // Extrai o bloco "message" manualmente
                val msgStart = update.indexOf(""""message"""")
                if (msgStart != -1) {
                    val braceStart = update.indexOf('{', msgStart)
                    if (braceStart != -1) {
                        var depth = 0
                        var end = braceStart
                        for (i in braceStart until update.length) {
                            when (update[i]) {
                                '{' -> depth++
                                '}' -> { depth--; if (depth == 0) { end = i; break } }
                            }
                        }
                        val msgBlock = update.substring(braceStart, end + 1)

                        // Extrai o chat id de dentro do bloco "chat"
                        val chatStart = msgBlock.indexOf(""""chat"""")
                        val chatId = if (chatStart != -1) {
                            val chatBrace = msgBlock.indexOf('{', chatStart)
                            val chatBlock = msgBlock.substring(chatBrace)
                            extrairLong(chatBlock, "id")
                        } else null

                        if (chatId != null) {
                            val msgComChat = msgBlock.replace(
                                Regex(""""chat"\s*:\s*\{[^}]*\}"""),
                                """"id":$chatId"""
                            )
                            processarMensagem(msgComChat)
                        }
                    }
                }
                offset = updateId + 1
            }
        } catch (e: Exception) {
            println("Erro: ${e.message}")
            Thread.sleep(3000)
        }
    }
}