package simulations

import common._
import scala.math._

class Wire {
  private var sigVal = false
  private var actions: List[Simulator#Action] = List()

  def getSignal: Boolean = sigVal
  
  def setSignal(s: Boolean) {
    if (s != sigVal) {
      sigVal = s
      actions.foreach(action => action())
    }
  }

  def addAction(a: Simulator#Action) {
    actions = a :: actions
    a()
  }
}

abstract class CircuitSimulator extends Simulator {

  val InverterDelay: Int
  val AndGateDelay: Int
  val OrGateDelay: Int

  def probe(name: String, wire: Wire) {
    wire addAction {
      () => afterDelay(0) {
        println(
          "  " + currentTime + ": " + name + " -> " +  wire.getSignal)
      }
    }
  }

  def inverter(input: Wire, output: Wire) {
    def invertAction() {
      val inputSig = input.getSignal
      afterDelay(InverterDelay) { output.setSignal(!inputSig) }
    }
    input addAction invertAction
  }

  def andGate(a1: Wire, a2: Wire, output: Wire) {
    def andAction() {
      val a1Sig = a1.getSignal
      val a2Sig = a2.getSignal
      afterDelay(AndGateDelay) { output.setSignal(a1Sig & a2Sig) }
    }
    a1 addAction andAction
    a2 addAction andAction
  }

  //
  // to complete with orGates and demux...
  //

  def orGate(a1: Wire, a2: Wire, output: Wire) {
    def orAction() {
      val a1Sig = a1.getSignal
      val a2Sig = a2.getSignal
      afterDelay(OrGateDelay) { output.setSignal(a1Sig | a2Sig) }
    }
    a1 addAction orAction
    a2 addAction orAction
  }
  
  def orGate2(a1: Wire, a2: Wire, output: Wire) {
    val notIn1, notIn2, notOut = new Wire
    inverter(a1, notIn1); inverter(a2, notIn2)
    andGate(notIn1, notIn2, notOut)
    inverter(notOut, output)
  }

  def demux(in: Wire, c: List[Wire], out: List[Wire]) {

    def demuxAction() {
      val signallist = (c.map( wire => if(wire.getSignal) 1 else 0) toList).reverse
      val outputIndex =  signallist.zipWithIndex.map( (t) => math.pow(2,t._2)).zip(signallist).map( (t) => ( t._1 * t._2)).sum.toInt
      afterDelay(AndGateDelay) {
        val signallist = (c.map( wire => if(wire.getSignal) 1 else 0) toList).reverse
        println("signal list:"+signallist.reverse)
        val outputIndex =  signallist.zipWithIndex.map( (t) => math.pow(2,t._2)).zip(signallist).map( (t) => ( t._1 * t._2)).sum.toInt

        for(o <- out) o.setSignal(false)
        //println("out size:"+ out.size)
       // println("out putindex:"+ (outputIndex-1))
       // println("final index:"+ (out.size - outputIndex-1))

        out(out.size - outputIndex-1).setSignal(in.getSignal)
        val finalout=(out.map( wire => if(wire.getSignal) 1 else 0))
        println("finalout list:"+finalout)
      }
    }
    in addAction demuxAction
    for(ctrl <- c) ctrl addAction demuxAction

  }

}

object Circuit extends CircuitSimulator {
  val InverterDelay = 1
  val AndGateDelay = 3
  val OrGateDelay = 5

  def andGateExample {
    val in1, in2, out = new Wire
    andGate(in1, in2, out)
    probe("in1", in1)
    probe("in2", in2)
    probe("out", out)
    in1.setSignal(false)
    in2.setSignal(false)
    run

    in1.setSignal(true)
    run

    in2.setSignal(true)
    run
  }

  //
  // to complete with orGateExample and demuxExample...
  //
}

object CircuitMain extends App {
  // You can write tests either here, or better in the test class CircuitSuite.
  Circuit.andGateExample
}
