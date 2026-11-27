/*
 * Copyright 2024 HM Revenue & Customs
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
