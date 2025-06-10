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

package org.typelevel.catapult.codec

import cats.Show
import cats.data.Chain
import cats.kernel.{Hash, Monoid}
import cats.syntax.all.*
import org.typelevel.catapult.codec.LDCursorHistory.Move
import org.typelevel.catapult.codec.LDCursorHistory.Move.{Field, Index}

sealed trait LDCursorHistory {
  def moves: Chain[Move]
  def at(field: String): LDCursorHistory
  def at(index: Int): LDCursorHistory

  override def hashCode(): Int = LDCursorHistory.hash.hash(this)
  override def equals(obj: Any): Boolean = obj match {
    case that: LDCursorHistory => LDCursorHistory.hash.eqv(this, that)
    case _ => false
  }
}
object LDCursorHistory {
  val root: LDCursorHistory = new HistoryImpl(Chain.empty)
  def of(moves: Chain[Move]): LDCursorHistory = new HistoryImpl(moves)

  implicit val show: Show[LDCursorHistory] = Show.fromToString
  implicit val hash: Hash[LDCursorHistory] = Hash.by(_.moves)
  implicit val monoid: Monoid[LDCursorHistory] =
    Monoid.instance(root, (a, b) => new HistoryImpl(a.moves.concat(b.moves)))

  private final class HistoryImpl(val moves: Chain[Move]) extends LDCursorHistory {
    override def at(field: String): LDCursorHistory = new HistoryImpl(moves.append(Move(field)))

    override def at(index: Int): LDCursorHistory = new HistoryImpl(moves.append(Move(index)))

    override def toString: String = moves.mkString_("$", "", "")
  }

  sealed trait Move {
    override def toString: String = this match {
      case f: Field if f.name.forall(c => c.isLetterOrDigit || c == '_' || c == '-') =>
        s".${f.name}"
      case f: Field => s"[${f.name}]"
      case i: Index => s"[${i.index}]"
    }

    override def hashCode(): Int = Move.hash.hash(this)

    override def equals(obj: Any): Boolean = obj match {
      case that: Move => Move.hash.eqv(this, that)
      case _ => false
    }
  }
  object Move {
    def apply(name: String): Move = new Field(name)
    def apply(index: Int): Move = new Index(index)

    final class Field(val name: String) extends Move

    final class Index(val index: Int) extends Move

    implicit val show: Show[Move] = Show.fromToString
    implicit val hash: Hash[Move] = Hash.by {
      case field: Field => ("field", field.name, 0)
      case index: Index => ("index", "", index.index)
    }
  }
}
