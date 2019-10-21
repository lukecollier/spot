package spot.internals

import cats.data.ValidatedNec
import cats.data.{NonEmptyChain, Chain}
import cats.kernel.Eq
import cats.Traverse
import cats.kernel.Semigroup
import cats.TraverseFilter
import cats.data.Validated.{Invalid, Valid}
import cats.syntax.either._
import cats.implicits._

sealed trait Difference[A] {
  def message: String
}
final case class Additional[A](val ref: A) extends Difference[A] {
  def message: String = "Found element missing from original source"
}
final case class Removed[A](val ref: A) extends Difference[A] {
  def message: String = "Found element missing from original source"
}
final case class Ordering[A](val ref: A) extends Difference[A] {
  def message: String = "Found element missing from original source"
}

// convert this to an applicative typeclass
// convert chain to be a Semigroup typeclass
private[internals] sealed trait DifferenceValidatorNec {
  type Differences[A, B] = ValidatedNec[Difference[A], B]

  // TODO improve this
  private[internals] def validateOccurances[A: Eq, F[_]: Traverse : TraverseFilter](left: F[A], right: F[A]): Differences[A, F[A]] = {
    def check(el: A, amount: Int): Either[List[Difference[A]], A] = {
      if (amount > 0) {
        Left(left
          .filter{ ot => Eq[A].eqv(el, ot) }
          .fmap{ ot => Removed(ot) }
          .toList.takeRight(Math.abs(amount)))
      } 
      else if (amount < 0) {
        Left(right
          .filter{ot => Eq[A].eqv(el, ot)}
          .fmap{ot => Additional(ot)}
          .toList.takeRight(Math.abs(amount)))
      }
      else if (amount === 0) Right(el)
      else throw new RuntimeException("Unexpected")
    }

    val uniq = (left.toList |+| right.toList)
      .foldLeft(Chain[A]()){ (acc, x)=>
        if (acc.find(y=>x===y).isEmpty) acc :+ x else acc
      }

    val diffs = uniq.fmap{ x =>
      (x, left.filter{ y => y === x }.size - right.filter{ y => y === x }.size)
    }.toList.toMap

    val res = diffs.map{ case (k, v) => check(k, v.toInt) }

    res.toList.sequence match {
      case Left(_) => Invalid(NonEmptyChain.fromChainUnsafe(
        Chain.fromSeq(res.collect{case Left(v) => v}.toSeq.flatten))
      )
      case Right(_) => Valid(left)
    }
  }

  // TODO improve this
  private[internals] def validateOrder[A: Eq, F[_]: Traverse : TraverseFilter](left: F[A], right: F[A]): Differences[A, F[A]] = {
    val leftWithPrevious = left.zipWithIndex.fmap{case (v, idx) => 
      left.get((idx - 1).toLong).map{ other => 
        (other, v)
      }.getOrElse((v, v))
    }

    val rightWithPrevious = right.zipWithIndex.fmap{case (v, idx) => 
      right.get((idx - 1).toLong).map{ other => 
        (other, v)
      }.getOrElse((v, v))
    }

    val outOfOrders = leftWithPrevious.fmap{case (f, s) => 
      right.find{x => x === s} match {
        case None => Right(s)
        case Some(_) => 
          rightWithPrevious.find{case (f1, s1) => (f1 === f && s === s1)} match {
            case None => Left(Ordering(s))
            case Some(_) => Right(s)
          }
      }
    }

    outOfOrders.sequence match {
      case Left(_) => Invalid(NonEmptyChain.fromChainUnsafe(
        Chain.fromSeq(outOfOrders.collect{case Left(v) => v}.toList.toSeq)))
      case Right(_) => Valid(left)
    }
  }

  private[spot] def validate[A: Eq, F[_]: Traverse : TraverseFilter](left: F[A], right: F[A]): Differences[A, F[A]] = {
    (validateOrder(left, right), validateOccurances(left, right)) match {
      case (Valid(_), Valid(_)) => Valid(left)
      case (i@Invalid(_), Valid(_)) => i
      case (Valid(_), i@Invalid(_)) => i
      case (Invalid(e1), Invalid(e2)) => Invalid(e1 |+| e2)
    }
  }
}

object DifferenceValidatorNec extends DifferenceValidatorNec
