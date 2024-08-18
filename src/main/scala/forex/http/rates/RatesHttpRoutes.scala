package forex.http
package rates

import cats.effect.Sync
import cats.syntax.flatMap._
import forex.programs.RatesProgram
import forex.programs.rates.{ Protocol => RatesProgramProtocol }
import forex.programs.rates.errors.Error
import org.http4s.Response

import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router

class RatesHttpRoutes[F[_]: Sync](rates: RatesProgram[F]) extends Http4sDsl[F] {
  import Converters._, QueryParams._, Protocol._

  private[http] val prefixPath = "/rates"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
  case GET -> Root :? FromQueryParam(fromResult) +& ToQueryParam(toResult) =>
    (fromResult, toResult) match {
      case (Right(from), Right(to)) =>
        println(s"RatesHttpRoutes: Received request for $from to $to")
        rates.get(RatesProgramProtocol.GetRatesRequest(from, to)).flatMap {
          case Right(rate) => Ok(rate.asGetApiResponse)
          case Left(error) => handleError(error)
        }
      case (Left(fromError), _) =>
        println(s"Invalid 'from' currency: ${fromError.sanitized}")
        BadRequest(s"Invalid 'from' currency: ${fromError.details}")
      case (_, Left(toError)) =>
        println(s"Invalid 'from' currency: ${toError.sanitized}")
        BadRequest(s"Invalid 'to' currency: ${toError.details}")
    }
}

private def handleError(error: Error): F[Response[F]] = error match {
  case Error.RateLookupFailed(msg) => NotFound(s"Rate not found: $msg")
  case _ => InternalServerError("An unexpected error occurred. Please try again later.")
}

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )
}