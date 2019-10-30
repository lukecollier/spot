package spot.compare

import cats.data.Validated.{Invalid, Valid}
import cats.{Eq, Traverse, Monad}
import cats.data.Chain
import cats.effect.IO
import cats.data.EitherT
import cats.implicits._

import sttp.client._
import sttp.model.{Uri, Header}

import spot.internals.DifferenceValidatorNec.validate

object Compare {

  type Body[A] = Either[String, A]
  type PartReq[A] = PartialRequest[Body[A], Nothing]
  type Res[A] = Response[Body[A]]

  final case class Requests[A](
    candidate: Res[A], 
    primary: Res[A], 
    secondary: Res[A])

  private[compare] def request[A, F[_]: Monad, WS[_]](host: Uri, hosts: (Uri, Uri), request: PartReq[A])(
    implicit b: SttpBackend[F, Nothing, WS]): F[Requests[A]] = {
      val (u1, u2) = hosts
      (for {
        cr <- request.get(host).send()
        pr1 <- request.get(u1).send()
        pr2 <- request.get(u2).send()
      } yield Requests[A](cr, pr1, pr2))
  }

  implicit val headerEq: Eq[Header] = new Eq[Header] {
    def eqv(a: Header, b: Header) = a.value === b.value && a.name === b.name
  }

  val headerComparator: Comparator[Vector[Header]] = Comparator.make(
    (orig, alt) => {
      validate(orig, alt)
      Different()
    })

  private[compare] def compare[A](a: Res[A], b: Res[A])(implicit co: Comparator[A]) = {
    (a.body, b.body) match {
      case (Right(aBody), Right(bBody)) => {
        val (aHeaders, bHeaders) = (a.headers.toVector, b.headers.toVector)
        (co.compare(aBody, bBody), headerComparator.compare(aHeaders, bHeaders))
      }
      case (Left(_), Right(bBody)) => ???
      case (Right(aBody), Left(_)) => ???
      case (Left(_), Left(_)) => ???
    }
  }

  def buildAnalysis[A, F[_]: Monad, WS[_]](
    host: Uri, hosts: (Uri, Uri), reqs: List[PartReq[A]])(
    implicit co: Comparator[A], b: SttpBackend[F, Nothing, WS]) = {
      reqs.map { partialRequest =>
        request[A, F, WS](host, hosts, partialRequest).map { 
          case Requests(candidate, primary, secondary) => {
            val primarySecondary = co.compare(primary, secondary)
            val canidatePrimary = co.compare(candidate, primary)
            Different()
          }
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
  def make[A](compareFn: (A, A) => Comparison[A]): Comparator[A] = new Comparator[A] {
    override def compare(orig: A, alt: A): Comparison[A] = compareFn(orig, alt)
  }
}
