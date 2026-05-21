package org.example

import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

class MeuBot : TelegramLongPollingBot() {
    fun validarCPF(cpf: String): Boolean {

    val numeros = cpf.filter { it.isDigit() }

    if (numeros.length != 11) return false

    if (numeros.all { it == numeros[0] }) return false

    var soma = 0

    for (i in 0..8) {
        soma += numeros[i].digitToInt() * (10 - i)
    }

    var resto = soma % 11
    val dig1 = if (resto < 2) 0 else 11 - resto

    soma = 0

    for (i in 0..9) {
        soma += numeros[i].digitToInt() * (11 - i)
    }

    resto = soma % 11
    val dig2 = if (resto < 2) 0 else 11 - resto

    return dig1 == numeros[9].digitToInt() &&
           dig2 == numeros[10].digitToInt()
}

    override fun getBotUsername(): String {
        return "CpfValidatorKotlinBot"
    }

    override fun getBotToken(): String {
        return "8938753749:AAHAR_yuqCIMpQ7EnStabwgQYRiJr9eOcQk"
    }

    override fun onUpdateReceived(update: Update) {

        println("UPDATE RECEBIDO!")

        if (update.hasMessage() && update.message.hasText()) {

            val texto = update.message.text
            val chatId = update.message.chatId.toString()

            println("Mensagem: $texto")

            val resposta = SendMessage()

            resposta.chatId = chatId
            resposta.text = if (validarCPF(texto)) {
    "CPF válido ✅"
} else {
    "CPF inválido ❌"
}

            execute(resposta)
        }
    }
}

fun main() {

    println("INICIANDO BOT...")

    try {

        val botsApi = TelegramBotsApi(DefaultBotSession::class.java)

        botsApi.registerBot(MeuBot())

        println("BOT ONLINE!")

        while (true) {
            Thread.sleep(1000)
        }

    } catch (e: Exception) {

        println("ERRO NO BOT:")
        e.printStackTrace()
    }
}
