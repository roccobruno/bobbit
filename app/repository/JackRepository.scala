package repository

import java.util.UUID

import com.couchbase.client.protocol.views.{DesignDocument, ComplexKey, Stale, Query}
import model.{Job, Jack}
import org.joda.time.DateTime
import org.reactivecouchbase.{ReactiveCouchbaseDriver, CouchbaseBucket}
import org.reactivecouchbase.play.PlayCouchbase
import play.api.libs.json._
import org.reactivecouchbase.client.OpResult
import play.api.Play.current
import scala.concurrent.ExecutionContext.Implicits.global
import org.reactivecouchbase.play.plugins.CouchbaseN1QLPlugin._

import scala.concurrent.Future
import scala.util.Failure

import model._
object JackRepository extends JackRepository {


  val driver = ReactiveCouchbaseDriver()
  override lazy val bucket = driver.bucket("default")
  override lazy val runningJobBucket = driver.bucket("runningJob")
  override lazy val alertsBucket = driver.bucket("alert")

}


trait JackRepository {


  def bucket: CouchbaseBucket
  def runningJobBucket : CouchbaseBucket
  def alertsBucket : CouchbaseBucket

  def saveAlert(alert: EmailAlert): Future[Either[String,Any]] = {
    val id = UUID.randomUUID().toString
    alertsBucket.set[EmailAlert](id,alert) map {
      case o: OpResult if o.isSuccess => Left(id)
      case o: OpResult => Right(o.getMessage)
    }
  }

  def saveAJackJob(job: Job): Future[Either[String,Any]] = {
    val id = job.getId
    bucket.set[Job](id, job) map {
      case o: OpResult if o.isSuccess => Left(id)
      case o: OpResult => Right(o.getMessage)
    }
  }

  def saveARunningJackJob(job: RunningJob): Future[Either[String,Any]] = {
    val id = job.getId
    runningJobBucket.set[RunningJob](id, job) map {
      case o: OpResult if o.isSuccess => Left(id)
      case o: OpResult => Right(o.getMessage)
    }
  }

  def findRunningJobById(id: String): Future[Option[RunningJob]] = {
    runningJobBucket.get[RunningJob](id)
  }

  def findById(id: String): Future[Option[Job]] = {
    bucket.get[Job](id)
  }

  def deleteById(id:String) : Future[Either[String,Any]] = {
    bucket.delete(id) map {
      case o: OpResult if o.isSuccess => Left(id)
      case o: OpResult => Right(o.getMessage)
    }
  }

  def deleteRunningJoById(id:String) : Future[Either[String,Any]] = {
    runningJobBucket.delete(id) map {
      case o: OpResult if o.isSuccess => Left(id)
      case o: OpResult => Right(o.getMessage)
    }
  }

  def findAll(): Future[List[Job]] = {
    bucket.find[Job]("jack", "by_name")(new Query().setIncludeDocs(true).setStale(Stale.FALSE))
  }

  def findByName(name: String): Future[Option[Jack]] = {
    val query = new Query().setIncludeDocs(true).setLimit(1)
      .setRangeStart(ComplexKey.of(name)).setRangeEnd(ComplexKey.of(s"$name\uefff")).setStale(Stale.FALSE)
    bucket.find[Jack]("jack", "by_name")(query).map(_.headOption)
  }


  def findRunningJobByJobId(jobId: String): Future[Option[RunningJob]] = {
    val query = new Query().setIncludeDocs(true).setLimit(1)
      .setRangeStart(ComplexKey.of(jobId)).setRangeEnd(ComplexKey.of(s"$jobId\uefff")).setStale(Stale.FALSE)
    runningJobBucket.find[RunningJob]("runningJob", "by_jobId")(query).map(_.headOption)
  }


  def findRunningJobToExecute() : Future[Seq[RunningJob]] = {

    val query = new Query().setIncludeDocs(true).setLimit(1)
      .setRangeStart(ComplexKey.of(timeOfDay(DateTime.now),java.lang.Boolean.TRUE,java.lang.Boolean.TRUE)).
      setRangeEnd(ComplexKey.of(timeOfDay(DateTime.now.plusMinutes(10)),java.lang.Boolean.TRUE,java.lang.Boolean.FALSE)).setStale(Stale.FALSE)
    runningJobBucket.find[RunningJob]("runningJob", "by_time_and_recurring_alert_sent")(query)
  }

  private def timeOfDay(tm: DateTime): Integer = {
    val nowHour = tm.hourOfDay().get()
    val nowTime = tm.minuteOfHour().get()
    TimeOfDay.time(nowHour,nowTime)
  }
}
