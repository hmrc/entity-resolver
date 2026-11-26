/*
 * Copyright 2023 HM Revenue & Customs
 *
 */

package uk.gov.hmrc.entityresolver

import org.apache.pekko.actor.ActorSystem
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{ Application, Configuration }
import uk.gov.hmrc.auth.core.ConfidenceLevel.L50
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.entityresolver.AuthPredicateBuilder.{ AuthParamsNotDefined, NoConfidenceLevel, NoControllersConfigDefined, NoEnrolmentDefined }

class AuthPredicateBuilderSpec extends PlaySpec with ScalaFutures with MockitoSugar with GuiceOneAppPerTest {

  import play.api.Configuration

  implicit val system: ActorSystem = ActorSystem("test")

  "AuthPredicateBuilder" should {
    "confidenceLevel mustBe L50" in new TestCase {
      val authBuilder = new AuthPredicateBuilder("", None)
      authBuilder.confidenceLevel(config) must be(Some(L50))
    }

    "resolveControllerConfigs mustBe NoControllersConfigDefined when config missing" in new TestCase {
      val authBuilder = new AuthPredicateBuilder("", None)
      authBuilder.resolveControllerConfigs must be(Left(NoControllersConfigDefined()))
    }

    "resolveControllerConfigs mustBe Configuration when config exists" in new TestCase {
      val authBuilder = new AuthPredicateBuilder("", Some(config))
      authBuilder.resolveControllerConfigs must be(Right(config))
    }

    "confidence mustBe L50" in new TestCase {
      val authBuilder = new AuthPredicateBuilder("", None)
      authBuilder.confidence(config) must be(Right(L50))
    }

    "enrolment mustBe NoEnrolmentDefined(TestController)" in new TestCase {
      val authBuilder = new AuthPredicateBuilder("TestController", None)
      authBuilder.enrolment(config) must be(Left(NoEnrolmentDefined("TestController")))
    }

    "authPredicate mustBe NoEnrolmentDefined(TestController)" in new TestCase {
      val authBuilder = new AuthPredicateBuilder("TestController", None)
      authBuilder.authPredicate(config) must be(Left(NoEnrolmentDefined("TestController")))
    }

    "authParams mustBe NoEnrolmentDefined(TestController)" in new TestCase {
      val authBuilder = new AuthPredicateBuilder("TestController", None)
      authBuilder.authParams(config) must be(Left(AuthParamsNotDefined("TestController")))
    }

    "authConfig mustBe NoControllersConfigDefined" in new TestCase {
      val authBuilder = new AuthPredicateBuilder("TestController", None)
      authBuilder.authConfig must be(Left(NoControllersConfigDefined()))
    }
  }

  "AuthPredicateBuilder" should {
    "NoConfidenceLevel msg mustBe" in new TestCase {
      NoConfidenceLevel("TestController").msg must be(
        "No Confidence level defined for controller: TestController or globally, which needs authentication"
      )
    }

    "NoEnrolmentDefined msg mustBe" in new TestCase {
      NoEnrolmentDefined("TestController").msg must be(
        "No enrolment defined for controller: TestController, which needs authentication"
      )
    }

    "AuthParamsNotDefined msg mustBe" in new TestCase {
      AuthParamsNotDefined("TestController").msg must be(
        "No authParams defined for controller: TestController, which needs authentication"
      )
    }
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder().configure(Map("confidenceLevel" -> 50)).build()

  class TestCase {
    val config: Configuration = app.injector.instanceOf[Configuration]
  }
}

class AuthPredicateBuilderWithCorrectConfigSpec
    extends PlaySpec with ScalaFutures with MockitoSugar with GuiceOneAppPerTest {
  "AuthPredicateBuilder with correct config" should {
    "enrolment mustBe Enrolment(TestEnrolment)" in new TestCase {
      val authBuilder = new AuthPredicateBuilder("TestController", Some(config))
      authBuilder.enrolment(config) must be(Right(Enrolment("TestEnrolment")))
    }

    "authPredicate mustBe Predicate" in new TestCase {
      val authBuilder = new AuthPredicateBuilder("TestController", Some(config))
      authBuilder.authPredicate(config) must be(Right(Enrolment("TestEnrolment") and L50))
    }

    "authParams mustBe a configuration" in new TestCase {
      val authBuilder = new AuthPredicateBuilder("TestController", None)
      (authBuilder.authParams(config) match {
        case Right(_) => true
        case _        => false
      }) must be(true)
    }

    "authConfig mustBe a Predicate" in new TestCase {
      val authBuilder = new AuthPredicateBuilder("TestController", Some(config))
      authBuilder.authConfig must be(Right(Enrolment("TestEnrolment") and L50))
    }
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        Map(
          "confidenceLevel" -> 50,
          "enrolment"       -> "TestEnrolment",
          "TestController" -> Map[String, Any](
            "authParams" -> Map[String, Any]("enrolment" -> "TestEnrolment", "confidenceLevel" -> 50)
          )
        )
      )
      .build()

  class TestCase {
    val config: Configuration = app.injector.instanceOf[Configuration]
  }
}
