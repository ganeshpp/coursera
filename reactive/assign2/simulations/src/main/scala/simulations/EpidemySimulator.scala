package simulations

import math.random

class EpidemySimulator extends Simulator {

  def randomBelow(i: Int) = (random * i).toInt

  protected[simulations] object SimConfig {
    val population: Int = 300
    val roomRows: Int = 8
    val roomColumns: Int = 8
    val prevalenceRate = 0.01
    val transRate = 0.4
    val dieRate = 0.25

    val incubationTime = 6
    val dieTime = 14
    val immuneTime = 16
    val healTime = 18

    // to complete: additional parameters of simulation
  }

  import SimConfig._
  val normal = WorkItem(incubationTime,isnormal)
  val dead = WorkItem((dieTime+1),() =>todie)
  val sick = WorkItem((incubationTime+1),() =>toSick)
  val notdead = WorkItem((dieTime),() =>notDead)

  def isnormal: Action =()=> {
    //println("normal:")
    next
    //persons.map( x => if(x.infected==true) x.sick=true)

  }

  def toSick {
  //  println("assigning sick:")
    for(p <- this.persons)
    {
      if(true == p.infected)
      {
          p.sick=true

      }
    }
     next
  }

  def notDead {
  //  println( "notdead:")
    val healthyPerson = (persons find {p => !p.infected}).get
    val infectCount = persons.filter(p => p.infected).size
    val healthyList= persons.filter(p => !p.infected)
    val finalinfectedcount = (healthyList.size * transRate).toInt


   // println("count:"+ finalinfectedcount  )
    val inflist=(0 until (finalinfectedcount)) map (x => randomBelow(population-infectCount) ) toList


     inflist map  ( x => healthyList(x).infected=true )

     val infels= (persons.filter(p => p.infected)).size

    //println("infcount:"+infels)

  }

  def todie {
   // println( "die:")
    for(p <- this.persons)
    {
      if(true == p.sick)
      {
        p.dead=true

      }
    }
    run
  }

  agenda = List(normal,sick,notdead,notdead,normal,dead)

  val persons: List[Person] = {

    val infected  = prevalenceRate *  population
    val normalPersons: List[Person] = (0 until population) map ( x => new Person(x)  ) toList

    (0 until infected.toInt ) map ( x  =>    normalPersons(x).infected=true  )
    normalPersons
  } // to complete: construct list of persons


  class Person (val id: Int) {
    var infected = false
    var sick = false
    var immune = false
    var dead = false

    // demonstrates random number generation
    var row: Int = randomBelow(roomRows)
    var col: Int = randomBelow(roomColumns)



  }
}
