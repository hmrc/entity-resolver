/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.models

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.{ JsResultException, JsString, JsValue, Json }
import uk.gov.hmrc.domain.Nino
import utils.TestData.{ TEST_FAILURE_COUNT, TEST_FORM_TYPE, TEST_ID, TEST_IDENTITY_ID_VALUE, TEST_INSTANT, TEST_ITEM_STRING, TEST_NINO, TEST_STATUS, TEST_URL }

import java.time.Instant

class WorkItemSpec extends PlaySpec {
  import WorkItemFormats._

  "WorkItemFormats" should {

    "convert Json to NoticeOfCodingWorkItem" in new Setup {
      json.as[NoticeOfCodingWorkItem[String]] must be(noc)
    }

    "convert NoticeOfCodingWorkItem to Json" in new Setup {
      Json.toJson(noc) must be(json)
    }

    "convert DateTime to Json" in new Setup {
      Json.toJson(TEST_INSTANT) must be(JsString("2022-06-06T08:24:30.350Z"))
    }

    "throw an exception  parsing a date if it is not a valid date" in new Setup {
      intercept[JsResultException] {
        JsString("error").as[Instant]
      }
    }
  }

  "WorkItemFormats.ninoItemFormats" should {
    import WorkItemFormats.ninoItemFormats

    "read the json correctly" in new Setup {
      Json.parse(ninoItemJsonString).as[NinoItem] mustBe ninoItem
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(ninoItemInvalidJsonString).as[NinoItem]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(ninoItem) mustBe Json.parse(ninoItemJsonString)
    }
  }

  "WorkItemFormats.entityIdItemFormats" should {
    import WorkItemFormats.entityIdItemFormats

    "read the json correctly" in new Setup {
      Json.parse(entityIdItemJsonString).as[EntityIdItem] mustBe entityIdItem
    }

    "throw the exception for invalid json" in new Setup {
      intercept[JsResultException] {
        Json.parse(entityIdItemInvalidJsonString).as[EntityIdItem]
      }
    }

    "write the object correctly" in new Setup {
      Json.toJson(entityIdItem) mustBe Json.parse(entityIdItemJsonString)
    }
  }

  "NoticeOfCodingWorkItem.replaceItem" should {

    "replace the item correctly" in new Setup {
      noc.replaceItem("updated item") mustBe replacedNoc
    }
  }

  trait Setup {
    val ninoItem: NinoItem = NinoItem(nino = TEST_NINO, formType = TEST_FORM_TYPE, suppressible = true)
    val ninoItemJsonString: String = """{"nino":"SJ123456A","formType":"test_form","suppressible":true}""".stripMargin

    val ninoItemInvalidJsonString: String = """{"nino":"SJ123456A","formType":"test_form"}""".stripMargin

    val entityId: EntityId = EntityId(TEST_IDENTITY_ID_VALUE)
    val entityIdItem: EntityIdItem = EntityIdItem(entityId = entityId, suppressible = true)
    val entityIdItemJsonString: String = """{"entityId":"test_identity","suppressible":true}""".stripMargin
    val entityIdItemInvalidJsonString: String = """{"entityId":"test_identity"}""".stripMargin

    val noc: NoticeOfCodingWorkItem[String] =
      NoticeOfCodingWorkItem(
        TEST_ID,
        TEST_INSTANT,
        TEST_INSTANT,
        TEST_INSTANT,
        TEST_STATUS,
        TEST_FAILURE_COUNT,
        TEST_URL,
        TEST_ITEM_STRING
      )

    val replacedNoc: NoticeOfCodingWorkItem[String] =
      NoticeOfCodingWorkItem(
        TEST_ID,
        TEST_INSTANT,
        TEST_INSTANT,
        TEST_INSTANT,
        TEST_STATUS,
        TEST_FAILURE_COUNT,
        TEST_URL,
        "updated item"
      )

    val json: JsValue =
      Json.parse("""{
          "id" : "someId",
          "updatedAt" : "2022-06-06T08:24:30.350Z",
          "receivedAt" : "2022-06-06T08:24:30.350Z",
          "availableAt" : "2022-06-06T08:24:30.350Z",
          "status" : "some status",
          "failureCount" : 0,
          "statusUrl" : "some url",
          "item" : "some item"
          }""")
  }
}
