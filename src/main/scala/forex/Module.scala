package forex

import cats.effect.{Concurrent, Timer}
import cats.syntax.flatMap._
import forex.config.ApplicationConfig
import forex.http.rates.RatesHttpRoutes
import forex.services._
import forex.programs._
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.middleware.{AutoSlash, Timeout}

class Module[F[_]: Concurrent: Timer](config: ApplicationConfig) {
  private val ratesService: RatesService[F] = createRatesService(config)
  private val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesService)
  private val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  type TotalMiddleware = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = { http: HttpRoutes[F] =>
    AutoSlash(http)
  }

  private val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
    Timeout(config.http.timeout)(http)
  }

  private val http: HttpRoutes[F] = ratesHttpRoutes
  val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(http).orNotFound)

  def refreshRatesCache: F[Unit] = ratesService.refreshCache.flatMap {
    case Right(_) => Concurrent[F].delay(println("Rates cache refreshed successfully"))
    case Left(error) => Concurrent[F].delay(println(s"Failed to refresh rates: ${error.message}"))
  }

  private def createRatesService(config: ApplicationConfig): RatesService[F] = {
    config.oneframe.serviceMode.toLowerCase match {
      case "dummy" => RatesServices.dummy[F]
      case _ => RatesServices.live[F](config.oneframe)
    }
  }
}