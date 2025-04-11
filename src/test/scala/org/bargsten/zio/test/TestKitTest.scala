package org.bargsten.zio.test

// :snx nodiff-test
import difflicious.implicits.*
import difflicious.Differ
import zio.*
import zio.test.*
import zio.test.Assertion.*
import org.bargsten.zio.test.TestKit.*

object TestKitTest extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment & Scope, Any] = suite("diff")(
    test("simple diff") {
      case class Pokemon(name: String, level: Int)
      given Differ[Pokemon] = Differ.derived
      val a = Pokemon("Pikachu", 1)
      val b = Pokemon("Pikchu", 2)
      assert(a)(Differ[Pokemon].noDiff(b))
    }
  )
}
// :xns
