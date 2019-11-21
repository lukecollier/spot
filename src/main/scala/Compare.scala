package spot.compare

import cats.effect.IO
import cats.implicits._
import cats.{Eq, Monad}
import spot.internals.DifferenceValidatorNec.validate
import sttp.client._
import sttp.model.{Header, Uri}
import cats.data._

import spot.internals.{Difference, Additional, Removed, Ordering}

object Compare {

  type Body[A] = Either[String, A]
  type PartReq[A] = PartialRequest[Body[A], Nothing]
  type Res[A] = Response[Body[A]]

  final case class Requests[A](
    candidate: Res[A], 
    primary: Res[A], 
    secondary: Res[A])

  private[compare] def request[A,  WS[_]](host: Uri, hosts: (Uri, Uri), request: PartReq[A] = basicRequest)(
    implicit b: SttpBackend[IO, Nothing, WS]): IO[Requests[A]] = {
      val (u1, u2) = hosts
      for {
        cr <- request.get(host).send()
        pr1 <- request.get(u1).send()
        pr2 <- request.get(u2).send()
      } yield Requests[A](cr, pr1, pr2)
  }

  implicit val headerEq: Eq[Header] = new Eq[Header] {
    def eqv(a: Header, b: Header): Boolean = a.value === b.value && a.name === b.name
  }

  val headerComparator: Comparator[Vector[Header]] = (orig, alt) => {
    validate(orig, alt).toEither match {
      case Right(_) => Same
      case Left(nec) => Different(nec)
    }
  }

  private[compare] def compare[A](a: Res[A], b: Res[A])(implicit co: Comparator[A]) = {
    (a.body, b.body) match {
      case (Right(aBody), Right(bBody)) =>
        val (aHeaders, bHeaders) = (a.headers.toVector, b.headers.toVector)
        (co.compare(aBody, bBody), headerComparator.compare(aHeaders, bHeaders))
      case (Left(_), Right(bBody)) => ???
      case (Right(aBody), Left(_)) => ???
      case (Left(_), Left(_)) => ???
    }
  }

  // needs to convert to event driven so we can continually run this
  def runAnalysis[A, WS[_]](host: Uri, hosts: (Uri, Uri), reqs: List[PartReq[A]])(
    implicit co: Comparator[A], b: SttpBackend[IO, Nothing, WS]) = {
      reqs.map { partialRequest =>
        request[A, WS](host, hosts, partialRequest).map {
          case Requests(candidate, primary, secondary) =>
            val primarySecondary = compare(primary, secondary)
            val canidatePrimary = compare(candidate, primary)
            val bodyRes = (primarySecondary._1, canidatePrimary._1) match {
              case (Different(primaryErr), Different(candidateErr)) => 
                // todo seperate this complexity or even fix the root problem
                val (pAdd, cAdd) = (
                  primaryErr.collect { case Additional(value) => value}, 
                  candidateErr.collect { case Additional(value) => value})
                val (pRem, cRem) = (
                  primaryErr.collect { case Removed(value) => value}, 
                  candidateErr.collect { case Removed(value) => value})
                val (firstDiff, secondDiff) = (pAdd.toList.diff(cRem.toList), cAdd.toList.diff(pRem.toList))
                if (firstDiff.isEmpty && secondDiff.isEmpty) {
                  Same
                } else {
                  Different(candidateErr)
                }
              case (Different(_), Same) => Same
              case (Same, Different(chain)) => Different(chain)
              case (Same, Same) => Same
            }
            val headerRes = (primarySecondary._2, canidatePrimary._2) match {
              case (Different(primaryErr), Different(candidateErr)) => ??? // if same return same
              case (Different(_), Same) => Same
              case (Same, Different(chain)) => Different(chain)
              case (Same, Same) => Same
            }
            (bodyRes, headerRes)
        }
      }
  }

}

sealed trait Comparison
final case class Different[A](diffs: NonEmptyChain[Difference[A]]) extends Comparison
final case object Same extends Comparison

trait Comparator[A] { 
  def compare(orig: A, alt: A): Comparison
}

object Comparator {
  implicit val listString: Comparator[List[String]] = (orig: List[String], alt: List[String]) => {
    validate(orig, alt).toEither match {
      case Right(_) => Same
      case Left(nec) => Different(nec)
    }
  }

  implicit val string: Comparator[String] = (orig: String, alt: String) => {
    val origList: List[Char] = orig.toList
    val altList: List[Char] = alt.toList
    validate(origList, altList).toEither match {
      case Right(_) => Same
      case Left(nec) => Different(nec)
    }
  }

  implicit val byteArray: Comparator[Array[Byte]] = (orig: Array[Byte], alt: Array[Byte]) => {
    val origList: List[Byte] = orig.toList
    val altList: List[Byte] = alt.toList
    validate(origList, altList).toEither match {
      case Right(_) => Same
      case Left(nec) => Different(nec)
    }
  }
}
