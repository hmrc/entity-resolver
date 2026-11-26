/*
 * Copyright 2025 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.binders

import org.scalatestplus.play.PlaySpec
import uk.gov.hmrc.entityresolver.models.EntityId
import play.api.mvc.QueryStringBindable

class GetEntityParamsBinderSpec extends PlaySpec {

  "GetEntityParamsBinder querystring binder" should {
    val bindable = implicitly[QueryStringBindable[GetEntityParamsBinder]]

    "bind a valid entityId params" in {
      val eid = EntityId.generate()
      val params: Map[String, Seq[String]] = Map("entityId" -> Seq(eid.value))
      bindable.bind("", params) mustBe Some(
        Right(GetEntityParamsBinder(None, None, Some(eid.value), None))
      )
    }

    "bind invalid key" in {
      val params = Map("taxRegimey" -> Seq("nino"), "taxId" -> Seq("AB112233A"))
      bindable.bind("", params) mustBe Some(
        Right(GetEntityParamsBinder(None, Some("AB112233A"), None, None))
      )
    }

    "bind a valid taxid params" in {
      val params: Map[String, Seq[String]] = Map("taxRegime" -> Seq("nino"), "taxId" -> Seq("AB112233A"))
      bindable.bind("", params) mustBe Some(
        Right(GetEntityParamsBinder(Some("nino"), Some("AB112233A"), None, None))
      )
    }

    "bind no resolve params" in {
      val params = Map("" -> Seq(""))
      bindable.bind("", params) mustBe Some(Right(GetEntityParamsBinder(None, None, None, None, None)))
    }

    "bind bad key" in {
      val params = Map("re5olve" -> Seq("true"))
      bindable.bind("", params) mustBe Some(Right(GetEntityParamsBinder(None, None, None, None, None)))
    }

    "bind a valid resolve params" in {
      val params = Map("resolve" -> Seq("true"))
      bindable.bind("", params) mustBe Some(Right(GetEntityParamsBinder(None, None, None, Some(true), None)))
    }

    "bind a valid resolve params when resolve false" in {
      val params = Map("resolve" -> Seq("false"))
      bindable.bind("", params) mustBe Some(Right(GetEntityParamsBinder(None, None, None, Some(false), None)))
    }

    "bind valid taxid params" in {
      val params = Map("taxRegime" -> Seq("nino"), "taxId" -> Seq("AB112233A"))
      bindable.bind("", params) mustBe Some(
        Right(
          GetEntityParamsBinder(Some("nino"), Some("AB112233A"), None, None)
        )
      )
    }

    "bind valid resolve params" in {
      val params = Map("resolve" -> Seq("true"))
      bindable.bind("", params) mustBe Some(
        Right(GetEntityParamsBinder(None, None, None, Some(true)))
      )
    }

    "bind valid taxid and resolve params" in {
      val params = Map("taxRegime" -> Seq("nino"), "taxId" -> Seq("AB112233A"), "resolve" -> Seq("true"))
      bindable.bind("", params) mustBe Some(
        Right(
          GetEntityParamsBinder(Some("nino"), Some("AB112233A"), None, Some(true))
        )
      )
    }

    "bind valid taxid and resolve false params" in {
      val params = Map("taxRegime" -> Seq("nino"), "taxId" -> Seq("AB112233A"), "resolve" -> Seq("false"))
      bindable.bind("", params) mustBe Some(
        Right(
          GetEntityParamsBinder(Some("nino"), Some("AB112233A"), None, Some(false))
        )
      )
    }

    "bind valid taxid and all resolve params" in {
      val params = Map(
        "taxRegime"  -> Seq("nino"),
        "taxId"      -> Seq("AB112233A"),
        "resolve"    -> Seq("true"),
        "resolveIds" -> Seq("true")
      )
      bindable.bind("", params) mustBe Some(
        Right(
          GetEntityParamsBinder(Some("nino"), Some("AB112233A"), None, Some(true), Some(true))
        )
      )
    }
  }
}
