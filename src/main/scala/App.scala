import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}

object App {
  def main(args: Array[String]): Unit = {
    val uri = new URI("https://google.ca")

    val client = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder(uri).build()
    val response = client.send(request, BodyHandlers.ofString())
    println(Console.YELLOW + response.body() + Console.RESET)
  }
}
