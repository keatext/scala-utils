package com.keatext.mintosci.scalautils

import scala.collection.GenTraversableLike
import scala.collection.generic.CanBuildFrom


object Unzip4 {
  def unapply[A, B, C, D, Repr, AA, BB, CC, DD](
    tuples: GenTraversableLike[(A, B, C, D), Repr]
  )(implicit
    cbfA: CanBuildFrom[Repr, A, AA],
    cbfB: CanBuildFrom[Repr, B, BB],
    cbfC: CanBuildFrom[Repr, C, CC],
    cbfD: CanBuildFrom[Repr, D, DD]
  )
  : Some[(AA, BB, CC, DD)] =
    Some((
      tuples.map(_._1),
      tuples.map(_._2),
      tuples.map(_._3),
      tuples.map(_._4)
    ))
}
