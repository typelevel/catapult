package catapult

import com.launchdarkly.sdk.{LDContext, LDUser}

trait ContextEncoder[Ctx] {
  def encode(ctx: Ctx): LDContext
}

object ContextEncoder {
  implicit val catapultContextEncoderForLdContext: ContextEncoder[LDContext] = identity

  implicit val catapultContextEncoderForLdUser: ContextEncoder[LDUser] = LDContext.fromUser
}
