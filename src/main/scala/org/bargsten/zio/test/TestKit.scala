package org.bargsten.zio.test

// :snx nodiff-extension
import difflicious.{DiffResultPrinter, Differ}
import zio.test.*

trait TestKit {
  extension [A](differ: Differ[A]) {
    def noDiff(expected: A): Assertion[A] =
      Assertion[A](
        TestArrow
          .make[A, Boolean] { actual =>
            val result = differ.diff(actual, expected)
            TestTrace.boolean(
              result.isOk
            ) {
              ErrorMessage.custom(DiffResultPrinter.consoleOutput(result, 0).render)
            }
          }
          .withCode("noDiff")
      )
  }
}

object TestKit extends TestKit
// :xns
