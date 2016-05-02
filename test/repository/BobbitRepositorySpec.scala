package repository

import java.util.UUID

import model.{TimeOfDay, RunningJob}
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.test.WithApplication
import util.Testing

import scala.concurrent.Await
import scala.concurrent.duration._

class BobbitRepositorySpec extends Testing {

  def repo: BobbitRepository = BobbitRepository

  override protected def afterAll(): Unit = {
    println("shutting down the driver")
    repo.driver.shutdown()
  }


  "a repository" should {

    "return running job to execute" in new WithApplication {

      await(repo.deleteAllRunningJob())
      await(repo.deleteAllJobs())

      val now = DateTime.now.plusMinutes(2)
      val hourOfTheDay = now.hourOfDay().get()
      val minOfTheDay = now.minuteOfHour().get()
      val startJob = TimeOfDay(hourOfTheDay, minOfTheDay, Some(TimeOfDay.time(hourOfTheDay, minOfTheDay)))

      val job = RunningJob(from = startJob, to = startJob.plusMinutes(40), alertSent = false,
        recurring = true, jobId = UUID.randomUUID().toString)
      await(repo.saveRunningJob(job))
      await(repo.saveRunningJob(RunningJob(from = startJob, to = startJob.plusMinutes(40), alertSent = false,
        recurring = true, jobId = UUID.randomUUID().toString)))

      val job1 = RunningJob(from = startJob, to = startJob.plusMinutes(50), alertSent = true,
        recurring = true, jobId = UUID.randomUUID().toString)
      await(repo.saveRunningJob(job1))


      val res = await(repo.findRunningJobToExecute())
      res.size should be(2)

      res contains job should be(true)

    }

    "return running jobs to reset" in new WithApplication {

      await(repo.deleteAllRunningJob(), 10 second)

      val startJob = startTimeOfDay(now.minusHours(2))
      val job = RunningJob(from = startJob, to = startJob.plusMinutes(40), alertSent = true,
        recurring = true, jobId = UUID.randomUUID().toString)
      await(repo.saveRunningJob(job))

      val job1 = RunningJob(from = startJob, to = startJob.plusMinutes(50), alertSent = false,
        recurring = true, jobId = UUID.randomUUID().toString)
      await(repo.saveRunningJob(job1))

      val startTimeOfDay1 = startTimeOfDay(now.plusMinutes(2))
      val job2 = RunningJob(from = startTimeOfDay1, to = startTimeOfDay1.plusMinutes(50), alertSent = true,
        recurring = true, jobId = UUID.randomUUID().toString)
      await(repo.saveRunningJob(job2))


      val res = await(repo.findRunningJobToReset())
      res.size should be(1)
      res contains job should be(true)

    }


  }

  private def startTimeOfDay(nowTime: DateTime) = {
    val hourOfTheDay = nowTime.hourOfDay().get()
    val minOfTheDay = nowTime.minuteOfHour().get()
    TimeOfDay(hourOfTheDay, minOfTheDay, Some(TimeOfDay.time(hourOfTheDay, minOfTheDay)))
  }


}
