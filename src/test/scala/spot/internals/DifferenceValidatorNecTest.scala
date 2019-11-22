package spot.internals

import utest._
import scala.xml._
import cats.data.Validated.{Invalid, Valid}
import cats._, cats.derived._
import cats.data.Chain
import cats.implicits._


object DifferenceValidatorNecTest extends TestSuite{
  val tests = Tests{

    test("xml"){
      implicit val metaEq: Eq[MetaData] = new Eq[MetaData] {
        def eqv(x: MetaData, y: MetaData): Boolean = {
          x strict_== y
        }
      }
      implicit val nodeEq: Eq[Node] = new Eq[Node] {
        def eqv(x: Node, y: Node): Boolean = {
          x.label === y.label && x.attributes === y.attributes
        }
      }

      def listAllNodes(xml: Node): List[Node] = {
        xml.descendant_or_self.collect{case e:Elem => e}
      }

      test("same xml are equal") {
        val initial: Elem = <cars><car supplier="avis" price="£50"/></cars>
        val res = DifferenceValidatorNec.validate(listAllNodes(initial), listAllNodes(initial))
        assertMatch(res){case Valid(_) =>}
      }
      test("second xml has additions") {
        val initial: Elem = <request><cars><car price="50"/></cars></request>
        val compare: Elem = <request><cars><car price="20"/></cars></request>
        val res = DifferenceValidatorNec.validate(listAllNodes(initial), listAllNodes(compare))
          assertMatch(res){case Invalid(Chain(Removed(_), Additional(_))) =>}
      }
      test("many additions") {
        val initial: Elem = <request><cars><car price="50"/></cars></request>
        val compare: Elem = <request><cars><car price="50"/><car price="50"/><car price="50"/></cars></request>
        val res = DifferenceValidatorNec.validate(listAllNodes(initial), listAllNodes(compare))
        assertMatch(res){case Invalid(Chain(Additional(_), Additional(_))) =>}
      }
      test("nested addition") {
        val initial: Elem = <request><cars><car price="50"/></cars></request>
        val compare: Elem = <request><cars><cars><car price="50"/></cars></cars></request>
        val res = DifferenceValidatorNec.validate(listAllNodes(initial), listAllNodes(compare))
        assertMatch(res){case Invalid(Chain(Additional(_))) =>} // should be only 1 addition currently two :(
      }
      test("complex ordering change") {
        val initial: Elem = <request><cars><car price="50"/></cars></request>
        val compare: Elem = <request><cars><removed><car price="50"/></removed></cars></request>
        val res = DifferenceValidatorNec.validate(listAllNodes(initial), listAllNodes(compare))
        assertMatch(res){case Invalid(Chain(Ordering(_), Additional(_))) =>} // should be only 1 addition currently two :(
      }
    }
    test("string") {
      test("occurances and order"){
        test("are the same and in the same order") {
          val initial = "hello, world!".toList
          val compare = "hello, world!".toList
          val res = DifferenceValidatorNec.validate(initial, compare)
          assertMatch(res){case Valid(_) =>}
        }
        test("are the same but in a different order") {
          val initial = "hello, world!".toList
          val compare = "hello!, world".toList
          val res = DifferenceValidatorNec.validate(initial, compare)
          assertMatch(res){case Invalid(Chain(Ordering(_), Ordering(_))) =>}
        }
        test("are different but order is the same") {
          val initial = "hello, world!".toList
          val compare = "hello, world".toList
          val res = DifferenceValidatorNec.validate(initial, compare)
          assertMatch(res){case Invalid(Chain(Removed(_))) =>}
        }
      }
      test("occurances"){
        test("contain the same characters") {
          val initial = "hello, world!".toList
          val compare = "hello, world!".toList
          val res = DifferenceValidatorNec.validateOccurrences(initial, compare)
          assertMatch(res){case Valid(_) =>}
        }
        test("contain 1 additional character") {
          val initial = "hello, world!".toList
          val compare = "hello, world!?".toList
          val res = DifferenceValidatorNec.validateOccurrences(initial, compare)
          assertMatch(res){case Invalid(Chain(Additional(_))) =>}
        }
        test("contain 2 additional characters") {
          val initial = "hello, world".toList
          val compare = "hello, world!?".toList
          val res = DifferenceValidatorNec.validateOccurrences(initial, compare)
          assertMatch(res){case Invalid(Chain(Additional(_), Additional(_))) =>}
        }
        test("contain 1 less character") {
          val initial = "hello, world!".toList
          val compare = "hello, world".toList
          val res = DifferenceValidatorNec.validateOccurrences(initial, compare)
          assertMatch(res){case Invalid(Chain(Removed(_))) =>}
        }
        test("contain 2 less characters") {
          val initial = "hello, world!".toList
          val compare = "hello world".toList
          val res = DifferenceValidatorNec.validateOccurrences(initial, compare)
          assertMatch(res){case Invalid(Chain(Removed(_), Removed(_))) =>}
        }
        test("contain 1 less character and 1 additional character") {
          val initial = "hello, world!".toList
          val compare = "hello, world?".toList
          val res = DifferenceValidatorNec.validateOccurrences(initial, compare)
          assertMatch(res){case Invalid(Chain(Removed(_), Additional(_))) =>}
        }
        test("contain 1 less characters and 2 additional characters") {
          val initial = "hello, world!".toList
          val compare = "hello world?!!".toList
          val res = DifferenceValidatorNec.validateOccurrences(initial, compare)
          assertMatch(res){case Invalid(Chain(Additional(_), Removed(_),
          Additional(_))) =>}
        }
        test("contain 2 less characters and 1 additional characters") {
          val initial = "hello world!".toList
          val compare = "hello, world?".toList
          val res = DifferenceValidatorNec.validateOccurrences(initial, compare)
          assertMatch(res){case Invalid(Chain(Removed(_), Additional(_),
          Additional(_))) =>}
        }
        test("contains the same word twice") {
          val initial = "hello, world!".toList
          val compare = "hellohello, world!".toList
          val res = DifferenceValidatorNec.validateOccurrences(initial, compare)
          assertMatch(res){case Invalid(
            Chain(Additional(_), Additional(_), Additional(_), Additional(_),
          Additional(_))) =>}
        }
        test("is completely different") {
          val initial = "abcd".toList
          val compare = "efgh".toList
          val res = DifferenceValidatorNec.validateOccurrences(initial, compare)
          assertMatch(res){case Invalid(
            Chain(Additional(_), Additional(_), Removed(_), Removed(_),
          Additional(_), Removed(_), Removed(_), Additional(_))) =>}
        }
      }
      test("ordering"){
        test("are in a different order but the same"){
          val initial = "abcd".toList
          val compare = "dcba".toList
          val res = DifferenceValidatorNec.validateOrder(initial, compare)
          assertMatch(res){case Invalid(Chain(Ordering(_), Ordering(_),
          Ordering(_), Ordering(_))) =>}
        }
        test("order is the same"){
          val initial = "dcba".toList
          val compare = "dcba".toList
          val res = DifferenceValidatorNec.validateOrder(initial, compare)
          assertMatch(res){case Valid(_) =>}
        }
        test("order is different by one"){
          val initial = "dcba".toList
          val compare = "dcbza".toList
          val res = DifferenceValidatorNec.validateOrder(initial, compare)
          assertMatch(res){case Invalid(Chain(Ordering(_))) =>}
        }
        test("order is different by two"){
          val initial = "dcba".toList
          val compare = "dcab".toList
          val res = DifferenceValidatorNec.validateOrder(initial, compare)
          assertMatch(res){case Invalid(Chain(Ordering(_), Ordering(_))) =>}
        }
        test("word order is changed in list of strings"){
          val initial = List("hello", "there")
          val compare = List("there", "hello")
          val res = DifferenceValidatorNec.validateOrder(initial, compare)
          assertMatch(res){case Invalid(Chain(Ordering(_), Ordering(_))) =>}
        }
      }
    }
  }
}
