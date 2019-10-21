package spot.compare

import spot.internals.DifferenceValidatorNec.validate
import cats.data.Validated.{Invalid, Valid}
import cats.{Eq, Traverse, TraverseFilter}
import cats.effect.IO

import sttp.client._

// desired UX
// implicit val jsonComparator: Comparator[Resp, Json] = {
//   def compare(x: Resp, y: Resp): Comparison[Json] = {
//     val (xJson, yJson): (Json, Json) = (x.as[Json], y.as[Json])
//     DifferenceValidatorNec.compare(xJson, yJson)
//   }
// }
// val requests = new Sink((x,y) => Comparator.compare(x,y)) # find a way to implement this
// or
// val requests: Bucket[List[_], (Resp, Resp) => Boolean] = new Bucket(List(json, binary, xml), (x, y) => Comparator.compare(x, y))
//
// val liveHostNames: List[Host] = List(Host("https", "pe-04.lhr"), Host("https", "pe-05.ams"))
// val stageHostNames: List[Host] = List(Host("https", "pe-stage-05.ams"))
// requests.candidates(stageHostNames).productions(liveHostNames).compare(x).against(y)
//
//
case class Request[I, O](input: RequestInput, output: RequestOutput) {
}

sealed trait RequestIO {
}
object RequestIO {
  final case class Host(scheme: String, host: String) {
    def withPathParams(path: List[String], params: Map[String, String]) = 
      uri"$scheme://$host/$path$params"
  }

  final case class Path(paths: List[List[String]]) {
    def params(params: Map[String, String]): Params = Params(params)
  }

  final case class Params(params: List[Map[String, String]]) {
    def candidates(candidates: List[Host]): CandidateServers = {
      CandidateServers(candidates)
    }
  }

  final case class CandidateServers(candidates: List[Host]) {
    def live(live: List[Host]): LiveServers = {
      LiveServers(live)
    }
  }

  final case class LiveServers(candidates: List[Host]) {
  }
}

sealed trait Comparison[A]
case class Different[A]() extends Comparison[A]
case class Same[A]() extends Comparison[A]

trait Comparator[A, B] {
  def compare(orig: A, alt: A): Comparison[B]
}

object Comparator {
  def make[A, B](compareFn: (A, A) => Comparison[B]): Comparator[A, B] = new Comparator[A, B] {
    override def compare(orig: A, alt: A): Comparison[B] = compareFn(orig, alt)
  }
}
