package forex.domain

import enumeratum._
import enumeratum.CirceEnum
import scala.collection.immutable
import cats.Show

sealed trait Currency extends EnumEntry

object Currency extends Enum[Currency] with CirceEnum[Currency] {
  case object AUD extends Currency
  case object CAD extends Currency
  case object CHF extends Currency
  case object EUR extends Currency
  case object GBP extends Currency
  case object NZD extends Currency
  case object JPY extends Currency
  case object SGD extends Currency
  case object USD extends Currency

  val values: immutable.IndexedSeq[Currency] = findValues

  def fromString(s: String): Option[Currency] = withNameOption(s.toUpperCase)

  implicit val show: Show[Currency] = Show.show(_.entryName)

  val allPairs: List[Rate.Pair] = {
    values.toList.flatMap { from =>
      values.filterNot(_ == from).map { to =>
        Rate.Pair(from, to)
      }
    }
  }
}