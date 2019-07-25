package org.alexisjago.videostream

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import org.alexisjago.videostream.StreamCount.{Decrement, EntityEnvelope, Get, Increment, NewStream}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.duration._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._

object WebServer extends App {

  implicit val system = ActorSystem("ClusterSystem")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val timeout = Timeout(5.seconds) // needed for `?` below


  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(id, payload) => (id.toString, payload)
  }

  val numberOfShards = 100

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(id, _) => (id % numberOfShards).toString
    case ShardRegion.StartEntity(id) =>
      // StartEntity is used by remembering entities feature
      (id.toLong % numberOfShards).toString
  }

  val streamRegion: ActorRef = ClusterSharding(system).start(
    typeName = "Counter",
    entityProps = Props[StreamCount],
    settings = ClusterShardingSettings(system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)

  case class ResponseCountBody(numberOfStreams: Int)

  case class ResponseNewStream(id: Int)

  implicit val itemFormat = jsonFormat1(ResponseCountBody)
  implicit val itemFormat2 = jsonFormat1(ResponseNewStream)

  val route =
    get {
      pathPrefix("stream" / LongNumber) { id =>
        val p = (streamRegion ? EntityEnvelope(id, Get)).mapTo[Int]
        onSuccess(p) { i => complete(ResponseCountBody(i)) }
      }
    } ~
      post {
        pathPrefix("stream" / LongNumber) { id =>
          val p = (streamRegion ? EntityEnvelope(id, Increment)).mapTo[NewStream]
          onSuccess(p) {
            case NewStream(i) => complete(ResponseNewStream(i))
          }
        }
      }   ~
      delete {
        pathPrefix("stream" / LongNumber) { id =>
          val p = streamRegion ? EntityEnvelope(id, Decrement)
          onComplete(p) { _ =>
            complete(StatusCodes.NoContent)
          }
        }
      }

  val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)
}