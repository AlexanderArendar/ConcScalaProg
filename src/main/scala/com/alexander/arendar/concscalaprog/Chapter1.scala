package com.alexander.arendar.concscalaprog

import scala.util.Try

object Chapter1 {

  def compose[A, B, C](g:B => C, f:A => B): A => C = x => g(f(x))

  def fuse[A, B](a:Option[A], b:Option[B]):Option[(A, B)] =
    for{
      aValue <- a
      bValue <- b
    } yield (aValue, bValue)

  def check[T](xs:Seq[T])(pred: T => Boolean):Boolean = Try(xs.forall(pred)).getOrElse(false)

  class Pair[P, Q](val first:P, val second:Q)

  object Pair{
    def unapply[P, Q](p:Pair[P, Q]):Option[(P, Q)] = Some((p.first, p.second))
  }

  def lexPermutations(phrase:String):Seq[String] = {
    val chars = phrase.toSeq
    
    val combs = for{
      i <- 1 to chars.size
      head = phrase(i-1)
      remainder = {
        val halves = chars.splitAt(i)
        halves._1.dropRight(1) ++ halves._2
      }
    } yield (head, remainder)

    val result = for {
      (head, remainder) <- combs
    } yield {
      if(remainder.size == 0) Seq(String.valueOf(head)) else {
        lexPermutations(String.valueOf(remainder.toArray)).map(s => String.valueOf(head) + s)
      }
    }
    result.flatten.sorted
  }

  def main(args:Array[String]):Unit = {
    lexPermutations("alexander") foreach {s => print(s); print(" | ")}
  }
}
