package aia.state

import akka.actor.FSM.{CurrentState, SubscribeTransitionCallBack, Transition}
import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.concurrent.duration._

class InventoryTest extends TestKit(ActorSystem("InventoryTest"))
  with WordSpecLike with BeforeAndAfterAll with MustMatchers
  with ImplicitSender {

  override def afterAll(): Unit = {
    system.terminate()
  }

  "Inventory" must {
    "follow the flow" in {
      val publisher = system.actorOf(Props(new Publisher(2, 2)))
      val inventory = system.actorOf(Props(new Inventory(publisher)))
      val stateProbe = TestProbe()
      val replyProbe = TestProbe()

      inventory ! new SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsg(
        new CurrentState(inventory, WaitForRequests))

      //start test
      inventory ! new BookRequest("context1", replyProbe.ref)
      stateProbe.expectMsg(
        new Transition(inventory, WaitForRequests, WaitForPublisher))
      stateProbe.expectMsg(
        new Transition(inventory, WaitForPublisher, ProcessRequest))
      stateProbe.expectMsg(
        new Transition(inventory, ProcessRequest, WaitForRequests))
      replyProbe.expectMsg(
        new BookReply("context1", Right(1)))

      inventory ! new BookRequest("context2", replyProbe.ref)
      stateProbe.expectMsg(
        new Transition(inventory, WaitForRequests, ProcessRequest))
      stateProbe.expectMsg(
        new Transition(inventory, ProcessRequest, WaitForRequests))
      replyProbe.expectMsg(new BookReply("context2", Right(2)))

      inventory ! new BookRequest("context3", replyProbe.ref)
      stateProbe.expectMsg(
        new Transition(inventory, WaitForRequests, WaitForPublisher))
      stateProbe.expectMsg(
        new Transition(inventory, WaitForPublisher, ProcessSoldOut))
      replyProbe.expectMsg(
        new BookReply("context3", Left("SoldOut")))
      stateProbe.expectMsg(
        new Transition(inventory, ProcessSoldOut, SoldOut))

      inventory ! new BookRequest("context4", replyProbe.ref)
      stateProbe.expectMsg(
        new Transition(inventory, SoldOut, ProcessSoldOut))
      replyProbe.expectMsg(new BookReply("context4", Left("SoldOut")))
      stateProbe.expectMsg(
        new Transition(inventory, ProcessSoldOut, SoldOut))
      system.stop(inventory)
      system.stop(publisher)
    }
    "process multiple requests" in {
      val publisher = system.actorOf(Props(new Publisher(2, 2)))
      val inventory = system.actorOf(Props(new Inventory(publisher)))
      val stateProbe = TestProbe()
      val replyProbe = TestProbe()

      inventory ! new SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsg(
        new CurrentState(inventory, WaitForRequests))

      //start test
      inventory ! new BookRequest("context1", replyProbe.ref)
      inventory ! new BookRequest("context2", replyProbe.ref)
      stateProbe.expectMsg(
        new Transition(inventory, WaitForRequests, WaitForPublisher))
      stateProbe.expectMsg(
        new Transition(inventory, WaitForPublisher, ProcessRequest))
      stateProbe.expectMsg(
        new Transition(inventory, ProcessRequest, WaitForRequests))
      replyProbe.expectMsg(new BookReply("context1", Right(1)))
      stateProbe.expectMsg(
        new Transition(inventory, WaitForRequests, ProcessRequest))
      stateProbe.expectMsg(
        new Transition(inventory, ProcessRequest, WaitForRequests))
      replyProbe.expectMsg(new BookReply("context2", Right(2)))

      system.stop(inventory)
      system.stop(publisher)
    }
    "support multiple supplies" in {
      val publisher = system.actorOf(Props(new Publisher(4, 2)))
      val inventory = system.actorOf(Props(new Inventory(publisher)))
      val stateProbe = TestProbe()
      val replyProbe = TestProbe()

      inventory ! new SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsg(
        new CurrentState(inventory, WaitForRequests))

      //start test
      inventory ! new BookRequest("context1", replyProbe.ref)
      stateProbe.expectMsg(
        new Transition(inventory, WaitForRequests, WaitForPublisher))
      stateProbe.expectMsg(
        new Transition(inventory, WaitForPublisher, ProcessRequest))
      stateProbe.expectMsg(
        new Transition(inventory, ProcessRequest, WaitForRequests))
      replyProbe.expectMsg(new BookReply("context1", Right(1)))

      inventory ! new BookRequest("context2", replyProbe.ref)
      stateProbe.expectMsg(
        new Transition(inventory, WaitForRequests, ProcessRequest))
      stateProbe.expectMsg(
        new Transition(inventory, ProcessRequest, WaitForRequests))
      replyProbe.expectMsg(new BookReply("context2", Right(2)))

      inventory ! new BookRequest("context3", replyProbe.ref)
      stateProbe.expectMsg(
        new Transition(inventory, WaitForRequests, WaitForPublisher))
      stateProbe.expectMsg(
        new Transition(inventory, WaitForPublisher, ProcessRequest))
      stateProbe.expectMsg(
        new Transition(inventory, ProcessRequest, WaitForRequests))
      replyProbe.expectMsg(new BookReply("context3", Right(3)))

      inventory ! new BookRequest("context4", replyProbe.ref)
      stateProbe.expectMsg(
        new Transition(inventory, WaitForRequests, ProcessRequest))
      stateProbe.expectMsg(
        new Transition(inventory, ProcessRequest, WaitForRequests))
      replyProbe.expectMsg(new BookReply("context4", Right(4)))

      system.stop(inventory)
      system.stop(publisher)
    }
  }
  "InventoryTimer" must {
    "follow the flow" in {

      val publisher = TestProbe()
      val inventory = system.actorOf(
        Props(new InventoryWithTimer(publisher.ref)))
      val stateProbe = TestProbe()
      val replyProbe = TestProbe()

      inventory ! new SubscribeTransitionCallBack(stateProbe.ref)
      stateProbe.expectMsg(
        new CurrentState(inventory, WaitForRequests))

      //start test
      inventory ! new BookRequest("context1", replyProbe.ref)
      stateProbe.expectMsg(
        new Transition(inventory, WaitForRequests, WaitForPublisher))
      publisher.expectMsg(PublisherRequest)
      stateProbe.expectMsg(6 seconds,
        new Transition(inventory, WaitForPublisher, WaitForRequests))
      stateProbe.expectMsg(
        new Transition(inventory, WaitForRequests, WaitForPublisher))

      system.stop(inventory)
    }
  }

}
