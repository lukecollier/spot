package spot.compare

import cats.effect.IO
import cats.implicits._
import cats.{Eq, Monad}
import spot.internals.DifferenceValidatorNec.validate
import sttp.client._
import sttp.model.{Header, Uri}

import scala.language.higherKinds

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
      if (validate(orig, alt).isValid) {
        Same()
      } else {
        Different()
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

  def buildAnalysis[A, WS[_]](host: Uri, hosts: (Uri, Uri), reqs: List[PartReq[A]])(
    implicit co: Comparator[A], b: SttpBackend[IO, Nothing, WS]) = {
      reqs.map { partialRequest =>
        request[A, WS](host, hosts, partialRequest).map {
          case Requests(candidate, primary, secondary) =>
            val primarySecondary = compare(primary, secondary)
            val canidatePrimary = compare(candidate, primary)
            // compare primary secondary and candidate primary and see if the differences are the same
            Same()
        }
      }
  }

}

sealed trait Comparison[A] // should implement Semigroup
final case class Different[A]() extends Comparison[A]
final case class Same[A]() extends Comparison[A]

trait Comparator[A] { 
  def compare(orig: A, alt: A): Comparison[A]
}

object Comparator {
  implicit val listString: Comparator[List[String]] = (orig: List[String], alt: List[String]) => {
    if (validate(orig, alt).isValid) {
      Same()
    } else {
      Different()
    }
  }

  implicit val byteArray: Comparator[Array[Byte]] = (orig: Array[Byte], alt: Array[Byte]) => {
    val origList: List[Byte] = orig.toList
    val altList: List[Byte] = alt.toList
    if (validate(origList, altList).isValid) {
      Same()
    } else {
      Different()
    }
  }
}
