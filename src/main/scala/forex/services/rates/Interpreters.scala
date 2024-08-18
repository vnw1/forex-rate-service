package forex.services.rates

import cats.Applicative
import cats.effect.Concurrent
import interpreters._
import forex.config.OneFrameConfig
import forex.services.oneframe.interpreters.OneFrameLive

object Interpreters {
  def dummy[F[_]: Applicative]: Algebra[F] = new OneFrameDummy[F]()
  def live[F[_]: Concurrent](config: OneFrameConfig): Algebra[F] = 
    OneFrameLive[F](config)
}
