package com.alexander.arendar

package object concscalaprog {
  def log[T](msg:T):Unit = {
    println(s"${Thread.currentThread().getName} : $msg")
  }
}
