package forex.services.rates

object errors {

  sealed trait Error {
    def message: String
  }
  object Error {
    final case class OneFrameLookupFailed(message: String) extends Error
    final case class CacheLookupFailed(message: String) extends Error
    final case class JsonParsingFailed(message: String) extends Error
  }

}
