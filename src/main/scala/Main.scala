import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.functor._
import fs2.kafka._


// should be a simple check between either stdout or files contents
object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {
    def processRecord(record: ConsumerRecord[Array[Byte], Array[Byte]]): IO[(Array[Byte], Array[Byte])] =
        IO.pure(record.key -> record.value)

    val consumerSettings =
      ConsumerSettings[IO, Array[Byte], Array[Byte]]
        .withAutoOffsetReset(AutoOffsetReset.Earliest)
        .withBootstrapServers("")
        .withGroupId("group")

    val stream =
      consumerStream[IO]
        .using(consumerSettings)
        .evalTap(_.subscribeTo("pe-events-experiment"))
        .flatMap(_.stream)
        .mapAsync(25) { committable =>
          processRecord(committable.record)
            .map { case (key, value) =>
              val record = ProducerRecord("topic", key, value)
              ProducerRecords.one(record, committable.offset)
            }
        }
        .map(_.passthrough)

    stream.compile.drain.as(ExitCode.Success)
  }

}
