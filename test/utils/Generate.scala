/*
 * Copyright 2023 HM Revenue & Customs
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

package utils

import java.util.UUID
import uk.gov.hmrc.domain.{ CtUtr, HmrcMtdItsa, Nino, SaUtr, SaUtrGenerator }
import uk.gov.hmrc.entityresolver.models.EntityId

import scala.util.Random

object Generate {
  private val seedInt: Int = 100000
  private val random = Random()
  private val saUtrGenerator = SaUtrGenerator()

  def nino: Nino = Nino(f"CE${random.nextInt(seedInt)}%06dD")

  def utr: SaUtr = saUtrGenerator.nextSaUtr

  def itsa: HmrcMtdItsa = HmrcMtdItsa("XTIT00000000001")

  def ctUtr: CtUtr = CtUtr(f"${random.nextInt(seedInt)}%06d")

  def entityId: EntityId = EntityId(UUID.randomUUID.toString)
}
