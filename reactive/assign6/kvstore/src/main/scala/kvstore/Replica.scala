package kvstore

import akka.actor._
import kvstore.Arbiter._
import scala.collection.immutable.{Range, Queue}
import akka.actor.SupervisorStrategy.{Stop, Restart}
import scala.annotation.tailrec
import akka.pattern.{ask, pipe}
import scala.concurrent.duration._
import akka.util.Timeout
import scala.Some
import scala.concurrent._
import java.lang.Exception
import scala.Exception
import scala.util.Random
import akka.actor
import scala.Predef._
import akka.actor.ActorKilledException
import scala.Some
import akka.actor.OneForOneStrategy
import kvstore.Arbiter.Replicas

object Replica {

  sealed trait Operation {
    def key: String

    def id: Long
  }

  case class Insert(key: String, value: String, id: Long) extends Operation

  case class Remove(key: String, id: Long) extends Operation

  case class Get(key: String, id: Long) extends Operation

  sealed trait OperationReply

  case class OperationAck(id: Long) extends OperationReply

  case class OperationFailed(id: Long) extends OperationReply

  case class GetResult(key: String, valueOption: Option[String], id: Long) extends OperationReply

  def props(arbiter: ActorRef, persistenceProps: Props): Props = Props(new Replica(arbiter, persistenceProps))
}

class Replica(val arbiter: ActorRef, persistenceProps: Props) extends Actor {

  import Replica._
  import Replicator._
  import Persistence._
  import context.dispatcher

  /*
   * The contents of this actor is just a suggestion, you can implement it in any way you like.
   */
  var acks = Map.empty[Long, (ActorRef, Object, Cancellable, Cancellable, Map[ActorRef, Promise[Long]])]
  val persist = context.actorOf(persistenceProps, "persist-primary")
  var kv = Map.empty[String, String]
  // a map from secondary replicas to replicators
  var secondaries = Map.empty[ActorRef, ActorRef]
  // the current set of replicators
  var replicators = Set.empty[ActorRef]
  var seq: Long = 0

  def receive = {
    case JoinedPrimary => context.become(leader)
    case JoinedSecondary => context.become(replica)
  }

  def all[T](fs: List[Future[T]]): Future[List[T]] = {
    val successful = Promise[List[T]]()

    successful.success(Nil)
    fs.foldRight(successful.future) {
      (f, acc) => for {x <- f; xs <- acc} yield x :: xs
    }
  }

