package com.keatext.mintosci.scalautils

object Unzip4 {
  def unapply[A, B, C, D](tuples: List[(A, B, C, D)])
  : Some[(List[A], List[B], List[C], List[D])] =
    Some((
      tuples.map(_._1),
      tuples.map(_._2),
      tuples.map(_._3),
      tuples.map(_._4)
    ))
}
