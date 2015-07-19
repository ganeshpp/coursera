package suggestions



import language.postfixOps
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try, Success, Failure}
import rx.lang.scala._
import org.scalatest._
import gui._

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import rx.lang.scala.concurrency.Schedulers


@RunWith(classOf[JUnitRunner])
class WikipediaApiTest extends FunSuite {

  object mockApi extends WikipediaApi {
    def wikipediaSuggestion(term: String) = Future {
      if (term.head.isLetter) {
        for (suffix <- List(" (Computer Scientist)", " (Footballer)")) yield term + suffix
      } else {
        List(term)
      }
    }
    def wikipediaPage(term: String) = Future {
      "Title: " + term
    }
  }

  import mockApi._
  test("timedOut should collect the correct number of values"){
    val nbTicks = 4
    val clock = Observable.interval(1 second)
    val timedOut = clock.timedOut(nbTicks)
    assert (timedOut.toBlockingObservable.toList.length === nbTicks)  }

  test("test concat") {
    val requests = Observable(1, 2, 3, 4, 5)
    def rm(num: Int) = if (num != 4) Observable(num) else Observable(new Exception)

    val rec = requests concatRecovered(rm)
    val ret = rec.map(_.isFailure).toBlockingObservable.toList
    assert(ret === List(false, false, false, true, false))
  }
  test("test recovered") {
    val requests = Observable(3, 2, 1)
    val comp = requests.map(i => i / (i - 1))

    val theList = comp.recovered.map(_.isFailure).toBlockingObservable.toList
    assert(theList === List(false, false, true))
  }
  test("WikipediaApi should make the stream valid using sanitized") {
    val notvalid = Observable("erik", "erik meijer", "martin")
    val valid = notvalid.sanitized

    var count = 0
    var completed = false

    val sub = valid.subscribe(
      term => {
        assert(term.forall(_ != ' '))
        count += 1
      },
      t => assert(false, s"stream error $t"),
      () => completed = true
    )
    assert(completed && count == 3, "completed: " + completed + ", event count: " + count)
  }
  test("WikipediaApi should correctly use recovered on excepetion") {
    val exception = new Exception("foo")
    val requests = Observable(1, 2) ++ Observable(exception) ++ Observable(3)
    val recovered = requests.recovered

    assert(recovered.toBlockingObservable.toList === List(Success(1), Success(2), Failure(exception)))
  }
  test("WikipediaApi should correctly use concatRecovered") {
    val requests = Observable(1, 2, 3)
    val remoteComputation = (n: Int) => Observable(0 to n)
    val responses = requests concatRecovered remoteComputation
    val sum = responses.foldLeft(0) { (acc, tn) =>
      tn match {
        case Success(n) => acc + n
        case Failure(t) => throw t
      }
    }
    var total = -1
    val sub = sum.subscribe {
      s => total = s
    }
    assert(total == (1 + 1 + 2 + 1 + 2 + 3), s"Sum: $total")
  }
}