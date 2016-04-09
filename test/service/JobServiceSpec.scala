package service

import java.util.UUID

import model._
import org.joda.time.DateTime
import org.mockito.Matchers.any
import org.mockito.{Matchers, Mockito}
import org.mockito.Mockito._
import org.scalatest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ OptionValues, WordSpecLike}
import play.api.libs.ws.WSClient
import repository.{JackRepository, TubeRepository}
import service.tfl.JobService
import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise, Future}


class JobServiceSpec extends  WordSpecLike with OptionValues with ScalaFutures{

  trait Setup {


     val repoMock = mock(classOf[JackRepository])
     val tubeMock = mock(classOf[TubeRepository])

     val service = new JobService {
      override val repo: JackRepository = repoMock
      override val ws: WSClient = mock(classOf[WSClient])
      override val tubeRepository: TubeRepository = tubeMock

      override def apiId: String = ""

      override def apiKey: String = ""
    }

  }

  "a service" should {


    "create an alert" in  new Setup {

      val job = Job(alert = Email("from@mss.it", "from@mss.it"), journey = Journey(true,
        MeansOfTransportation(Seq(TubeLine("central", "central")), Nil), TimeOfDay(8, 30), 40))

      val lineStatus = LineStatus(10, "", None, Nil, Some(Disruption("minor-delay", "", "", None, None)))
      val tubeService = TFLTubeService("central", "central", Seq(lineStatus))

      val runningJob = RunningJob(from = TimeOfDay(8, 30), to = TimeOfDay(9, 10), jobId = job.getId)

      when(repoMock.findRunningJobToExecute()).thenReturn(Future.successful(Seq(runningJob)))
      when(tubeMock.findById("central")).thenReturn(Future.successful(Some(tubeService)))
      when(repoMock.findById(job.getId)).thenReturn(Future.successful(Some(job)))
      when(repoMock.saveAlert(any[EmailAlert])).thenReturn(Future.successful(Left("id")))

      Await.result(service.findAndProcessActiveJobs(),100 millisecond)

      verify(repoMock, times(1)).saveAlert(EmailAlert(job.alert, any[Some[DateTime]], None, job.getId))

    }


    "create no alert in case of no disruption" in new Setup {

      val job = Job(alert = Email("from@mss.it", "from@mss.it"), journey = Journey(true,
        MeansOfTransportation(Seq(TubeLine("central", "central")), Nil), TimeOfDay(8, 30), 40))

      val lineStatus = LineStatus(10, "", None, Nil, None)
      val tubeService = TFLTubeService("central", "central", Seq(lineStatus))

      val runningJob = RunningJob(from = TimeOfDay(8, 30), to = TimeOfDay(9, 10), jobId = job.getId)

      when(repoMock.findRunningJobToExecute()).thenReturn(Future.successful(Seq(runningJob)))
      when(tubeMock.findById("central")).thenReturn(Future.successful(Some(tubeService)))
      when(repoMock.findById(job.getId)).thenReturn(Future.successful(Some(job)))
      when(repoMock.saveAlert(any[EmailAlert])).thenReturn(Future.successful(Left("id")))

      Await.result(service.findAndProcessActiveJobs(),100 millisecond)

      verify(repoMock, times(0)).saveAlert(EmailAlert(job.alert, any[Some[DateTime]], None, job.getId))

    }


    "create one alert when multiple disruption for same journey " in  new Setup {

      val job = Job(alert = Email("from@mss.it", "from@mss.it"), journey = Journey(true,
        MeansOfTransportation(Seq(TubeLine("central", "central")), Nil), TimeOfDay(8, 30), 40))

      val lineStatus = LineStatus(10, "", None, Nil, Some(Disruption("minor-delay", "", "", None, None)))
      val tubeService = TFLTubeService("central", "central", Seq(lineStatus))
      val tubeService2 = TFLTubeService("northern", "northern", Seq(lineStatus))

      val runningJob = RunningJob(from = TimeOfDay(8, 30), to = TimeOfDay(9, 10), jobId = job.getId)

      when(repoMock.findRunningJobToExecute()).thenReturn(Future.successful(Seq(runningJob)))
      when(tubeMock.findById("central")).thenReturn(Future.successful(Some(tubeService)))
      when(tubeMock.findById("northern")).thenReturn(Future.successful(Some(tubeService2)))
      when(repoMock.findById(job.getId)).thenReturn(Future.successful(Some(job)))
      when(repoMock.saveAlert(any[EmailAlert])).thenReturn(Future.successful(Left("id")))

      Await.result(service.findAndProcessActiveJobs(),100 millisecond)

      verify(repoMock, times(1)).saveAlert(EmailAlert(job.alert, any[Some[DateTime]], None, job.getId))

    }


    "create two alert when multiple disruption for two journeys " in  new Setup {

      val job = Job(alert = Email("from@mss.it", "from@mss.it"), journey = Journey(true,
        MeansOfTransportation(Seq(TubeLine("central", "central")), Nil), TimeOfDay(8, 30), 40))

      val lineStatus = LineStatus(10, "", None, Nil, Some(Disruption("minor-delay", "", "", None, None)))
      val tubeService = TFLTubeService("central", "central", Seq(lineStatus))
      val tubeService2 = TFLTubeService("northern", "northern", Seq(lineStatus))

      val runningJob = RunningJob(from = TimeOfDay(8, 30), to = TimeOfDay(9, 10), jobId = job.getId)
      val runningJob2 = RunningJob(from = TimeOfDay(8, 30), to = TimeOfDay(9, 10), jobId = job.getId)

      when(repoMock.findRunningJobToExecute()).thenReturn(Future.successful(Seq(runningJob,runningJob2)))
      when(tubeMock.findById("central")).thenReturn(Future.successful(Some(tubeService)))
      when(tubeMock.findById("northern")).thenReturn(Future.successful(Some(tubeService2)))
      when(repoMock.findById(job.getId)).thenReturn(Future.successful(Some(job)))
      when(repoMock.saveAlert(any[EmailAlert])).thenReturn(Future.successful(Left("id")))

      Await.result(service.findAndProcessActiveJobs(),100 millisecond)

      verify(repoMock, times(2)).saveAlert(EmailAlert(job.alert, any[Some[DateTime]], None, job.getId))

    }
  }

}