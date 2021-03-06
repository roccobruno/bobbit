package controller

import java.net.URLEncoder

import model._
import org.joda.time.DateTime
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import play.api.Configuration
import play.api.http.HeaderNames
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeRequest, WithApplication}
import repository.{BobbytRepository, ClusterConfiguration}
import util.TokenUtil

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


@RunWith(classOf[JUnitRunner])
class AccountControllerSpec extends Specification {

  case class TestJob(job: Job, location: String)

  lazy val appEnableSec = GuiceApplicationBuilder().loadConfig(Configuration("security-enabled" -> true)).build()

  trait Setup extends WithApplication with TokenUtil {

    val account = Account(userName = "neo13", email = Some(EmailAddress("test@test.it")), psw = Some("passw"))

    def createAccount = {
      val response = route(implicitApp, FakeRequest(POST, "/api/bobbyt/account").withBody(Json.parse("""{"userName":"neo13","email":{"value":"test@test.it"},"psw":"passw","active":false, "docType":"Account"}""")))
      status(response.get) must equalTo(CREATED)
      response
    }

    val bobbytRepos = new BobbytRepository(new ClusterConfiguration(app.configuration))

    def cleanUpDBAndCreateToken = {
      Await.result(for {
        del <- bobbytRepos.deleteAllToken()
        del <- bobbytRepos.deleteAllAccount()
        del <- bobbytRepos.deleteAllAlerts()
        del <- bobbytRepos.deleteAllJobs()
      } yield del, 10 seconds)

    }


    def login = {
      val resp = route(implicitApp, FakeRequest(POST, "/api/bobbyt/login-token").withHeaders((HeaderNames.AUTHORIZATION,
        s"Bearer $token")).withJsonBody(Json.parse("{}"))).get
      status(resp) must equalTo(CREATED)
    }

  }


