package catapult

import com.launchdarkly.sdk.{LDContext, LDUser}

/** A typeclass for converting values of type [[Ctx]] into [[LDContext]]. An instance must be in scope when
  * evaulating flags against a context represented by the [[Ctx]] type. Instances are provided for [[LDContext]]
  * and [[LDUser]]; custom instances can be created to allow other types to be used.
  */
trait ContextEncoder[Ctx] {
  def encode(ctx: Ctx): LDContext
}

object ContextEncoder {
  implicit val catapultContextEncoderForLdContext: ContextEncoder[LDContext] = identity

  implicit val catapultContextEncoderForLdUser: ContextEncoder[LDUser] = LDContext.fromUser
}
