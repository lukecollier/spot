package spot.compare

import cats.effect.{ContextShift, IO}
import sttp.client._
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client.testing.SttpBackendStub
import sttp.model.{Header, StatusCode}
import utest._

import scala.concurrent.ExecutionContext
import spot.compare.Compare.compare


// todo fix
// Thread[AsyncHttpClient-timer-1-1,5,main] loading org.asynchttpclient.util.DateUtils after test or run has completed. This is a likely resource leak.
object CompareTest extends TestSuite{
  val tests = Tests {
    test("can make requests") {
      val candidateResponse: Response[Either[String, String]] = Response(Right("candidate"), StatusCode.Ok, "OK")
      val firstResponse: Response[Either[String, String]] = Response(Right("first"), StatusCode.Ok, "OK")
      val secondResponse: Response[Either[String, String]] = Response(Right("second"), StatusCode.Ok, "OK")

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
        val exp = (Same(), Same())
        assert(res == exp)

      }
      test("body can be different") {
        val first: Response[Either[String, Array[Byte]]] =
          Response(Right(Array(0, 1, 2)), StatusCode.Ok, "OK")
        val second: Response[Either[String, Array[Byte]]] =
          Response(Right(Array(0, 1)), StatusCode.Ok, "OK")

        val res = compare[Array[Byte]](first, second)(Comparator.byteArray)
        val exp = (Different(), Same())
        assert(res == exp)
      }
      test("header can be different") {
        val base: Response[Either[String, Array[Byte]]] =
          Response(Right(Array(1, 2, 3)), StatusCode.Ok, "OK")

        val first = base.copy(headers = Seq(Header("X-experiment-seed", "123")))
        val second = base.copy(headers = Seq(Header("X-experiment-seed", "124")))

        val res = compare[Array[Byte]](first, second)(Comparator.byteArray)
        val exp = (Same(), Different())
        assert(res == exp)
      }
    }

    }
}
