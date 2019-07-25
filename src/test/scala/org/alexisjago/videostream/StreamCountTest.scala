package org.alexisjago.videostream

import org.scalatest.FlatSpec
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActors, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import akka.actor.Props
import org.alexisjago.videostream.StreamCount.Get


class StreamCountTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "An Echo actor" must {

    "send back messages unchanged" in {
      val echo = system.actorOf(Props[StreamCount])
      echo ! Get
      expectMsg(0)
    }
  }
}
