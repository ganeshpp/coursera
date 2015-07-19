/**
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package actorbintree

import akka.actor._
import scala.collection.immutable.Queue
import akka.event.LoggingReceive

object BinaryTreeSet {

  trait Operation {
    def requester: ActorRef

    def id: Int

    def elem: Int
  }

  trait OperationReply {
    def id: Int
  }

  /** Request with identifier `id` to insert an element `elem` into the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Insert(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to check whether an element `elem` is present
    * in the tree. The actor at reference `requester` should be notified when
    * this operation is completed.
    */
  case class Contains(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request with identifier `id` to remove the element `elem` from the tree.
    * The actor at reference `requester` should be notified when this operation
    * is completed.
    */
  case class Remove(requester: ActorRef, id: Int, elem: Int) extends Operation

  /** Request to perform garbage collection */
  case object GC

  /** Holds the answer to the Contains request with identifier `id`.
    * `result` is true if and only if the element is present in the tree.
    */
  case class ContainsResult(id: Int, result: Boolean) extends OperationReply

  /** Message to signal successful completion of an insert or remove operation. */
  case class OperationFinished(id: Int) extends OperationReply


}


class BinaryTreeSet extends Actor with ActorLogging {

  import BinaryTreeSet._
  import BinaryTreeNode._

  def createRoot: ActorRef = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true))

  var root = createRoot

  // optional
  var pendingQueue = Queue.empty[Operation]

  // optional
  def receive = normal

  // optional
  /** Accepts `Operation` and `GC` messages. */

  import BinaryTreeSet.{Operation, GC}

  val normal: Receive = LoggingReceive {
    case op: Operation => root ! op; //  println("recvd:"+op.toString)
    case GC => val newroot = context.actorOf(BinaryTreeNode.props(0, initiallyRemoved = true)); root ! CopyTo(newroot); context.become(garbageCollecting(newroot));
    case x: (String) => println("unknown " + x.toString)
    // case res:ContainsResult  => sender !  res
    //  case  op:OperationFinished  =>   sender ! op;

  }


  // optional
  /** Handles messages while garbage collection is performed.
    * `newRoot` is the root of the new binary tree where we want to copy
    * all non-removed elements into.
    */
  def garbageCollecting(newRoot: ActorRef): Receive = LoggingReceive {
    case op: Operation => pendingQueue = pendingQueue.enqueue(op); //println("pendingrecvd:"+op.toString)
    case GC => //println("received gc op")
    case CopyFinished => {
      log.debug("parent")
      pendingQueue.foreach(x => {
        // log.debug("replaying {}", x);
        newRoot ! x
      })
      pendingQueue = Queue.empty[Operation]
      root ! PoisonPill
      root = newRoot
      log.debug("GC done")
      context.become(normal)
    }
    case _ => println("unknown op")
  }

}

object BinaryTreeNode {
  trait Position
  case object Left extends Position
  case object Right extends Position
  case class CopyTo(treeNode: ActorRef)
  case object CopyFinished
  def props(elem: Int, initiallyRemoved: Boolean) = Props(classOf[BinaryTreeNode], elem, initiallyRemoved)
}

class BinaryTreeNode(val elem: Int, initiallyRemoved: Boolean) extends Actor with ActorLogging {

  import BinaryTreeNode._
  import BinaryTreeSet._

  var subtrees = Map[Position, ActorRef]()
  var removed = initiallyRemoved

  // optional
  def receive = normal

  // optional
  /** Handles `Operation` messages and `CopyTo` requests. */
  val normal: Receive = LoggingReceive {
    case in: Insert => // println("insert:"+in.elem)
      if (in.elem == elem) {
        if (removed) removed = false;
        in.requester ! OperationFinished(in.id)
      }
      else if (in.elem < elem) {
        // println("insert left:"+in.id)
        if (!subtrees.isDefinedAt(Left)) {
          subtrees = subtrees + (Left -> context.actorOf(BinaryTreeNode.props(in.elem, initiallyRemoved = false)))
          in.requester ! OperationFinished(in.id)
        }
        else {
          subtrees.get(Left).get ! in
        }
      }
      else if (in.elem > elem) {
        // println("insert right:"+in.id)
        if (!subtrees.isDefinedAt(Right)) {
          subtrees = subtrees + (Right -> context.actorOf(BinaryTreeNode.props(in.elem, initiallyRemoved = false)))
          in.requester ! OperationFinished(in.id)
        }
        else {
          subtrees.get(Right).get ! in
        }
      }

    case con: Contains => // println("contains:"+elem)
      if (con.elem == elem) {
        //  println("rem:"+removed)
        con.requester ! ContainsResult(con.id, !removed)
      }
      else if (con.elem < elem) {
        //println("contains left:"+con.id)
        if (!subtrees.isDefinedAt(Left)) {
          con.requester ! ContainsResult(con.id, false)
        }
        else {
          subtrees.get(Left).get ! con
        }
      }
      else if (con.elem > elem) {
        //println("contains right:"+con.id)
        if (!subtrees.isDefinedAt(Right)) {
          con.requester ! ContainsResult(con.id, false)
        }
        else {
          subtrees.get(Right).get ! con
        }
      }


    case rem: Remove => //println("remove:"+elem)
      if (rem.elem == elem) {
        removed = true
        rem.requester ! OperationFinished(rem.id)
      }
      else if (rem.elem < elem) {
        // println("remove left:"+rem.id)
        if (!subtrees.isDefinedAt(Left)) {
          rem.requester ! OperationFinished(rem.id)
        }
        else {
          subtrees.get(Left).get ! rem
        }
      }
      else if (rem.elem > elem) {
        // println("remove right:"+rem.id)
        if (!subtrees.isDefinedAt(Right)) {
          rem.requester ! OperationFinished(rem.id)
        }
        else {
          subtrees.get(Right).get ! rem
        }
      }
    case GC => // println("requested gc")
    case CopyFinished => //println("heeee"); context.parent ! CopyFinished  ;//println("cpfin:"+sender +"  senderparent:"+context.parent +" self:"+self)
    case ct: CopyTo => // println("insided copey to sender:"+sender +"  senderparent:"+context.parent +" self:"+self)

      if (subtrees.isEmpty && removed) {
        sender ! CopyFinished
        println("subtree empty:" + elem)
      }
      else {
        // println("firing inisert for:"+elem)
        if (!removed) ct.treeNode ! Insert(self, elem, elem)
        subtrees.values.map(_ ! CopyTo(ct.treeNode))
        context.become(copying(subtrees.values.toSet, false))
      }

  }


  // optional
  /** `expected` is the set of ActorRefs whose replies we are waiting for,
    * `insertConfirmed` tracks whether the copy of this node to the new tree has been confirmed.
    */


  def copying(expected: Set[ActorRef], insertConfirmed: Boolean): Receive = LoggingReceive {
    case OperationFinished(_) =>
      //  println("op finished:"+x.toString+" expected:"+expected.toString()+" context:"+context.self)
      //println("ope:"+sender +"  senderparent:"+context.parent +" self:"+self)
      self ! CopyFinished
      context.become(copying(expected, true))

    case CopyFinished => val newSet = expected - (sender);
    if (newSet.isEmpty) {
        context.become(normal);
        (context.parent) ! CopyFinished

        // println("copying finished sender:"+sender +"  senderparent:"+context.parent +" self:"+self)
      }
      else {
        // println("changing context"+sender.toString()+"newset:"+newSet+" iinsertconfirm:"+insertConfirmed);
        context.become(copying(expected - (sender), insertConfirmed))
      }
    case _ => println("received unknown:")
  }

}
