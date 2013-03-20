package uk.gov.tna.dri.validator

import uk.gov.tna.dri.schema._
import scalaz._
import Scalaz._
import uk.gov.tna.dri.metadata.Cell
import uk.gov.tna.dri.metadata.Row
import uk.gov.tna.dri.schema.TotalColumns
import scala.Some
import uk.gov.tna.dri.schema.Optional

trait AllErrorsMetaDataValidator extends MetaDataValidator {

  val pathSubstitutions: List[(String,String)]

  def validateRows(rows: List[Row], schema: Schema): FailMetaDataValidation[Any] = {
    val v = for (row <- rows) yield validateRow(row, schema)
    v.sequence[FailMetaDataValidation, Any]
  }

  private def validateRow(row: Row, schema: Schema): FailMetaDataValidation[Any] = {
    val totalColumnsV = totalColumns(row, schema)
    val rulesV = rules(row, schema)
    (totalColumnsV |@| rulesV) { _ :: _ }
  }

  private def totalColumns(row: Row, schema: Schema): FailMetaDataValidation[Any] = {
    val tc: Option[TotalColumns] = schema.globalDirectives.collectFirst {
      case t@TotalColumns(_) => t
    }

    if (tc.isEmpty || tc.get.numberOfColumns == row.cells.length) true.successNel[FailMessage]
    else ErrorMessage(s"Expected @totalColumns of ${tc.get.numberOfColumns} and found ${row.cells.length} on line ${row.lineNumber}").failNel[Any]
  }

  private def rules(row: Row, schema: Schema): FailMetaDataValidation[List[Any]] = {
    val cells: (Int) => Option[Cell] = row.cells.lift
    val v = for {(columnDefinition, columnIndex) <- schema.columnDefinitions.zipWithIndex} yield validateCell(columnIndex, cells, row, schema)
    v.sequence[FailMetaDataValidation, Any]
  }

  private def validateCell(columnIndex: Int, cells: (Int) => Option[Cell], row: Row, schema: Schema): FailMetaDataValidation[Any] = {
    cells(columnIndex) match {
      case Some(c) => rulesForCell(columnIndex, row, schema)
      case _ => ErrorMessage(s"Missing value at line: ${row.lineNumber}, column: ${schema.columnDefinitions(columnIndex).id}").failNel[Any]
    }
  }


  private def rulesForCell(columnIndex: Int, row: Row, schema: Schema): FailMetaDataValidation[Any] = {
    val columnDefinition = schema.columnDefinitions(columnIndex)

    def convert2Warnings( results:Rule#RuleValidation[Any]): FailMetaDataValidation[Any] = {
      val a = results.fail
      val b = a.map{b => b.map(c => WarningMessage(c))}.validation
      b
    }

    def convert2Errors( results:Rule#RuleValidation[Any]): FailMetaDataValidation[Any] = {
      val a = results.fail
      val b = a.map{b => b.map(c => ErrorMessage(c))}.validation
      b
    }

    if (row.cells(columnIndex).value.trim.isEmpty && columnDefinition.directives.contains(Optional())) true.successNel
    else columnDefinition.rules.map(_.evaluate(columnIndex, row, schema)).    map{ a:Rule#RuleValidation[Any] => {
      if(columnDefinition.directives.contains(Warning())) convert2Warnings(a)
      else convert2Errors(a)
    }}.sequence[FailMetaDataValidation, Any]
  }
}