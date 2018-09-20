package com.alexander.arendar.concscalaprog

import scala.io.StdIn


object Chapter2 {

  def parallel[A, B](a: => A, b: => B):(A, B) = {
    var aResult = Option.empty[A]
    var bResult = Option.empty[B]
    val t1 = thread{aResult = Some(a)}
    val t2 = thread{bResult = Some(b)}
    t1.join()
    t2.join()
    (aResult.get, bResult.get)
  }

  def periodically(duration:Long)(b: => Unit):Unit =
    thread{
      while(true){
        b
        Thread.sleep(duration)
      }
    }

  final class SyncVar[T]{
    private val lock = new AnyRef

    @volatile
    private var value:Option[T] = None

    def getWait():T = lock.synchronized{
      var completed = false
      while(! completed) {
        if (isEmpty) lock.wait()
        if (nonEmpty) {
          completed = true
        }
      }
      val result = this.value.get
      this.value = None
      lock.notifyAll()
      result
    }

    def putWait(value:T):Unit = lock.synchronized{
      var completed = false
      while(! completed){
        if(nonEmpty) lock.wait()
        if(isEmpty){
          this.value = Some(value)
          log("put: " + value)
          completed = true
        }
      }
      lock.notify()
    }

    def isEmpty:Boolean = this.value.isEmpty

    def nonEmpty:Boolean = ! isEmpty
  }

  def main(args:Array[String]):Unit = {
    val syncVar1 = new SyncVar[Int]()

    val producer1 = thread {
      (0 until 100000) foreach{ i =>
        syncVar1.putWait(i)
      }
    }

    val producer2 = thread {
      (500000 until 600000) foreach{ i =>
        syncVar1.putWait(i)
      }
    }

    val consumer1 = thread {
      while(true){
        log(syncVar1.getWait())
      }
    }

    val consumer2 = thread {
      while(true){
        log(syncVar1.getWait())
      }
    }


  }

}
