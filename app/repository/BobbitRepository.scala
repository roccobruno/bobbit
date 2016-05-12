package repository

import java.util.UUID

import com.couchbase.client.protocol.views._
import model.{Job}
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
object BobbitRepository extends BobbitRepository {


  val driver = ReactiveCouchbaseDriver()
  override lazy val bobbitBucket = driver.bucket("bobbit")


  def createBobbitDesignerDocument() = bobbitBucket.designDocument("bobbit") map {

    case res: DesignDocument => println("designer document present")
    case _ => {
      val desDoc = new DesignDocument("bobbit")
      desDoc.getViews.add(new ViewDesign("by_Id",viewByIdMapFunction))
      desDoc.getViews.add(new ViewDesign("by_type_jobId",viewMapFunctionByTypeAndJobId))
      desDoc.getViews.add(new ViewDesign("by_type",viewByDoctypeMapFunction))
      desDoc.getViews.add(new ViewDesign("by_type_time_and_recurring_alert_sent",viewByStartTimeMapFunction))
      desDoc.getViews.add(new ViewDesign("by_type_end_time_and_recurring_alert_sent",viewByEndTimeMapFunction))
      bobbitBucket.createDesignDoc(desDoc) map {
        {
          case o: OpResult if o.isSuccess => println("designer doc CREATED for RunningJob table")
          case o: OpResult => Right(o.getMessage)
        }
      }

    }
  } 



  val viewMapFunctionByTypeAndJobId =
    """
      |function (doc, meta) {
      |  emit([doc.docType,doc.jobId], null);
      |}
    """.stripMargin

  val viewByStartTimeMapFunction =
    """
      |function (doc, meta) {
      |  emit([doc.docType,doc.recurring,doc.alertSent,doc.from.time], null);
      |}
    """.stripMargin

  val viewByEndTimeMapFunction =
    """
      |function (doc, meta) {
      |  emit([doc.docType,doc.recurring,doc.alertSent,doc.to.time], null);
      |}
    """.stripMargin

  val viewByIdMapFunction =
    """
      |function (doc, meta) {
      |  emit(doc.id, null);
      |}
    """.stripMargin

  val viewByDoctypeMapFunction =
    """
      |function (doc, meta) {
      |  emit(doc.docType, null);
      |}
    """.stripMargin


  createBobbitDesignerDocument()
}


trait BobbitRepository {


  def driver:ReactiveCouchbaseDriver
  def bobbitBucket : CouchbaseBucket


  def saveAccount(account: Account): Future[Either[String,Any]] = {
    val id = account.getId
    bobbitBucket.set[Account](id, account) map {
      case o: OpResult if o.isSuccess => Left(id)
      case o: OpResult => Right(o.getMessage)
    }
  }

  def deleteAllRunningJob() = {
    deleteAll(findAllRunningJob,deleteRunningJoById)
  }

  def deleteAllJobs() = {
    deleteAll(findAllJob,deleteJobById)
  }

  def deleteAllAccount() = {
    deleteAll(findAllAccount,deleteJobById)
  }

  def deleteAllAlerts() = {
    deleteAll(findAllAlert,deleteAlertById)
  }

  def deleteAll[T <: InternalId](findAll : () => Future[Seq[T]], delete: (String) => Future[Either[String,Any]]) = {
    findAll() map {
      recs =>
        recs map (rec => delete(rec.getId))
    } recover {
      case _ => println("Error in deleting rows. Probably no rows were found")
    }
  }

  def saveAlert(alert: EmailAlert): Future[Either[String,Any]] = {
    val id = alert.getId
    bobbitBucket.set[EmailAlert](id,alert) map {
      case o: OpResult if o.isSuccess => Left(id)
      case o: OpResult => Right(o.getMessage)
    }
  }

  def saveJob(job: Job): Future[Either[String,Any]] = {
    val id = job.getId
    bobbitBucket.set[Job](id, job) map {
      case o: OpResult if o.isSuccess => Left(id)
      case o: OpResult => Right(o.getMessage)
    }
  }

  def saveRunningJob(job: RunningJob): Future[Either[String,Any]] = {
    val id = job.getId
    bobbitBucket.set[RunningJob](id, job) map {
      case o: OpResult if o.isSuccess => Left(id)
      case o: OpResult => Right(o.getMessage)
    }
  }

  def findRunningJobById(id: String): Future[Option[RunningJob]] = {
    findById[RunningJob](id)
  }

  def findJobById(id: String): Future[Option[Job]] = {
    findById[Job](id)
  }
  
  def findAccountById(id: String): Future[Option[Account]] = {
    findById[Account](id)
  }
  