  /*final val defaultStrategy: SupervisorStrategy = {
    import akka.actor.SupervisorStrategy._
    def defaultDecider: Decider = {
      case _: ActorInitializationException ⇒ Stop
      case _: ActorKilledException         ⇒ Stop
      case _: Exception                    ⇒ println("Restarting:");Restart
    }
    OneForOneStrategy()(defaultDecider)
  }  */
  override val supervisorStrategy = OneForOneStrategy(3) {
    case e: Exception => Restart; // println("Restarting:"+sender.toString()+" excep:"+e.toString);
    case _: ActorKilledException => Stop; //   println("stopping:"+sender.toString());
  }
  /* TODO Behavior for  the leader role. */
  val leader: Receive = {
    case get: Get => sender ! GetResult(get.key, kv.get(get.key), get.id)
    case ins: Insert => kv += (ins.key -> ins.value);
      // println("got insert:"+ins.id);
      val can = context.system.scheduler.schedule(0 milliseconds, 100 milliseconds, persist, Persist(ins.key, Some(ins.value), ins.id))
      val can2 = context.system.scheduler.scheduleOnce(1 second, sender, OperationFailed(ins.id))
      // acks += (ins.id ->(sender,OperationAck(ins.id),can,can2))
      // println("rep:"+replicators.toString())
      // replicators.map( _ ! Replicate(ins.key,Some(ins.value),ins.id))
      var idtopromise = Map.empty[ActorRef, Promise[Long]]
      if (!replicators.isEmpty) {
        for (reps <- replicators) {
          reps ! Replicate(ins.key, Some(ins.value), ins.id)
          val p = Promise[Long]()
          idtopromise += (reps -> p)
        }
        val p = Promise[Long]()
        idtopromise += (persist -> p)
        val fs = idtopromise.map(x => x._2.future).toList
        val completed = Future.sequence(fs)
        acks += (ins.id ->(sender, OperationAck(ins.id), can, can2, idtopromise))
        val actor = sender
        context.system.scheduler.scheduleOnce(1 second) {

          if (completed.isCompleted) {
            completed onSuccess { case _ => actor ! OperationAck(ins.id);
              // println(" completed, succses ack")
            }
            completed onFailure { case _ => actor ! OperationFailed(ins.id);
              //println(" completed failure ack:"+actor.toString())
            }
          }
          else {
            //  println("not completed, sendingack:"+ins.id)
            actor ! OperationFailed(ins.id)
          }
        }
      } else {
        acks += (ins.id ->(sender, OperationAck(ins.id), can, can2, null))
      }


    case rem: Remove => kv = kv - rem.key;
      val can = context.system.scheduler.schedule(0 milliseconds, 100 milliseconds, persist, Persist(rem.key, None, rem.id))
      val can2 = context.system.scheduler.scheduleOnce(1 second, sender, OperationFailed(rem.id))
      // replicators.map( future{ _ ! Replicate(rem.key,None,rem.id) })
      if (!replicators.isEmpty) {
        var idtopromise = Map.empty[ActorRef, Promise[Long]]
        for (reps <- replicators) {
          reps ! Replicate(rem.key, None, rem.id)
          val p = Promise[Long]()
          idtopromise += (reps -> p)
        }
        val p = Promise[Long]()
        idtopromise += (persist -> p)
        val fs = idtopromise.map(x => x._2.future).toList
        val completed = Future.sequence(fs)
        val actor = sender
        acks += (rem.id ->(sender, OperationAck(rem.id), can, can2, idtopromise))
        context.system.scheduler.scheduleOnce(1 second) {
          if (completed.isCompleted) {
            completed onSuccess { case _ => actor ! OperationAck(rem.id)}
            completed onFailure { case _ => actor ! OperationFailed(rem.id)}
          }
          else {
            actor ! OperationFailed(rem.id)
          }

        }
      } else {
        acks += (rem.id ->(sender, OperationAck(rem.id), can, can2, null))
      }

    case pers: Persisted => acks.get(pers.id).map(x => {
      x._4.cancel(); x._3.cancel(); if (replicators.isEmpty) {
        x._1 ! x._2; acks -= pers.id
      } else {
        x._5.get(sender).get.success(pers.id)
      }
    }) //
    case fail: OperationFailed => acks.get(fail.id).map(x => {
      // println("opfailed:"+sender.toString());//x._5.get(sender).get.failure(new Exception(fail.id.toString))
      x._5.get(sender).map(x => {
        if (!x.isCompleted) x.failure(new Exception(fail.id.toString))
      })
    })
    case x: Replicas => //println("recieved replicas:"+x.toString)  ;
      if (replicators.isEmpty) {
        for (rep <- (x.replicas - self)) {
          val replicator = context.actorOf(Replicator.props(rep))
          secondaries += (rep -> replicator)
        }
      } else {
        val newreplicas = (x.replicas - self)

        for (x <- newreplicas) {
          if (!secondaries.contains(x)) {
            val replicator = context.actorOf(Replicator.props(x))
            secondaries += (x -> replicator)
          }
        }

        for (x <- secondaries.keySet) {
          if (!newreplicas.contains(x)) {
            val actor = secondaries.get(x)
            context.stop(actor.get)
            secondaries -= x
            acks.map { case (z, y) => // val promise =y._5.get(actor.get).get; if(!promise.isCompleted) promise.success(0)
              y._5.get(actor.get).map(x => {
                if (!x.isCompleted) x.success(0)
              })
            }
          }
        }
        //   println("sedond:"+secondaries.keySet.toString())
      }
      replicators = secondaries.values.toSet
      val acter = self
      for (reps <- replicators) {
        var promise = Map.empty[ActorRef, Promise[Long]]
        val rand = Random.nextLong()
        kv.toMap.map(x => reps ! Replicate(x._1, Some(x._2), rand))
        val p = Promise[Long]()
        promise += (reps -> p)
        acks += (rand ->(reps, OperationAck(rand), null, null, Map(reps -> p)))
        // println("rand:"+reps.toString())
        p.future onSuccess { case _ => acter ! OperationAck(rand);
          //println(" , succses ack")
        }
        p.future onFailure { case _ => acter ! OperationFailed(rand); //println(" , failure ack:"+self.toString())
        }


        //  x => {println("replica sender:"+sender.toString()+" map:"+x._5.toString()); }
      }

    case replicated: Replicated => // println("recieved replicated success:"+replicated.id)  ;
      acks.get(replicated.id).map(x => {
        val prom = x._5.get(sender).get; if (!prom.isCompleted) prom.success(replicated.id)
      }); //acks.get(replicated.id).map( x => println("inmap:"+x.toString()) );
    case o: Object => // println("unknown:"+o.toString)

  }

  /* TODO Behavior for the replica role. */
  val replica: Receive = {
    case get: Get => sender ! GetResult(get.key, kv.get(get.key), get.id); // println("rec:"+kv.get(get.key));
    case snap: Snapshot =>
      // println("got snapshot mesg :"+snap.seq)
      if (seq == snap.seq) {
        //   println("==Seq:"+seq +" incoming:"+snap.seq)
        snap.valueOption match {
          case Some(key) => kv += (snap.key -> snap.valueOption.get);
          case None => kv = kv - snap.key;
        }
        val can = context.system.scheduler.schedule(0 milliseconds, 100 milliseconds, persist, Persist(snap.key, snap.valueOption, snap.seq))
        val can2 = context.system.scheduler.scheduleOnce(1 second, sender, OperationFailed(snap.seq))
        acks += (snap.seq ->(sender, SnapshotAck(snap.key, snap.seq), can, can2, null))
        seq = seq + 1
      } else if (seq > snap.seq) {
        // println("<Seq:"+seq +" incoming:"+snap.seq)
        seq = List(seq, snap.seq + 1).max
        sender ! SnapshotAck(snap.key, snap.seq)
      }
      else if (seq < snap.seq) {
        //println(">Seq:"+seq +" incoming:"+snap.seq)
      }

    case pers: Persisted => acks.get(pers.id).map(x => {
      x._4.cancel(); x._3.cancel(); x._1 ! x._2
    })
    case o: Object => //println("unknown replica:"+o.toString)

  }

  override def preStart() = {
    arbiter ! Join
  }

}
