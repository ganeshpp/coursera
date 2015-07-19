package quickcheck

import common._

import org.scalacheck._
import Arbitrary._
import Gen._
import Prop._

abstract class QuickCheckHeap extends Properties("Heap") with IntHeap {

  property("min1") = forAll { a: Int =>
    val h = insert(a, empty)
    findMin(h) == a
  }


  property("deleteAll") = forAll { a: Int =>

    val h=deleteMin(insert(a, empty))
    isEmpty(h)==true
  }
  property("minof2num") = forAll { (a: Int,b: Int) =>
    val h = insert(b, insert(a, empty) )
   findMin(h) == List(a,b).min
  }

  property("deleteAll2") = forAll { (a: Int,b: Int) =>
    val h = insert(b, insert(a, empty) )
    val oneElem= deleteMin(h)
    val emptyHeap=deleteMin(oneElem)
    isEmpty(emptyHeap)==true
  }

  property("deleteMin") = forAll { (a: Int,b: Int) =>
    val h = insert(b, insert(a, empty) )
    val oneElem= deleteMin(h)
    findMin(oneElem)==List(a,b).max
  }
  property("minof3num") = forAll { (a: Int,b: Int,c: Int) =>
    val h = insert(c,insert(b, insert(a, empty)))
    findMin(h) == List(a,b,c).min
  }

  property("deleteRegress") = forAll { (a: Int,b: Int,c: Int,d: Int) =>
    val h = insert(d,insert(c,insert(b, insert(a, empty))))
    val min1 = findMin(h)
    min1 == List(a,b,c,d).min
    val nlist = List(a,b,c,d) diff List(min1)
    val min2 = findMin(deleteMin(h))
    min2 == nlist.min
  }


  property("MergeHeaps") = forAll { (a: Int,b: Int,c: Int,d: Int,e: Int, f: Int) =>
    val g1 = Gen.choose(100,500)
    val g2 = Gen.listOfN(5,g1)

    val list1 =g2.sample.get
    val list2 = g2.sample.get
    val h1 =  AddAll(list1,empty) //insert(c,insert(b, insert(a, empty)))
    val h2 =  AddAll(list2,empty)//insert(d,insert(e,insert(f,empty)))
    val minh1 = findMin(h1)
    val minh2 = findMin(h2)
    minh1 == list1.min
    minh2 == list2.min
    val mergedH = meld(h1,h2)
    val finalList=deleteAll(Nil,mergedH)
    finalList ==  finalList.sortWith(_  <  _)

    findMin(mergedH) == List(minh1,minh2).min
    val min = findMin(mergedH)
    val mergedH1= deleteMin(mergedH)
    val min2 = findMin(mergedH1)
    val nlist = list1:::list2 diff List(min)
    min2 == nlist.min
  }



  lazy val random5: List[Int]=  {
    val g1 = Gen.choose(100,500)
    val g2 = Gen.listOfN(5,g1)
    g2.sample.get
  }




  def AddAll(list: List[Int], ts: H): H = list match {
    case Nil => ts
    case head :: _ => AddAll(list.tail,insert(head,ts))
  }

  def deleteAll(list: List[Int], ts: H): List[Int] = ts match {
    case  ts if(isEmpty(ts))==true => list
    case  ts if(isEmpty(ts)) == false  => val min = findMin(ts)
              deleteAll(min::list,deleteMin(ts))

  }



  lazy val genHeap: Gen[H] = empty

  implicit lazy val arbHeap: Arbitrary[H] = Arbitrary(genHeap)

}
