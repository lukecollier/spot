package spot

import utest.{TestSuite, Tests, test}

import scala.io.Source

object MainTest extends TestSuite{
  val tests = Tests {
    val one = getClass.getResource("/one.json")
    val two = getClass.getResource("/two.json")
    test("can compare two files") {
      println(one, two)
      Main.run(List(one.getPath, one.getPath)).map(ec => ec.code == 0).unsafeToFuture()
    }
    test("can detect changes between two files") {
      Main.run(List(one.getPath, two.getPath)).map(ec => ec.code == 1).unsafeToFuture()
    }
  }
}
