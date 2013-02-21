package uk.gov.tna.dri.schema

import org.specs2.mutable.Specification
import uk.gov.tna.dri.metadata.{Cell, Row}
import scalaz.{Failure, Success}
import uk.gov.tna.dri.metadata.Cell
import uk.gov.tna.dri.metadata.Row

class UniqueRuleSpec extends Specification {

  "unique rule" should {

    "pass if all column values are distinct" in {
      val schema = Schema(List(TotalColumns(1)), List(ColumnDefinition("Name")))
      val rule = UniqueRule()

      rule.evaluate(0, Row(Cell("Jim") :: Nil, 1), schema)
      rule.evaluate(0, Row(Cell("Ben") :: Nil, 2), schema) must beLike { case Success(_) => ok }
    }

    "fail if there are duplicate column values" in {
      val schema = Schema(List(TotalColumns(1)), List(ColumnDefinition("Name")))
      val rule = UniqueRule()

      rule.evaluate(0, Row(Cell("Jim") :: Nil, 1), schema)
      rule.evaluate(0, Row(Cell("Ben") :: Nil, 2), schema)

      rule.evaluate(0, Row(Cell("Jim") :: Nil, 3), schema) must beLike {
        case Failure(msgs) => msgs.list mustEqual(List("unique fails for line: 3, column: Name, value: Jim"))
      }
    }

    "fail if columns differ only in case with @ignoreCase" in {
      val rule = UniqueRule()
      val schema = Schema(List(TotalColumns(1)), List(ColumnDefinition("Name", rule :: Nil, IgnoreCase() :: Nil)))

      rule.evaluate(0, Row(Cell("Ben") :: Nil, 1), schema)

      rule.evaluate(0, Row(Cell("BEN") :: Nil, 2), schema) must beLike {
        case Failure(msgs) => msgs.list mustEqual(List("unique fails for line: 2, column: Name, value: BEN"))
      }
    }
  }
}