/*
 * Copyright 2022 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.typelevel.catapult

import cats.Show
import cats.kernel.Hash
import com.launchdarkly.sdk.LDValue

object instances {
  implicit val catapultCatsInstancesForLDValue: Hash[LDValue] & Show[LDValue] = new Hash[LDValue]
    with Show[LDValue] {
    override def hash(x: LDValue): Int = x.##

    override def eqv(x: LDValue, y: LDValue): Boolean =
      // Need to do both directions because LDValue.equals is asymmetric for objects
      x == y && y == x

    override def show(t: LDValue): String = t.toJsonString
  }
}
