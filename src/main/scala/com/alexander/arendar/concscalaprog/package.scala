package com.alexander.arendar

package object concscalaprog {

  def log[T](msg:T):Unit = {
    println(s"${Thread.currentThread().getName()} : $msg")
  }

  def thread(block: =>Unit):Thread = {
    val t = new Thread{
      override def run(): Unit = block
    }
    t.start()
    t
  }
}
