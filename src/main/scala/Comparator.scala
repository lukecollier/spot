package spot.compare

import spot.internals.{DifferenceValidatorNec}

case class Comparator[F[_]](left: F[_]) {
}
