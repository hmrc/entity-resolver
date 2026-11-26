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
