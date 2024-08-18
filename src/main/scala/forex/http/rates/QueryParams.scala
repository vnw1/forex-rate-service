package forex.http.rates

import forex.domain.Currency
import org.http4s.{ParseFailure, QueryParamDecoder}
import org.http4s.dsl.impl.QueryParamDecoderMatcher

object QueryParams {
  private[http] implicit val currencyQueryParamDecoder: QueryParamDecoder[Either[ParseFailure, Currency]] =
    QueryParamDecoder[String].emap { s =>
      Currency.fromString(s) match {
        case Some(currency) => Right(Right(currency))
        case None => Right(Left(ParseFailure("Invalid currency", s"'$s' is not a valid currency")))
      }
    }

  object FromQueryParam extends QueryParamDecoderMatcher[Either[ParseFailure, Currency]]("from")
  object ToQueryParam extends QueryParamDecoderMatcher[Either[ParseFailure, Currency]]("to")
}