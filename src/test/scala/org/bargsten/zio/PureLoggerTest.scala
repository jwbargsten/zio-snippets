package org.bargsten.zio

import org.bargsten.zio.PureLogger.{DefaultMaxEntries, LogResults}
import zio.*
import zio.test.*
import zio.test.Assertion.*

object PureLoggerTest extends ZIOSpecDefault {

  def spec = suite("PureLogger")(
    test("should log the message with ZIO") {

      def domainLogic(using logger: PureLogger): String = {
        logger.info("i1")
        logger.info("i2")
        logger.warn("w1")
        logger.error("e1")
        logger.debug("d1")
        "result"
      }

      // for demo purposes in the docs
      def insertIntoDb(v: String): UIO[Unit] = ZIO.unit

      val prg =
        ZIO.scoped {
          for {
            given PureLogger <- PureLogger.defaultZIO
            result = domainLogic
            _ <- insertIntoDb(result)
          } yield result
        }

      for {
        _ <- prg
        loggerOutput <- ZTestLogger.logOutput
      } yield assert(loggerOutput.map(e => (e.logLevel, e.message())))(
        equalTo(
          Chunk(
            (LogLevel.Error, "e1"),
            (LogLevel.Warning, "w1"),
            (LogLevel.Info, "i1"),
            (LogLevel.Info, "i2"),
            (LogLevel.Debug, "d1")
          )
        )
      )
    },
    test("should log the message") {
      given pureLogger: PureLogger = PureLogger()

      def domainLogic(using logger: PureLogger): Unit = {
        logger.info("i1")
        logger.info("i2")
        logger.warn("w1")
        logger.error("e1")
        logger.debug("d1")
      }

      domainLogic

      assert(pureLogger.flush())(equalTo(LogResults(List("e1"), List("w1"), List("i1", "i2"), List("d1"))))
    },
    test("should cap at default max num entries") {
      given pureLogger: PureLogger = PureLogger()

      def domainLogic(using logger: PureLogger): Unit = {
        logger.info("i1")
        logger.warn("w1")
        logger.error("e1")
        logger.debug("d1")
      }

      (1 to DefaultMaxEntries * 2).foreach(_ => domainLogic)
      val results = pureLogger.flush()
      assert(results.debug.size + results.error.size + results.warn.size + results.info.size)(
        equalTo(PureLogger.DefaultMaxEntries)
      ) &&
      assert(results.error.last)(containsString("overflowed")) &&
      assert(results.info.toSet)(equalTo(Set("i1"))) &&
      assert(results.warn.toSet)(equalTo(Set("w1"))) &&
      assert(results.debug.toSet)(equalTo(Set("d1")))

    },
    test("should cap at max num entries") {
      given pureLogger: PureLogger = PureLoggerDefault(10)

      def domainLogic(using logger: PureLogger): Unit = {
        logger.info("i1")
        logger.warn("w1")
        logger.error("e1")
        logger.debug("d1")
      }

      (1 to 100).foreach(_ => domainLogic)
      val results = pureLogger.flush()
      assert(results.debug.size + results.error.size + results.warn.size + results.info.size)(equalTo(10)) &&
      assert(results.error.last)(containsString("overflowed")) &&
      assert(results.info.toSet)(equalTo(Set("i1"))) &&
      assert(results.warn.toSet)(equalTo(Set("w1"))) &&
      assert(results.debug.toSet)(equalTo(Set("d1")))

    }
  ).provideLayer(
    Runtime.removeDefaultLoggers >>> ZTestLogger.default
  ) @@ TestAspect.withLiveClock

}
