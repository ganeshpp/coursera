package kvstore

import akka.actor.{Cancellable, Props, Actor, ActorRef}
import scala.concurrent.duration._
import org.scalacheck.Prop.Exception

object Replicator {
  case class Replicate(key: String, valueOption: Option[String], id: Long)
  case class Replicated(key: String, id: Long)
  
  case class Snapshot(key: String, valueOption: Option[String], seq: Long)
  case class SnapshotAck(key: String, seq: Long)

  def props(replica: ActorRef): Props = Props(new Replicator(replica))
}

class Replicator(val replica: ActorRef) extends Actor {
  import Replicator._
  import Replica._
  import context.dispatcher
  
  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */

  // map from sequence number to pair of sender and request
  var acks = Map.empty[Long, (ActorRef, Replicate,Cancellable,Cancellable)]
  // a sequence of not-yet-sent snapshots (you can disregard this if not implementing batching)
  var pending = Vector.empty[Snapshot]
  
  var _seqCounter = 0L
  def nextSeq = {
    val ret = _seqCounter
    _seqCounter += 1
    ret
  }
  
  /* TODO Behavior for the Replicator. */
  def receive: Receive = {
    case rep:Replicate=> //println("got replicate mesg: "+rep.id);
      val seq =nextSeq; val req= Snapshot(rep.key,rep.valueOption,seq);
      val can =  context.system.scheduler.schedule(0 milliseconds,100 milliseconds,replica,req)
      val can2 = context.system.scheduler.scheduleOnce(2 second, sender, OperationFailed(rep.id))
      acks += (seq->(sender,rep,can,can2)) ;
    case ack: SnapshotAck => //println("got snapshot ack: "+ack.seq);
                            acks.get(ack.seq).map( x => {x._3.cancel();x._4.cancel(); x._1 ! Replicated(ack.key,x._2.id)})
  }

}
