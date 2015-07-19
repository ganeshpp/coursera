package simulations

import org.scalatest.FunSuite

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CircuitSuite extends CircuitSimulator with FunSuite {
  val InverterDelay = 1
  val AndGateDelay = 3
  val OrGateDelay = 5
 
  test("andGate example") {
    val in1, in2, out = new Wire
    andGate(in1, in2, out)
    in1.setSignal(false)
    in2.setSignal(false)
    run
    
    assert(out.getSignal === false, "and 1")

    in1.setSignal(true)
    run
    
    assert(out.getSignal === false, "and 2")

    in2.setSignal(true)
    run
    
    assert(out.getSignal === true, "and 3")
  }

  test("orGate example") {
    val in1, in2, out = new Wire
    orGate(in1, in2, out)
    in1.setSignal(false)
    in2.setSignal(false)
    run

    assert(out.getSignal === false, "and 1")

    in1.setSignal(true)
    run

    assert(out.getSignal === true, "and 2")

    in2.setSignal(true)
    run

    assert(out.getSignal === true, "and 3")
  }

  test("orGate2 example") {
    val in1, in2, out = new Wire
    orGate2(in1, in2, out)
    in1.setSignal(false)
    in2.setSignal(false)
    run

    assert(out.getSignal === false, "and 1")

    in1.setSignal(true)
    run

    assert(out.getSignal === true, "and 2")

    in2.setSignal(true)
    run

    assert(out.getSignal === true, "and 3")
  }

  test("demux example") {
    val in = new Wire
    val ctrl = (0 until 4) map (x => (new Wire))
    val out  = (0 until 16) map (x => new Wire)
    ctrl.map(wire => wire.setSignal(false))
    out.map(wire => wire.setSignal(false))
    demux(in,ctrl.toList, out.toList)
    in.setSignal(true)
    ctrl(0).setSignal(true)  //1000->8
    run
   // out.map{ x => if (x.getSignal) print(1) else print(0)}
    assert(out(7).getSignal === true, "and 1")

    in.setSignal(false)
    run

    assert(out(7).getSignal === false, "and 2")
    in.setSignal(true)
    ctrl(2).setSignal(true)   //1010 ->10
    run
    assert(out(5).getSignal === true, "and 3")
    ctrl(0).setSignal(false)    //0010->2->13
    run
    assert(out(5).getSignal === false, "and 4")
    assert(out(13).getSignal === true, "and 5")

    ctrl(2).setSignal(false)
    run
    assert(out(13).getSignal === false, "and 13")
    assert(out(15).getSignal === true, "and 15")
    ctrl(0).setSignal(true)
    ctrl(1).setSignal(true)
    ctrl(2).setSignal(true)
    ctrl(3).setSignal(true)
    run
    assert(out(15).getSignal === false, "and 15")
    assert(out(0).getSignal === true, "and 0")



  }

  test("demux2 example") {
    val in = new Wire
    val ctrl = (0 until 3) map (x => (new Wire))
    val out  = (0 until 8) map (x => new Wire)
    ctrl.map(wire => wire.setSignal(false))
    out.map(wire => wire.setSignal(false))
    demux(in,ctrl.toList, out.toList)
    in.setSignal(true)
    ctrl(0).setSignal(true)  //100->4
    run
   // out.map{ x => if (x.getSignal) print(1) else print(0)}
    assert(out(3).getSignal === true, "and 1")

    in.setSignal(false)
    run

    assert(out(3).getSignal === false, "and 2")
    in.setSignal(true)
    ctrl(0).setSignal(false)
    ctrl(2).setSignal(true)   //001 -> 1
    run
    assert(out(4).getSignal === false, "and 3")
    assert(out(6).getSignal === true, "and 5")
    ctrl(2).setSignal(false)
    run
    assert(out(6).getSignal === false, "and 6")
    assert(out(7).getSignal ===  true, "and 7")
    (0 until 7) map { x  => assert(out(x).getSignal ===  false, "and " + x) }
    in.setSignal(false)
    run

    (0 until 8) map { x  => assert(out(x).getSignal ===  false, "and " + x) }
  }

  //
  // to complete with tests for orGate, demux, ...
  //
     
}
