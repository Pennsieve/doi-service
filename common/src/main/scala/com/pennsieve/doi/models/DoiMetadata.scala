// Copyright (c) 2026 University of Pennsylvania. All Rights Reserved.

package com.pennsieve.doi.models

import java.text.NumberFormat
import java.time.{ OffsetDateTime, ZoneOffset }
import java.util.Locale

/**
  * What DataCite shows about a published dataset version beyond its title,
  * creators and licence: subjects (the dataset's tags), sizes, and the dates
  * it was issued, made available (after an embargo) and last revised.
  *
  * Every field is optional: one left out keeps what the DOI already has, so
  * callers that don't send them change nothing.
  */
case class DoiMetadata(
  keywords: Option[List[String]] = None,
  size: Option[Long] = None,
  fileCount: Option[Int] = None,
  publishedAt: Option[OffsetDateTime] = None,
  availableAt: Option[OffsetDateTime] = None,
  revisedAt: Option[OffsetDateTime] = None
) {

  /** The keywords as free-text subjects, trimmed, without duplicates. */
  def subjects: Option[List[Subject]] =
    keywords.map(_.map(_.trim).filter(_.nonEmpty).distinct.map(Subject(_)))

  /**
    * A revised DOI's subjects: the keywords replace its free-text subjects,
    * and its vocabulary terms (with a subjectScheme) stay. Without keywords
    * it keeps them all.
    */
  def revisedSubjects(existing: Option[List[Subject]]): Option[List[Subject]] =
    subjects match {
      case None => existing
      case Some(keywords) =>
        Some(existing.getOrElse(List.empty).filter(_.subjectScheme.isDefined) ++ keywords)
    }

  /** For example "3.2 TB" and "1,355 files". */
  def sizes: Option[List[String]] =
    if (size.isEmpty && fileCount.isEmpty) None
    else
      Some(
        size.map(DoiMetadata.humanBytes).toList ++ fileCount
          .map(DoiMetadata.files)
          .toList
      )

  /**
    * existing, with its Issued, Available and Updated dates replaced by the
    * ones given.
    */
  def dates(existing: List[DoiDate]): List[DoiDate] = {
    val given =
      publishedAt.map(t => DoiDate(DoiMetadata.day(t), "Issued")).toList ++
        availableAt.map(t => DoiDate(DoiMetadata.day(t), "Available")).toList ++
        revisedAt.map(t => DoiDate(DoiMetadata.day(t), "Updated")).toList
    existing.filterNot(d => given.exists(_.dateType == d.dateType)) ++ given
  }
}

object DoiMetadata {

  /** The UTC day, YYYY-MM-DD. */
  def day(t: OffsetDateTime): String =
    t.atZoneSameInstant(ZoneOffset.UTC).toLocalDate.toString

  /** Decimal units, one decimal place: 3216756367026 is "3.2 TB". */
  def humanBytes(bytes: Long): String = {
    val units = Vector("bytes", "kB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble
    var unit = 0
    while (value >= 1000 && unit < units.length - 1) {
      value /= 1000
      unit += 1
    }
    if (unit == 0) s"$bytes bytes"
    else String.format(Locale.US, "%.1f %s", Double.box(value), units(unit))
  }

  /** For example "1,355 files". */
  def files(count: Int): String = {
    val n = NumberFormat.getIntegerInstance(Locale.US).format(count.toLong)
    if (count == 1) s"$n file" else s"$n files"
  }
}
