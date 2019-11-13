package spot.compare

import cats.effect.{ContextShift, IO}
import sttp.client._
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import sttp.client.testing.SttpBackendStub
import sttp.model.StatusCode
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
      test("request can be transformed") {
        val first: Response[Either[String, List[String]]] =
          Response(Right(List("first","second")), StatusCode.Ok, "OK")
        val second: Response[Either[String, List[String]]] =
          Response(Right(List("first", "second")), StatusCode.Ok, "OK")

        val res = compare[List[String]](first, second)(Comparator.listString)
        val exp = (Same(), Same())
        assert(res == exp)

     }
    }
  }
}
