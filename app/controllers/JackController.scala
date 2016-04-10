package controllers

import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

import akka.actor.ActorSystem
import jobs._
import model.{Job, Jack}
import org.reactivecouchbase.client.OpResult
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc.{Request, Result, Action, Controller}
import repository.{TubeRepository, JackRepository}
import service.tfl.{JobService, TubeConnector, TubeService}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import model._


class JackController  @Inject() (system: ActorSystem, wsClient:WSClient) extends Controller with JsonParser {
  val repository: JackRepository = JackRepository
  def getTubRepository = TubeRepository

  object JobServiceImpl extends JobService {
    val repo = repository
    val ws = wsClient
    val tubeRepository = getTubRepository
  }

  object TubeServiceRegistry extends TubeService with TubeConnector {
    val ws = wsClient
    val tubeRepository = getTubRepository
  }
  lazy  val tubeServiceActor = system.actorOf(TubeServiceFetchActor.props(TubeServiceRegistry), "tubeServiceActor")
  lazy  val runningActor = system.actorOf(RunningJobActor.props(JobServiceImpl), "runningJobActor")
  lazy  val resetRunningJobActor = system.actorOf(ResetRunningJobActor.props(JobServiceImpl), "resetRunningJobActor")


  lazy val tubeScheduleJob = system.scheduler.schedule(
    0.microseconds, 10000.milliseconds, tubeServiceActor,  Run("tick"))

  lazy val runningJobScheduleJob = system.scheduler.schedule(
    0.microseconds, 10000.milliseconds, runningActor,  Run("tick"))

  lazy val resetRunningJobScheduleJob = system.scheduler.schedule(
    0.microseconds, 10000.milliseconds, resetRunningJobActor,  Run("tick"))

  def fetchTubeLine() = Action.async { implicit request =>

      tubeScheduleJob
      runningJobScheduleJob
      resetRunningJobScheduleJob
      Future.successful(Ok(Json.obj("res"->true)))

  }

  def find(id: String) = Action.async { implicit request =>
    repository.findById(id) map {
      case b: Some[Job] => Ok(Json.toJson[Job](b.get))
      case _ => NotFound
    }
  }

  def delete(id: String) = Action.async {
    implicit request =>
      repository.deleteById(id) map {
        case Left(id) => Ok
        case _ => InternalServerError
      }
  }

  def deleteRunningJob(id: String) = Action.async {
    implicit request =>
      repository.deleteRunningJoById(id) map {
        case Left(id) => Ok
        case _ => InternalServerError
      }
  }

  def findRunningJobByJobId(jobId: String) = Action.async { implicit request =>
    repository.findRunningJobByJobId(jobId) map {
      case b: Some[RunningJob] => Ok(Json.toJson[RunningJob](b.get))
      case _ => NotFound
    }
  }


  def findActiveRunningJob() = Action.async { implicit request =>
    JobServiceImpl.findAndProcessActiveJobs() map {
      case recs => Ok(Json.toJson[Seq[RunningJob]](recs))
    }

  }


  def save() = Action.async(parse.json) { implicit request =>
    withJsonBody[Job](jackJob =>

      for {
        Left(id) <- repository.saveAJackJob(jackJob)
        runningId <- repository.saveRunningJackJob(RunningJob.fromJob(jackJob))
      }  yield Created.withHeaders("Location" -> ("/api/jack/" + id))

    )
  }


}


