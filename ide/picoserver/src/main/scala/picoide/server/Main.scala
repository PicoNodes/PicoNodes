package picoide.server

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import picoide.server.model.{
  DownloaderRegistry,
  DownloaderStore,
  SourceFileManager
}
import picoide.server.model.PgProfile.api._
import picoide.server.net.IDEServer

object Main {
  def main(args: Array[String]): Unit = {
    implicit val database = Database.forConfig("db")

    implicit val actorSystem  = ActorSystem("picoserver")
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher   = actorSystem.dispatcher

    val downloaderStore = new DownloaderStore()
    val fileManager     = new SourceFileManager()

    val downloaderRegistry =
      actorSystem.actorOf(DownloaderRegistry.props(downloaderStore))
    IDEServer.start(downloaderRegistry, fileManager)
  }
}
