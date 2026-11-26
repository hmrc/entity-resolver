/*
 * Copyright 2023 HM Revenue & Customs
 *
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
