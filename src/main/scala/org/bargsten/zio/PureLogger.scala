package org.bargsten.zio

import org.bargsten.zio.PureLogger.{DefaultMaxEntries, LogResults, transform}
import zio.*

import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}
import scala.annotation.tailrec
import scala.collection.immutable.Queue
// we actually need import zio.stacktracer.TracingImplicits.disableAutoTrace
// but this shows up as unused import, so .* it is
import zio.stacktracer.TracingImplicits.*
import zio.stream.ZStream

/**
 * Your domain/business logic is pure. ZIO should not be part of it, but still you need to use it, because you need to log. But
 * introducing ZIO into your domain logic also enables service injection and other patterns that complicate the otherwise "pure"
 * logic.
 *
 * Additionally, your syntax and return types get considerably more verbose.
 *
 * [[PureLogger]] provides a lightweight logging alternative that interfaces with ZIO, once you "leave" your domain logic:
 *
 * {{{
 * def domainLogic(using logger: PureLogger): String = {
 *   logger.info("i1")
 *   "result"
 * }
 *
 * ZIO.scoped {
 *   for {
 *     given PureLogger <- PureLogger.defaultZIO
 *     result = domainLogic
 *     _ <- insertIntoDb(result)
 *   } yield result
 * }
 * }}}
 *
 * PureLogger works with ZIO Scope, so once you leave the scope, logs will get transferred to ZIO and flushed/cleared.
 */
trait PureLogger {
  def warn(msg: String): Unit
  def info(msg: String): Unit
  def error(msg: String): Unit
  def debug(msg: String): Unit
  def flushZIO(using Trace): UIO[Unit]
  def clear(): Unit
  def flush(): LogResults
}

class PureLoggerNoop extends PureLogger {
  def warn(msg: String): Unit = ()
  def info(msg: String): Unit = ()
  def error(msg: String): Unit = ()
  def debug(msg: String): Unit = ()
  def flushZIO(using Trace): UIO[Unit] = ZIO.unit
  def clear(): Unit = ()
  def flush(): LogResults = LogResults(Nil, Nil, Nil, Nil)
}

class PureLoggerDefault(maxEntries: Int = DefaultMaxEntries) extends PureLogger {
  private val count = AtomicInteger(0)
  private val errorMsgs = AtomicReference[Queue[String]](Queue.empty)
  private val warnMsgs = AtomicReference[Queue[String]](Queue.empty)
  private val infoMsgs = AtomicReference[Queue[String]](Queue.empty)
  private val debugMsgs = AtomicReference[Queue[String]](Queue.empty)

  private def overflowMsg = s"PureLogger overflowed (max is $maxEntries), some messages were lost"
  private def append(msg: String)(q: Queue[String]) = {
    val nWritten = count.incrementAndGet()
    if (nWritten < maxEntries) {
      q.enqueue(msg)
    } else if (nWritten == maxEntries) {
      errorMsgs.transform(_.enqueue(overflowMsg))
      q
    } else {
      q
    }
  }

  def clear(): Unit = {
    errorMsgs.set(Queue.empty)
    warnMsgs.set(Queue.empty)
    infoMsgs.set(Queue.empty)
    debugMsgs.set(Queue.empty)
  }

  def warn(msg: String): Unit = warnMsgs.transform(append(msg)): Unit

  def info(msg: String): Unit = infoMsgs.transform(append(msg)): Unit

  def error(msg: String): Unit = errorMsgs.transform(append(msg)): Unit

  def debug(msg: String): Unit = debugMsgs.transform(append(msg)): Unit

  def flush(): LogResults = {
    val results = LogResults(
      errorMsgs.get.toList,
      warnMsgs.get.toList,
      infoMsgs.get.toList,
      debugMsgs.get.toList
    )
    clear()
    results
  }
  def flushZIO(using Trace): UIO[Unit] = for {
    _ <- ZIO.foreachDiscard(errorMsgs.get.toList)(msg => ZIO.logError(msg))
    _ <- ZIO.foreachDiscard(warnMsgs.get.toList)(msg => ZIO.logWarning(msg))
    _ <- ZIO.foreachDiscard(infoMsgs.get.toList)(msg => ZIO.logInfo(msg))
    _ <- ZIO.foreachDiscard(debugMsgs.get.toList)(msg => ZIO.logDebug(msg))
    _ <- ZIO.succeed(clear())
  } yield ()
}

object PureLogger {

  /**
   * The idea is to have a relatively small scope, so the default limit is not that big. Also, intensive logging in the domain
   * logic is a questionable practice. Write tests instead or use [[Either]] for errors.
   */
  def DefaultMaxEntries: Int = 40000

  def apply(): PureLogger = new PureLoggerDefault
  lazy val noop: PureLoggerNoop = new PureLoggerNoop
  def default: PureLogger = new PureLoggerDefault
  def defaultZIO(using Trace): ZIO[Scope, Nothing, PureLogger] =
    ZIO.acquireRelease(ZIO.succeed(new PureLoggerDefault))(_.flushZIO)
  def defaultZStream(using Trace): ZStream[Scope, Nothing, PureLogger] = ZStream.fromZIO(defaultZIO)
  def emptyZIO(using Trace): ZIO[Scope, Nothing, PureLogger] = ZIO.succeed(new PureLoggerNoop)
  def emptyZStream(using Trace): ZStream[Scope, Nothing, PureLogger] = ZStream.fromZIO(emptyZIO)

  def flushZIO(using pureLogger: PureLogger, t: Trace): UIO[Unit] = pureLogger.flushZIO

  case class LogResults(error: List[String], warn: List[String], info: List[String], debug: List[String])

  extension [T <: AnyRef](ar: AtomicReference[T]) {
    @tailrec
    def transform(cb: T => T): Unit = {
      val oldValue = ar.get
      val newValue = cb(oldValue)

      if (oldValue.eq(newValue)) {
        ()
      } else if (!ar.compareAndSet(oldValue, newValue)) {
        transform(cb)
      } else {
        ()
      }
    }
  }
}
