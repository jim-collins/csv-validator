package uk.gov.tna.dri.validator

import uk.gov.tna.dri.schema.{Optional, CellContext, Schema}
import au.com.bytecode.opencsv.CSVReader
import java.io.Reader
import scala.collection.JavaConversions._
import scalaz._
import Scalaz._
import uk.gov.tna.dri.metadata.{Cell, Row}

trait MetaDataValidator {

  type MetaDataValidation[A] = ValidationNEL[String, A]

  def validate(csv: Reader, schema: Schema) = {
    val rows = new CSVReader(csv).readAll()
    val v = for ((row, rowIndex) <- rows.zipWithIndex) yield validateRow(Row(row.toList.map(Cell(_)), rowIndex + 1), schema)
    v.sequence[MetaDataValidation, Any]
  }

  private def validateRow(row: Row, schema: Schema) = {
    val totalColumnsV = totalColumns(row, schema)

    val rulesV = rules(row, schema)
    (totalColumnsV |@| rulesV) { _ :: _ }
  }

  private def totalColumns(row: Row, schema: Schema) = {
    if (row.cells.length == schema.totalColumns) true.successNel[String]
    else s"Expected @TotalColumns of ${schema.totalColumns} and found ${row.cells.length} on line ${row.lineNumber}".failNel[Any]
  }

  private def rules(row: Row, schema: Schema) = {
    val cells = row.cells.lift
    val v = for { (columnDefinition, columnIndex) <- schema.columnDefinitions.zipWithIndex } yield validateCell(cells(columnIndex), CellContext(columnIndex, row, schema))
    v.sequence[MetaDataValidation, Any]
  }

  private def validateCell(cell: Option[Cell], cellContext: CellContext) = cell match {
    case Some(c) => rulesForCell(cellContext)
    case _ => s"Missing value at line: ${cellContext.lineNumber}, column: ${cellContext.columnIdentifier}".failNel[Any]
  }

  private def rulesForCell(cellContext: CellContext) = {
    if (cellContext.cell.value.trim.isEmpty && cellContext.columnDirectives.contains(Optional())) true.successNel
    else cellContext.rules.map(_.execute(cellContext)).sequence[MetaDataValidation, Any]
  }
}
