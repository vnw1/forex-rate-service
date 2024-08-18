package forex

import cats.effect._
import fs2.Stream
import org.http4s.blaze.server.BlazeServerBuilder
import forex.config.{Config, ApplicationConfig}

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
      refresh = periodicRefresh(module, config)
      _ <- server.concurrently(refresh)
    } yield ()
  }

  private def periodicRefresh(module: Module[IO], config: ApplicationConfig): Stream[IO, Unit] = Stream
    .awakeEvery[IO](config.oneframe.refreshInterval)
    .map(_ => println("Refreshing rates cache..."))
    .evalMap(_ => module.refreshRatesCache)
}
