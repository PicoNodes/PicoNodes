package picoide.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import org.webjars.WebJarAssetLocator
import scala.concurrent.{ExecutionContext, Future}

object IDEServer {
  def start()(implicit actorSystem: ActorSystem,
              materializer: Materializer,
              executionContext: ExecutionContext): Future[ServerBinding] = {
    val webJarLocator = new WebJarAssetLocator()

    val router =
      path("") {
        get {
          getFromResource("index.html")
        }
      } ~ path("public" / Segment / Remaining) { (webJar, asset) =>
        get {
          Option(webJarLocator.getFullPathExact(webJar, asset))
            .map(getFromResource(_))
            .getOrElse(reject)
        }
      }

    val binding = Http().bindAndHandle(router, "0.0.0.0", 8080)
    binding.foreach(
      binding =>
        actorSystem.log.info("IDE server listening on http:/{}",
                             binding.localAddress))
    binding
  }
}
