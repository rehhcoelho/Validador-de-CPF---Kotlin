package org.example

import io.github.cdimascio.dotenv.dotenv
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

// Carrega variáveis de ambiente do arquivo .env
val dotenv = dotenv()

// Token de autenticação do bot no Telegram
val BOT_TOKEN = dotenv["TOKEN_TESTE"]

// URL base da API do Telegram, já com o token embutido
val BASE_URL = "https://api.telegram.org/bot$BOT_TOKEN"

// Cliente HTTP reutilizável para todas as requisições
val client = HttpClient.newHttpClient()


// ─── FUNÇÕES HTTP ───────────────────────────────────────────────────────────

// Faz uma requisição GET para o endpoint informado e retorna o corpo da resposta
fun get(endpoint: String): String {
    val request = HttpRequest.newBuilder()
        .uri(URI.create("$BASE_URL/$endpoint"))
        .GET()
        .build()
    return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
}

// Faz uma requisição POST com um corpo JSON e retorna a resposta
fun post(endpoint: String, body: String): String {
    val request = HttpRequest.newBuilder()
        .uri(URI.create("$BASE_URL/$endpoint"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build()
    return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
}


// ─── FUNÇÕES DE PARSING JSON (sem biblioteca externa) ───────────────────────

// Extrai um valor numérico Long de um campo JSON pelo nome
fun extrairLong(json: String, campo: String): Long? =
    Regex(""""$campo"\s*:\s*(-?\d+)""").find(json)?.groupValues?.get(1)?.toLongOrNull()

// Extrai um valor de texto (String) de um campo JSON pelo nome
fun extrairString(json: String, campo: String): String? =
    Regex(""""$campo"\s*:\s*"((?:[^"\\]|\\.)*)"""").find(json)?.groupValues?.get(1)

// Extrai uma lista de objetos JSON contidos em um array identificado pelo campo
fun extrairObjetos(json: String, campo: String): List<String> {
    val inicio = json.indexOf(""""$campo"""")
    if (inicio == -1) return emptyList()

    val arrayStart = json.indexOf('[', inicio)
    if (arrayStart == -1) return emptyList()

    val objetos = mutableListOf<String>()
    var depth = 0       // Controla o nível de aninhamento dos colchetes/chaves
    var objStart = -1   // Marca onde começa cada objeto { ... }
    var i = arrayStart

    while (i < json.length) {
        when (json[i]) {
            '[', '{' -> {
                // Ao encontrar '{' no primeiro nível do array, marca o início do objeto
                if (json[i] == '{' && depth == 1) objStart = i
                depth++
            }
            '}', ']' -> {
                depth--
                // Ao fechar '}' no primeiro nível, o objeto está completo — adiciona à lista
                if (json[i] == '}' && depth == 1 && objStart != -1) {
                    objetos.add(json.substring(objStart, i + 1))
                    objStart = -1
                }
                // Fechou o array principal — retorna o que foi coletado
                if (depth == 0) return objetos
            }
        }
        i++
    }
    return objetos
}


// ─── FUNÇÕES DE COMUNICAÇÃO COM O TELEGRAM ──────────────────────────────────

// Envia uma mensagem de texto para um chat específico pelo seu ID
fun sendMessage(chatId: Long, text: String) {
    // Escapa caracteres especiais para não quebrar o JSON
    val safeText = text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
    post("sendMessage", """{"chat_id":$chatId,"text":"$safeText"}""")
}

// Busca as atualizações (mensagens recebidas) a partir de um offset
// O offset evita reprocessar mensagens já tratadas
fun getUpdates(offset: Long): String =
    get("getUpdates?offset=$offset&timeout=30")


// ─── FUNÇÕES DE VALIDAÇÃO DE CPF ────────────────────────────────────────────

// Remove tudo que não for dígito (pontos, traços, espaços)
fun limparCpf(cpf: String): String =
    cpf.replace(Regex("[^0-9]"), "")

// Valida o CPF pelo algoritmo oficial dos dois dígitos verificadores
fun validarCpf(cpf: String): Boolean {
    val n = limparCpf(cpf)
    if (n.length != 11) return false           // Deve ter exatamente 11 dígitos
    if (n.all { it == n[0] }) return false     // Rejeita sequências como "111.111.111-11"

    // Calcula o 1º dígito verificador
    val d1 = ((0..8).sumOf { n[it].digitToInt() * (10 - it) } * 10 % 11) % 10
    if (d1 != n[9].digitToInt()) return false

    // Calcula o 2º dígito verificador
    val d2 = ((0..9).sumOf { n[it].digitToInt() * (11 - it) } * 10 % 11) % 10
    return d2 == n[10].digitToInt()
}

// Formata o CPF limpo no padrão 000.000.000-00
fun formatarCpf(cpf: String): String {
    val n = limparCpf(cpf)
    return if (n.length == 11)
        "${n.substring(0, 3)}.${n.substring(3, 6)}.${n.substring(6, 9)}-${n.substring(9)}"
    else cpf
}

// Retorna a mensagem de resultado da validação para o usuário
fun respostaCpf(cpfRaw: String): String {
    val fmt = formatarCpf(cpfRaw)
    return if (validarCpf(cpfRaw))
        "CPF $fmt é VÁLIDO."
    else
        "CPF $fmt é INVÁLIDO."
}


// ─── PROCESSAMENTO DE MENSAGENS ─────────────────────────────────────────────

// Interpreta o conteúdo de uma mensagem recebida e responde de acordo com o comando
fun processarMensagem(msgJson: String) {
    val chatId = extrairLong(msgJson, "id") ?: return   // Sem chat ID, ignora
    val texto = extrairString(msgJson, "text") ?: return // Sem texto, ignora

    when {
        // Comando /start — exibe mensagem de boas-vindas
        texto.startsWith("/start") -> sendMessage(
            chatId,
            "Olá! Sou o bot validador de CPF.\n\nEnvie um CPF diretamente ou use:\n/validar 000.000.000-00"
        )

        // Comando /validar <cpf> — valida o CPF passado como argumento
        texto.startsWith("/validar") -> {
            val cpfRaw = texto.removePrefix("/validar").trim()
            if (cpfRaw.isBlank()) {
                sendMessage(chatId, "Uso: /validar 000.000.000-00")
            } else {
                sendMessage(chatId, respostaCpf(cpfRaw))
            }
        }

        // Mensagem livre com 11 dígitos — trata como CPF direto
        limparCpf(texto).length == 11 -> sendMessage(chatId, respostaCpf(texto))

        // Qualquer outra mensagem — orienta o usuário
        else -> sendMessage(chatId, "Envie um CPF (com ou sem formatação) ou use /validar 000.000.000-00")
    }
}


// ─── LOOP PRINCIPAL ─────────────────────────────────────────────────────────

fun main() {
    println("Bot rodando... (Ctrl+C para parar)")
    var offset = 0L  // Controla qual foi a última mensagem processada

    while (true) {
        try {
            val response = getUpdates(offset)
            val updates = extrairObjetos(response, "result")

            for (update in updates) {
                val updateId = extrairLong(update, "update_id") ?: continue

                // Extrai manualmente o bloco "message" do JSON do update
                val msgStart = update.indexOf(""""message"""")
                if (msgStart != -1) {
                    val braceStart = update.indexOf('{', msgStart)
                    if (braceStart != -1) {
                        // Percorre o JSON contando chaves para encontrar o fim do objeto
                        var depth = 0
                        var end = braceStart
                        for (i in braceStart until update.length) {
                            when (update[i]) {
                                '{' -> depth++
                                '}' -> { depth--; if (depth == 0) { end = i; break } }
                            }
                        }
                        val msgBlock = update.substring(braceStart, end + 1)

                        // Extrai o chat_id de dentro do bloco "chat"
                        val chatStart = msgBlock.indexOf(""""chat"""")
                        val chatId = if (chatStart != -1) {
                            val chatBrace = msgBlock.indexOf('{', chatStart)
                            val chatBlock = msgBlock.substring(chatBrace)
                            extrairLong(chatBlock, "id")
                        } else null

                        if (chatId != null) {
                            // Substitui o objeto "chat" inteiro pelo campo "id" simples
                            // para que processarMensagem consiga extrair o ID corretamente
                            val msgComChat = msgBlock.replace(
                                Regex(""""chat"\s*:\s*\{[^}]*\}"""),
                                """"id":$chatId"""
                            )
                            processarMensagem(msgComChat)
                        }
                    }
                }

                // Avança o offset para não reprocessar este update
                offset = updateId + 1
            }
        } catch (e: Exception) {
            println("Erro: ${e.message}")
            Thread.sleep(3000) // Aguarda 3 segundos antes de tentar novamente
        }
    }
}