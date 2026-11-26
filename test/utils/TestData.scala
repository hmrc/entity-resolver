/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package utils

import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.entityresolver.models.EntityId

import java.time.Instant

object TestData {
  val FIVE = 5
  val TWO = 2

  val TEST_FAILURE_COUNT = 0

  val TEST_ID = "someId"
  val TEST_FORM_TYPE = "test_form"
  val TEST_IDENTITY_ID_VALUE = "test_identity"
  val TEST_ENTITY_ID: EntityId = EntityId("123456")
  val TEST_URL = "some url"
  val TEST_STATUS = "some status"
  val TEST_ITEM_STRING = "some item"

  val TEST_NINO_STRING = "SJ123456A"
  val TEST_NINO: Nino = Nino(TEST_NINO_STRING)

  val TEST_INSTANT: Instant = Instant.parse("2022-06-06T08:24:30.350Z")
}
