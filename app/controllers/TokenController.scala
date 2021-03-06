package controllers

import helpers.{Auth0Config, EncodedJwtToken, JwtToken}
import io.igl.jwt._
import model.Account
import org.joda.time.DateTime
import play.api.Logger
import play.api.http.HeaderNames
import play.api.mvc._
import repository.BobbytRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


trait TokenChecker {
  val repository: BobbytRepository
  val auth0Config: Auth0Config

  def WithAuthorization(body : (JwtToken) => Future[Result])(implicit request: Request[_]) = {
    val token = request.headers.get(HeaderNames.AUTHORIZATION)
    token.fold(Future.successful[Result](Results.Unauthorized)){
      value =>
        auth0Config.decodeAndVerifyToken(value.split(" ")(1)) match {
          case Right(tk) => {
            repository.findValidTokenByValue(tk.token) flatMap  {
              case Some(savedToken) => body(tk)
              case None => Logger.warn("Valid token but user has not logged it or record has expired");Future.successful(Results.Unauthorized)
            }
          }
          case Left(message) => Logger.warn(s"Token validation failed . Msg - $message");Future.successful(Results.Unauthorized)
        }
    }
  }

  def WithValidToken(body : (JwtToken) => Future[Result])(implicit request: Request[_]) : Future[Result]= {
    val token = request.headers.get(HeaderNames.AUTHORIZATION)
    WithValidToken(token)(body)(request)
  }

  def WithValidToken(jwtToken: Option[String])(body : (JwtToken) => Future[Result])(implicit request: Request[_]) : Future[Result] = {
    jwtToken.fold(Future.successful[Result](Results.Unauthorized)){
      value =>
        auth0Config.decodeAndVerifyToken(value.split(" ")(1)) match {
          case Right(token) =>  body(token)
          case Left(message) => Logger.warn(s"Token validation failed . Msg - $message");Future.successful(Results.Unauthorized)
        }
    }
  }

  def generateToken(account: Account) : Future[EncodedJwtToken] = {

    val expiry: Long = DateTime.now().plusHours(1).getMillis
    val created = DateTime.now().getMillis
    val jwt = new DecodedJwt(Seq(Alg(Algorithm.HS256), Typ("JWT")),
      Seq(Iss(account.getId), Sub(account.userName), Aud(""), Exp(expiry), Iat(created)))
    Future.successful(EncodedJwtToken(jwt.encodedAndSigned(auth0Config._secret)))

  }

}
