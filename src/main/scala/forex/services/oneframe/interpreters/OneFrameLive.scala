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

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    cache.get(pair) match {
      case Some(rate) => Concurrent[F].pure(Right(rate))
      case None =>
        Concurrent[F].pure(Left(Error.OneFrameLookupFailed("Invalid pair")))
    }

  private def fetchRatesFromApi(pairs: List[Rate.Pair]): F[List[Rate]] = {
    val pairParams = pairs.map(p => s"pair=${(p.from)}${(p.to)}").mkString("&")
    val url = s"${config.baseurl}/rates?$pairParams"
    print(url)
    val request = baseRequest.get(uri"$url")

    Concurrent[F].delay(request.send(backend)).flatMap { response =>
      handleResponse(response)
    }
  }

  private def handleResponse(
      response: Response[Either[String, String]]
  ): F[List[Rate]] =
    Concurrent[F].delay {
      response.body match {
        case Right(jsonBody) => parseJsonResponse(jsonBody)
        case Left(error) =>
          throw new RuntimeException(s"Request to One-Frame API failed: $error")
      }
    }

  private def parseJsonResponse(jsonBody: String): List[Rate] = {
    decode[List[OneFrameRate]](jsonBody) match {
      case Right(rates) =>
        rates.map { rate =>
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
        }
      case Left(error) =>
        println(s"Failed to parse JSON: $jsonBody")
        throw new RuntimeException(
          s"Failed to parse API response: ${error.getMessage}"
        )
    }
  }

  def refreshCache: F[Unit] =
    fetchAllRates.void

  private def fetchAllRates: F[List[Rate]] = {
    val pairs = generateAllPairs
    fetchRatesFromApi(pairs)
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
