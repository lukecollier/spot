package spot

import cats.effect.{ContextShift, IO}
import sttp.client._
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client.testing.SttpBackendStub
import sttp.model.{Header, StatusCode}
import utest._
import cats.implicits._
import cats.data._

import scala.concurrent.ExecutionContext
import spot.Compare.{compare, runAnalysis}
import spot.internals.{Additional, Removed}

// todo fix
// Thread[AsyncHttpClient-timer-1-1,5,main] loading org.asynchttpclient.util.DateUtils after test or run has completed. This is a likely resource leak.
object CompareTest extends TestSuite{
  val tests = Tests {
    test("can make requests") {
      type TestResponse = Response[Either[String, Array[Byte]]]
      val candidateResponse: TestResponse = Response(Right(Array(1)), StatusCode.Ok, "OK")
      val firstResponse: TestResponse = Response(Right(Array(2)), StatusCode.Ok, "OK")
      val secondResponse: TestResponse = Response(Right(Array(3)), StatusCode.Ok, "OK")

      implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
      AsyncHttpClientCatsBackend[cats.effect.IO]().map {
        backend =>
          SttpBackendStub(backend)
          .whenRequestMatches(_.uri == uri"www.candidate.com")
          .thenRespondWrapped(_ => IO.pure(candidateResponse))
          .whenRequestMatches(_.uri == uri"www.first.com")
          .thenRespondWrapped(_ => IO.pure(firstResponse))
          .whenRequestMatches(_.uri == uri"www.second.com")
          .thenRespondWrapped(_ => IO.pure(secondResponse))
      }.flatMap { backend: SttpBackendStub[IO, Nothing] =>
        Compare.request(uri"www.candidate.com", (uri"www.first.com", uri"www.second.com"))(backend)
      }.map {
        case Compare.Requests(candidate, first, second) => assert(
          candidate == candidateResponse, first == firstResponse, second == secondResponse)
      }.unsafeToFuture()
    }
    test("compare requests") {
      test("can be the same") {
        val first: Response[Either[String, Array[Byte]]] =
          Response(Right(Array(0, 1, 2)), StatusCode.Ok, "OK")
        val second: Response[Either[String, Array[Byte]]] =
          Response(Right(Array(0, 1, 2)), StatusCode.Ok, "OK")

        val res = compare[Array[Byte]](first, second)(Comparator.byteArray)
        val exp = (Same, Same)
        assert(res == exp)

      }
      test("body can be different") {
        val removed: Byte = 2
        val first: Response[Either[String, Array[Byte]]] =
          Response(Right(Array(0, 1, removed)), StatusCode.Ok, "OK")
        val second: Response[Either[String, Array[Byte]]] =
          Response(Right(Array(0, 1)), StatusCode.Ok, "OK")

        val res = compare[Array[Byte]](first, second)(Comparator.byteArray)
        val exp = (Different(NonEmptyChain(Removed(removed))), Same)
        assert(res == exp)
      }
      test("header can be different") {
        val base: Response[Either[String, Array[Byte]]] =
          Response(Right(Array(1, 2, 3)), StatusCode.Ok, "OK")

        val firstHeader = Header("X-experiment-seed", "123")
        val secondHeader = Header("X-experiment-seed", "124")
        val first = base.copy(headers = Seq(firstHeader))
        val second = base.copy(headers = Seq(secondHeader))

        val res = compare[Array[Byte]](first, second)(Comparator.byteArray)
        val exp = (Same, Different(NonEmptyChain(Removed(firstHeader), Additional(secondHeader))))
        assert(res == exp)
      }
    }
    test("can run analysis of requests") {
      test("same requests are the same") {
        type TestResponse = Response[Either[String, Array[Byte]]]
        val candidateResponse: TestResponse = Response(Right(Array(0)), StatusCode.Ok, "OK")
        val firstResponse: TestResponse = Response(Right(Array(0)), StatusCode.Ok, "OK")
        val secondResponse: TestResponse = Response(Right(Array(0)), StatusCode.Ok, "OK")
        implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
        AsyncHttpClientCatsBackend[cats.effect.IO]().map {
          backend =>
            SttpBackendStub(backend)
              .whenRequestMatches(_.uri == uri"www.candidate.com")
              .thenRespondWrapped(_ => IO.pure(candidateResponse))
              .whenRequestMatches(_.uri == uri"www.first.com")
              .thenRespondWrapped(_ => IO.pure(firstResponse))
              .whenRequestMatches(_.uri == uri"www.second.com")
              .thenRespondWrapped(_ => IO.pure(secondResponse))
        }.flatMap { backend: SttpBackendStub[IO, Nothing] =>
          runAnalysis(uri"www.candidate.com", (uri"www.first.com", uri"www.second.com"),
            List(basicRequest.response(asByteArray)))(Comparator.byteArray, backend).sequence
        }.map {
          assertMatch(_) { case List((Same, Same)) => }
        }.unsafeToFuture()
      }
      test("different requests give a difference") {
        type TestResponse = Response[Either[String, Array[Byte]]]
        val additional: Byte = 3
        val candidateResponse: TestResponse = Response(Right(Array(0,1,2,additional)), StatusCode.Ok, "OK")
        val firstResponse: TestResponse = Response(Right(Array(0,1,2)), StatusCode.Ok, "OK")
        val secondResponse: TestResponse = Response(Right(Array(0,1,2)), StatusCode.Ok, "OK")
        implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
        AsyncHttpClientCatsBackend[cats.effect.IO]().map {
          backend =>
            SttpBackendStub(backend)
              .whenRequestMatches(_.uri == uri"www.candidate.com")
              .thenRespondWrapped(_ => IO.pure(candidateResponse))
              .whenRequestMatches(_.uri == uri"www.first.com")
              .thenRespondWrapped(_ => IO.pure(firstResponse))
              .whenRequestMatches(_.uri == uri"www.second.com")
              .thenRespondWrapped(_ => IO.pure(secondResponse))
        }.flatMap { backend: SttpBackendStub[IO, Nothing] =>
          runAnalysis(uri"www.candidate.com", (uri"www.first.com", uri"www.second.com"),
              List(basicRequest.response(asByteArray)))(Comparator.byteArray, backend).sequence
        }.map {
          assertMatch(_) { case List((Different(Chain(Removed(additional))), Same)) => }
        }.unsafeToFuture()
      }
      test("first and second response have difference in body but are the same") {
        type TestResponse = Response[Either[String, Array[Byte]]]
        val candidateResponse: TestResponse = Response(Right(Array(0, 0, 2)), StatusCode.Ok, "OK")
        val firstResponse: TestResponse = Response(Right(Array(0, 0, 1)), StatusCode.Ok, "OK")
        val secondResponse: TestResponse = Response(Right(Array(0, 0, 2)), StatusCode.Ok, "OK")
        implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
        AsyncHttpClientCatsBackend[cats.effect.IO]().map {
          backend =>
            SttpBackendStub(backend)
              .whenRequestMatches(_.uri == uri"www.candidate.com")
              .thenRespondWrapped(_ => IO.pure(candidateResponse))
              .whenRequestMatches(_.uri == uri"www.first.com")
              .thenRespondWrapped(_ => IO.pure(firstResponse))
              .whenRequestMatches(_.uri == uri"www.second.com")
              .thenRespondWrapped(_ => IO.pure(secondResponse))
        }.flatMap { backend: SttpBackendStub[IO, Nothing] =>
          runAnalysis(uri"www.candidate.com", (uri"www.first.com", uri"www.second.com"),
              List(basicRequest.response(asByteArray)))(Comparator.byteArray, backend).sequence
        }.map {
          assertMatch(_) {case List((Same, Same)) => }
        }.unsafeToFuture()
      }
      test("can detect a change in the headers") {
        type TestResponse = Response[Either[String, Array[Byte]]]
        val candidate: TestResponse = Response(Right(Array(0,0,2)), StatusCode.Ok, "OK")
        val base: TestResponse = Response(Right(Array(0,0,2)), StatusCode.Ok, "OK")
        implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
        val firstHeader = Header("X-experiment-seed", "123")
        val secondHeader = Header("X-experiment-seed", "124")
        val candidateResponse = candidate.copy(headers = Seq(secondHeader))
        val firstSecondResponse = base.copy(headers = Seq(firstHeader))
        AsyncHttpClientCatsBackend[cats.effect.IO]().map {
          backend =>
            SttpBackendStub(backend)
              .whenRequestMatches(_.uri == uri"www.candidate.com")
              .thenRespondWrapped(_ => IO.pure(candidateResponse))
              .whenRequestMatches(_.uri == uri"www.first.com")
              .thenRespondWrapped(_ => IO.pure(firstSecondResponse))
              .whenRequestMatches(_.uri == uri"www.second.com")
              .thenRespondWrapped(_ => IO.pure(firstSecondResponse))
        }.flatMap { backend: SttpBackendStub[IO, Nothing] =>
          runAnalysis(uri"www.candidate.com", (uri"www.first.com", uri"www.second.com"),
              List(basicRequest.response(asByteArray)))(Comparator.byteArray, backend).sequence
        }.map {
          assertMatch(_) {case List((Same,
            Different(Chain(Removed(firstHeader), Additional(secondHeader))))) => }
        }.unsafeToFuture()
      }
    }
  }
}
