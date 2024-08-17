package forex

import cats.effect._
import fs2.Stream
import org.http4s.blaze.server.BlazeServerBuilder
import forex.config.{Config}

import scala.concurrent.duration._

object Main extends IOApp {

  override def run(args: List[String]): IO[ExitCode] =
    stream.compile.drain.as(ExitCode.Success)

  private def stream: Stream[IO, Unit] = {
    for {
      config <- Config.stream[IO]("app")
      module = new Module[IO](config)
      _ <- Stream.eval(module.refreshRatesCache)
      server = BlazeServerBuilder[IO](executionContext)
                 .bindHttp(config.http.port, config.http.host)
                 .withHttpApp(module.httpApp)
                 .serve
      refresh = periodicRefresh(module)
      _ <- server.concurrently(refresh)
    } yield ()
  }

  private def periodicRefresh(module: Module[IO]): Stream[IO, Unit] =
    Stream.awakeEvery[IO](2.minutes).evalMap(_ => module.refreshRatesCache)
}