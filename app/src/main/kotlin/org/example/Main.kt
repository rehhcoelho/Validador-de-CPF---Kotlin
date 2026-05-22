import io.github.cdimascio.dotenv.dotenv

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

const dotenv = dotenv()

const val BOT_TOKEN = dotenv["TOKEN_TESTE"]
const val BASE_URL = "https://api.telegram.org/bot$BOT_TOKEN"

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