package org.typelevel.catapult

import cats.Show
import cats.kernel.Hash
import cats.syntax.all.*
import com.launchdarkly.sdk.server.interfaces.FlagValueChangeEvent
import org.typelevel.catapult.codec.LDCodec
import org.typelevel.catapult.codec.LDCodec.LDCodecResult

trait FlagValueChanged[A] {
  def oldValue: A
  def newValue: A
}
object FlagValueChanged {
  def apply[A](oldValue: A, newValue: A): FlagValueChanged[A] =
    new Impl[A](oldValue = oldValue, newValue = newValue)

  def apply[A](
      event: FlagValueChangeEvent
  )(implicit codec: LDCodec[A]): LDCodecResult[FlagValueChanged[A]] =
    (
      codec.decode(event.getOldValue),
      codec.decode(event.getNewValue),
    ).mapN(apply[A])

  private final class Impl[A](override val oldValue: A, override val newValue: A)
      extends FlagValueChanged[A] {
    override def toString: String = s"FlagValueChanged($oldValue, $newValue)"
  }

  implicit def show[A: Show]: Show[FlagValueChanged[A]] = Show.show { fvc =>
    show"FlagValueChanged(${fvc.oldValue}, ${fvc.newValue})"
  }
  implicit def hash[A: Hash]: Hash[FlagValueChanged[A]] =
    Hash.by(fvc => (fvc.oldValue, fvc.newValue))
}
