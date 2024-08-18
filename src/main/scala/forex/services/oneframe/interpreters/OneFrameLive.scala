package forex.services.oneframe.interpreters
import forex.config.OneFrameConfig
import cats.effect.{Concurrent}
import cats.implicits._
import forex.domain._
import forex.services.rates.Algebra
import forex.services.rates.errors.Error
import io.circe.generic.auto._
import io.circe.parser.decode
import sttp.client3._
import java.time.OffsetDateTime

class OneFrameLive[F[_]: Concurrent](config: OneFrameConfig)
    extends Algebra[F] {

  private val backend = HttpURLConnectionBackend()
  private val baseRequest = basicRequest.header("token", config.token)
  private val cache = scala.collection.concurrent.TrieMap.empty[Rate.Pair, Rate]

  private case class OneFrameRate(
      from: Currency,
      to: Currency,
      bid: BigDecimal,
      ask: BigDecimal,
      price: BigDecimal,
      time_stamp: String
  )
  private case class OneFrameErrorResponse(error: String)

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    cache.get(pair) match {
      case Some(rate) => Concurrent[F].pure(Right(rate))
      case None =>
        Concurrent[F].pure(Left(Error.CacheLookupFailed("No data of this pair")))
    }

  private def fetchRatesFromApi(pairs: List[Rate.Pair]): F[Either[Error, List[Rate]]] = {
    val pairParams = pairs.map(p => s"pair=${p.from}${p.to}").mkString("&")
    val url = s"${config.baseurl}/rates?$pairParams"
    println(s"Fetching rates from: $url")
    val request = baseRequest.get(uri"$url")
    Concurrent[F].delay(request.send(backend)).attempt.flatMap {
      case Right(response) => handleResponse(response)
      case Left(throwable) => Concurrent[F].pure(Left(Error.OneFrameLookupFailed(s"Request to One-Frame API failed: ${throwable.getMessage}")))
    }
  }

  private def handleResponse(response: Response[Either[String, String]]): F[Either[Error, List[Rate]]] = 
  Concurrent[F].delay {
    response.body match {
      case Right(jsonBody) =>
        decode[List[OneFrameRate]](jsonBody) match {
          case Right(rates) => 
            Right(rates.map { rate =>
              val pair = Rate.Pair(rate.from, rate.to)
              val timestamp = Timestamp(OffsetDateTime.parse(rate.time_stamp))
              val rateInstance = Rate(
                pair,
                Price(rate.price),
                Price(rate.bid),
                Price(rate.ask),
                timestamp
              )
              cache.put(pair, rateInstance)
              rateInstance
            })
          case Left(_) => 
            decode[OneFrameErrorResponse](jsonBody) match {
              case Right(errorResponse) => Left(Error.OneFrameLookupFailed(errorResponse.error))
              case Left(error) => Left(Error.JsonParsingFailed(s"Failed to parse API response: ${error.getMessage}"))
            }
          }
      case Left(error) => Left(Error.OneFrameLookupFailed(s"Unexpected response body: $error"))
    }
  }

  override def refreshCache: F[Either[Error, Unit]] = 
    fetchAllRates.flatMap {
      case Right(_) => Concurrent[F].pure(Right(()))
      case Left(error) => Concurrent[F].pure(Left(error))
    }

  private def fetchAllRates: F[Either[Error, List[Rate]]] = {
    val pairs = generateAllPairs
    fetchRatesFromApi(pairs).map {
      case Right(rates) => Right(rates)
      case Left(error) => Left(error)
    }
  }

  private def generateAllPairs: List[Rate.Pair] = {
    val currencies = Currency.values
    (for {
      from <- currencies
      to <- currencies if from != to
    } yield Rate.Pair(from, to)).toList
  }
}

object OneFrameLive {
  def apply[F[_]: Concurrent](config: OneFrameConfig): OneFrameLive[F] =
    new OneFrameLive[F](config)
}
