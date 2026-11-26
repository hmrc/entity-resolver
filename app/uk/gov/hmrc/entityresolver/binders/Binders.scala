/*
 * Copyright 2024 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.binders

import uk.gov.hmrc.domain.{ HmrcMtdItsa, Nino, SaUtr }
import uk.gov.hmrc.entityresolver.models.EntityId

object Binders {
  implicit val saUtrBinder: SimpleObjectBinder[SaUtr] = new SimpleObjectBinder[SaUtr](SaUtr.apply, _.value)
  implicit val ninoBinder: SimpleObjectBinder[Nino] = new SimpleObjectBinder[Nino](Nino.apply, _.value)
  implicit val itsaBinder: SimpleObjectBinder[HmrcMtdItsa] =
    new SimpleObjectBinder[HmrcMtdItsa](HmrcMtdItsa.apply, _.value)
  implicit val entityIdBinder: SimpleObjectBinder[EntityId] = new SimpleObjectBinder[EntityId](EntityId.apply, _.value)
}
