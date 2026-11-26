/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.binders

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.domain._
import uk.gov.hmrc.entityresolver.binders.Binders._
import uk.gov.hmrc.entityresolver.models.EntityId

class BindersSpec extends PlaySpec {
  "Binders" should {
    "saUtrBinder bind" in {
      saUtrBinder.bind("key", "someval") must be(Right(SaUtr("someval")))
    }

    "saUtrBinder unbind" in {
      saUtrBinder.unbind("key", SaUtr("someval")) must be("someval")
    }

    "ninoBinder bind" in {
      ninoBinder.bind("key", "SP342312A") must be(Right(Nino("SP342312A")))
    }

    "ninoBinder unbind" in {
      ninoBinder.unbind("key", Nino("SP342312A")) must be("SP342312A")
    }

    "itsaBinder bind" in {
      itsaBinder.bind("key", "SP342312A") must be(Right(HmrcMtdItsa("SP342312A")))
    }

    "itsaBinder unbind" in {
      itsaBinder.unbind("key", HmrcMtdItsa("SP342312A")) must be("SP342312A")
    }

    "entityIdBinder bind" in {
      entityIdBinder.bind("key", "SP342312A") must be(Right(EntityId("SP342312A")))
    }

    "entityIdBinder unbind" in {
      entityIdBinder.unbind("key", EntityId("SP342312A")) must be("SP342312A")
    }

    /*    "BSONObjectIdBinder bind" in {
      BSONObjectIdBinder.bind("key", Map("key" -> List("123456789ABC123456789ABC"))) must be(
        Some(Right(BSONObjectID.parse("123456789abc123456789abc").get)))
    }

    "BSONObjectIdBinder bind if invalid ID" in {
      BSONObjectIdBinder.bind("key", Map("key" -> List("123456789ABC123456"))) must be(Some(
        Left("Cannot parse parameter 'key' with parameters 'Map(key -> List(123456789ABC123456))' as 'BSONObjectID'")))
    }

    "BSONObjectIdBinder unbind" in {
      BSONObjectIdBinder.unbind("key", BSONObjectID.parse("123456789ABC123456789ABC").get) must be(
        "key=123456789abc123456789abc")
    }*/
  }

}