  "account controller" should {

    "return 201 and create account record" in new Setup() {
      cleanUpDBAndCreateToken

      val response = createAccount
      val getResource = headers(response.get).get("Location").get
      getResource must be startWith ("/api/bobbyt/account")

      val ttoken = headers(response.get).get(AUTHORIZATION).get

      val getRec = route(implicitApp, FakeRequest(GET, getResource).withHeaders((HeaderNames.AUTHORIZATION,
        ttoken))).get
      status(getRec) must equalTo(OK)
      val json: Account = contentAsJson(getRec).as[Account]

      json.userName must equalTo(account.userName)
      json.firstName must equalTo(account.firstName)
      json.lastName must equalTo(account.lastName)
      json.email must equalTo(account.email)

    }

    "login via username/password , account not active" in new Setup() {
      cleanUpDBAndCreateToken

      createAccount

      val loginData = Login(username = "neo13", password = "passw")
      val responseLogin = route(implicitApp, FakeRequest(POST, "/api/bobbyt/login").withBody(Json.toJson(loginData)))
      status(responseLogin.get) must equalTo(UNAUTHORIZED)


    }


    "login via username/password , account active" in new Setup() {
      cleanUpDBAndCreateToken

      val response = createAccount

      val getResource = headers(response.get).get("Location").get
      val ttoken = headers(response.get).get(AUTHORIZATION).get

      //validate account
      val responseValidate = route(implicitApp, FakeRequest(POST, s"/api/bobbyt/account/validate?token=${URLEncoder.encode(ttoken, "UTF-8")}")
        .withBody(Json.parse("{}")))
      status(responseValidate.get) must equalTo(SEE_OTHER)

      //login
      val loginData = Login(username = "neo13", password = "passw")
      val responseLogin = route(implicitApp, FakeRequest(POST, "/api/bobbyt/login").withBody(Json.toJson(loginData)))
      status(responseLogin.get) must equalTo(OK)


    }

    "return 403 when validatoing account with expired token" in new Setup {

      cleanUpDBAndCreateToken
      override val expiry = DateTime.now().minusHours(1).getMillis / 1000
      val response = createAccount

      val responseValidate = route(implicitApp, FakeRequest(POST, s"/api/bobbyt/account/validate?token=${URLEncoder.encode(s"Bearer $token", "UTF-8")}").withBody(Json.parse("{}")))
      status(responseValidate.get) must equalTo(UNAUTHORIZED)

    }

    "create and validate account" in new Setup {
      cleanUpDBAndCreateToken

      val response = createAccount

      val getResource = headers(response.get).get("Location").get
      val ttoken = headers(response.get).get(AUTHORIZATION).get

      //validate account
      val responseValidate = route(implicitApp, FakeRequest(POST, s"/api/bobbyt/account/validate?token=${URLEncoder.encode(ttoken, "UTF-8")}").withBody(Json.parse("{}")))
      status(responseValidate.get) must equalTo(SEE_OTHER)

    }

    "returning 400 when wrong email format is provice in account" in new Setup {
        val response = route(implicitApp, FakeRequest(POST, "/api/bobbyt/account").withBody(Json.parse("""{"userName":"neo13","email":{"value":"testtest.it"},"psw":"passw","active":false, "docType":"Account"}""")))
        status(response.get) must equalTo(BAD_REQUEST)
    }



    "create and load account " in new Setup() {

      cleanUpDBAndCreateToken

      val response = createAccount
      val getResource = headers(response.get).get("Location").get
      getResource must be startWith ("/api/bobbyt/account")

      val ttoken = headers(response.get).get(AUTHORIZATION).get


      //lookup account by token
      val getRecByToken = route(implicitApp, FakeRequest(GET, "/api/bobbyt/account/load").withHeaders((HeaderNames.AUTHORIZATION,
        ttoken))).get
      status(getRecByToken) must equalTo(OK)


    }

    "login via token  and submit profile" in new Setup {

      cleanUpDBAndCreateToken
      val resp = route(implicitApp, FakeRequest(POST, "/api/bobbyt/login-token").withHeaders((HeaderNames.AUTHORIZATION,
        s"Bearer $token")).withJsonBody(Json.parse("{}"))).get
      status(resp) must equalTo(CREATED)

      val tokens = Await.result(bobbytRepos.findAllToken(), 10 seconds)

      tokens.size must equalTo(1)
      tokens(0).token must equalTo(token)


      val responseProfile = route(implicitApp, FakeRequest(POST, "/api/bobbyt/profile").withHeaders((HeaderNames.AUTHORIZATION,
        s"Bearer $token")).withJsonBody(Json.toJson(account))).get
      status(responseProfile) must equalTo(CREATED)

      val getResource = headers(responseProfile).get("Location").get
      getResource must be startWith ("/api/bobbyt/account")

      val getRec = route(implicitApp, FakeRequest(GET, getResource).withHeaders((HeaderNames.AUTHORIZATION,
        s"Bearer $token"))).get
      status(getRec) must equalTo(OK)
      val json: Account = contentAsJson(getRec).as[Account]

      json.userName must equalTo(account.userName)
      json.firstName must equalTo(account.firstName)
      json.lastName must equalTo(account.lastName)
      json.email must equalTo(account.email)

    }


    "logout a valid account" in new Setup {

      cleanUpDBAndCreateToken
      val resp = route(implicitApp, FakeRequest(POST, "/api/bobbyt/login-token").withHeaders((HeaderNames.AUTHORIZATION,
        s"Bearer $token")).withJsonBody(Json.parse("{}"))).get
      status(resp) must equalTo(CREATED)

      val response = route(implicitApp, FakeRequest(POST, "/api/bobbyt/logout").withBody("").withHeaders((HeaderNames.AUTHORIZATION,
        s"Bearer $token"))).get
      status(response) must equalTo(OK)

      val tokens = Await.result(bobbytRepos.findAllToken(), 10 seconds)
      tokens.size must equalTo(0)

      val responseProfile = route(implicitApp, FakeRequest(POST, "/api/bobbyt/profile").withHeaders((HeaderNames.AUTHORIZATION,
        s"Bearer $token")).withJsonBody(Json.toJson(account))).get
      status(responseProfile) must equalTo(UNAUTHORIZED)

    }


    "return 200 when username already exists" in new Setup {

      cleanUpDBAndCreateToken

      val createAccountResponse = createAccount

      val ttoken = headers(createAccountResponse.get).get(AUTHORIZATION).get

      val response = route(implicitApp, FakeRequest(GET, s"/api/bobbyt/account/username/neo13").withHeaders((HeaderNames.AUTHORIZATION,
        ttoken))).get
      status(response) must equalTo(OK)
    }

    "return 400 when username doesn not exist" in new Setup {

      cleanUpDBAndCreateToken

      val createAccountResponse = createAccount

      val ttoken = headers(createAccountResponse.get).get(AUTHORIZATION).get

      val response = route(implicitApp, FakeRequest(GET, s"/api/bobbyt/account/username/test").withHeaders((HeaderNames.AUTHORIZATION,
        ttoken))).get
      status(response) must equalTo(NOT_FOUND)
    }


  }

}