  def findById[T](id: String)(implicit rds: Reads[T]): Future[Option[T]] = {
    bobbitBucket.get[T](id)
  }
  
  def deleteJobById(id:String) : Future[Either[String,Any]] = {
    bobbitBucket.delete(id) map {
      case o: OpResult if o.isSuccess => Left(id)
      case o: OpResult => Right(o.getMessage)
    }
  }

  def deleteAlertById(id:String) : Future[Either[String,Any]] = {
    bobbitBucket.delete(id) map {
      case o: OpResult if o.isSuccess => Left(id)
      case o: OpResult => Right(o.getMessage)
    }
  }

  def deleteRunningJoById(id:String) : Future[Either[String,Any]] = {
    bobbitBucket.delete(id) map {
      case o: OpResult if o.isSuccess => Left(id)
      case o: OpResult => Right(o.getMessage)
    }
  }

  def findAllByType[T <:InternalId](docType: String)(implicit rds: Reads[T]): Future[List[T]] = {
    bobbitBucket.find[T]("bobbit", "by_type")(new Query().setIncludeDocs(true)
      .setRangeStart(ComplexKey.of(docType)).
      setRangeEnd(ComplexKey.of(s"$docType\uefff")).
      setStale(Stale.FALSE))
  }

  def findAllRunningJob(): Future[List[RunningJob]] = {
    findAllByType[RunningJob]("RunningJob")
  }

  def findAllAlert(): Future[List[EmailAlert]] = {
    findAllByType[EmailAlert]("Alert")
  }

  def findAllAccount(): Future[List[Account]] = {
    findAllByType[Account]("Account")
  }

  def findAllJob(): Future[List[Job]] = {
    findAllByType[Job]("Job")
  }


  def findRunningJobByJobId(jobId: String): Future[Option[RunningJob]] = {
    val query = new Query().setIncludeDocs(true).setLimit(1)
      .setRangeStart(ComplexKey.of("RunningJob",jobId)).setRangeEnd(ComplexKey.of("RunningJob",s"$jobId\uefff")).setStale(Stale.FALSE)
    bobbitBucket.find[RunningJob]("bobbit", "by_type_jobId")(query).map(_.headOption)
  }


  def findRunningJobToExecute() : Future[Set[RunningJob]] = {
    for {
      first <- findRunningJobToExecuteByStartTime()
      second <- findRunningJobToExecuteByEndTime()
    } yield (first ++ second).toSet
  }

  def findRunningJobToExecuteByStartTime() : Future[Seq[RunningJob]] = {
    val query = new Query().setIncludeDocs(true)
      .setRangeStart(ComplexKey.of("RunningJob",java.lang.Boolean.TRUE,java.lang.Boolean.FALSE,timeOfDay(DateTime.now))).
      setRangeEnd(ComplexKey.of("RunningJob",
        java.lang.Boolean.TRUE,java.lang.Boolean.FALSE,timeOfDay(DateTime.now.plusMinutes(30)))).setStale(Stale.FALSE)
    bobbitBucket.find[RunningJob]("bobbit", "by_type_time_and_recurring_alert_sent")(query)
  }

  def findRunningJobToExecuteByEndTime() : Future[Seq[RunningJob]] = {
    val query = new Query().setIncludeDocs(true)
      .setRangeStart(ComplexKey.of("RunningJob",java.lang.Boolean.TRUE,java.lang.Boolean.FALSE,timeOfDay(DateTime.now))).
      setRangeEnd(ComplexKey.of("RunningJob",
        java.lang.Boolean.TRUE,java.lang.Boolean.FALSE,timeOfDay(DateTime.now.plusMinutes(30)))).setStale(Stale.FALSE)
    bobbitBucket.find[RunningJob]("bobbit", "by_type_end_time_and_recurring_alert_sent")(query)
  }




  def findRunningJobToReset() : Future[Seq[RunningJob]] = {
    val query = new Query().setIncludeDocs(true)
      .setRangeStart(ComplexKey.of("RunningJob",java.lang.Boolean.TRUE,java.lang.Boolean.TRUE,new Integer("0"))).
      setRangeEnd(ComplexKey.of("RunningJob",java.lang.Boolean.TRUE,java.lang.Boolean.TRUE,timeOfDay(DateTime.now.minusHours(1)))).setStale(Stale.FALSE)
    bobbitBucket.find[RunningJob]("bobbit", "by_type_time_and_recurring_alert_sent")(query)
  }

  private def timeOfDay(tm: DateTime): Integer = TimeOfDay.time(tm.hourOfDay().get(),tm.minuteOfHour().get())

}
