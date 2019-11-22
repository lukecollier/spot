package spot

import java.nio.file.{NoSuchFileException, Path}

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits._
import fs2.{Stream, text}
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.rogach.scallop.ScallopConf
import spot.internals.DifferenceValidatorNec

// should be a simple check between either stdout or files contents
object Main extends IOApp {
  sealed trait Contents
  final case class FileNotFound(f: Throwable) extends Contents
  final case class Success(lines: List[String]) extends Contents

  object FileErrors {
    def apply(f: Throwable): Contents = {
      f match {
        case _: NoSuchFileException => FileNotFound(f)
        case _ => throw f
      }
    }
  }

  case class Conf(arguments: Seq[String]) extends ScallopConf(arguments) {
    val output = opt[Path]()
    val firstFile = trailArg[Path](required = true)
    val comparedFile = trailArg[Path](required = true)
    verify()
  }

  def toLines(path: Path): IO[Contents] = Stream.resource(Blocker[IO]).flatMap( blocker =>
    fs2.io.file.readAll[IO](path, blocker, 2048).through(text.utf8Decode).through(text.lines)
  ).compile.toList
    .map[Contents](l => Success(l))
    .handleError(f => FileErrors(f))

  def exitCode(validated: Validated[_, _]): IO[ExitCode] =
    validated match {
      case Valid(_) => IO { ExitCode.Success }
      case Invalid(_) => exitWithMsg(ExitCode.Error, "differences found")
    }

  def exitWithMsg(code: ExitCode, message: String): IO[ExitCode] = {
    for {
      logger <- Slf4jLogger.create[IO]
      _ <- logger.error(message)
      code <- IO.pure(code)
    } yield code
  }

  def checkContents(first: Contents, second: Contents): Validated[_, _] = {
    (first, second) match {
      case (Success(fi), Success(se)) => DifferenceValidatorNec.validate(fi, se)
      case (FileNotFound(f), _) => throw f
      case (_, FileNotFound(f)) => throw f
    }
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val conf = Conf(args)

    (for {
      logger <- Slf4jLogger.create[IO]
      _ <- logger.debug("logger successfully initialized")
      _ <- logger.debug(s"command line config given as $conf")
      first <- toLines(conf.firstFile.apply())
      second <- toLines(conf.firstFile.apply())
      res <- exitCode(checkContents(first, second))
    } yield res)
  }
}
