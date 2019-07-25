package org.alexisjago.videostream

import akka.actor.{ActorRef, ReceiveTimeout}
import akka.actor.SupervisorStrategy.Stop
import akka.persistence.PersistentActor
import org.alexisjago.videostream.StreamCount.{CounterChanged, Decrement, Get, Increment, NewStream}

import scala.concurrent.duration._
import scala.util.Success

object StreamCount {
  case class Get()
  case class Increment()
  case class Decrement()
  case class CounterChanged(delta: Int)
  case class EntityEnvelope(id: Long, payload: Any)
  case class NewStream(id: Int)
}

class StreamCount extends PersistentActor {
  import akka.cluster.sharding.ShardRegion.Passivate

  context.setReceiveTimeout(120.seconds)

  override def persistenceId: String = "Stream-" + self.path.name

  var count = 0

  def updateState(event: CounterChanged, sender: Option[ActorRef] = None ): Unit = {
    count += event.delta
    sender.foreach( _ ! NewStream(count))
  }

  override def receiveRecover: Receive = {
    case evt: CounterChanged => updateState(evt)
  }

  override def receiveCommand: Receive = {
    case Increment      => persist(CounterChanged(+1))(updateState(_, Some(sender)))
    case Decrement      => persist(CounterChanged(-1))(updateState(_, Some(sender)))
    case Get            => sender() ! count
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = Stop)
    case Stop           => context.stop(self)
  }
}