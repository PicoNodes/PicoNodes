package picoide.server

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object Main {
  def main(args: Array[String]): Unit = {
    implicit val actorSystem  = ActorSystem("picoserver")
    implicit val materializer = ActorMaterializer()
    implicit val dispatcher   = actorSystem.dispatcher

    IDEServer.start()
    NodeServer.start()
  }
}
