/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver.binders

import org.scalatestplus.play.PlaySpec

class SimpleObjectBinderSpec extends PlaySpec {
  val simpleStringBinder = new SimpleObjectBinder[Int](_.toInt, _.toString)
  "SimpleObjectBinder" should {
    "bind a string" in {
      simpleStringBinder.bind("key", "2") must be(Right(2))
    }

    "bind return Left if value now a Int" in {
      simpleStringBinder.bind("key", "test") must be(Left("Cannot parse parameter 'key' with value 'test' as 'int'"))
    }

    "unbind a Int" in {
      simpleStringBinder.unbind("key", 2) must be("2")
    }
  }
}
