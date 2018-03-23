package picoide.server

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import org.webjars.WebJarAssetLocator

object Main {
  def main(args: Array[String]): Unit = {
    implicit val actorSystem  = ActorSystem("picoserver")
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher   = actorSystem.dispatcher

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

    Http().bindAndHandle(router, "0.0.0.0", 8080)
  }
}
