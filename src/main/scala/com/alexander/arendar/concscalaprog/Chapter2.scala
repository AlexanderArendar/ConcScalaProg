package com.alexander.arendar.concscalaprog

import java.util.concurrent.LinkedBlockingQueue

import scala.collection.mutable

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

    @volatile
    private var value:Option[T] = None

    def getWait():T = this.synchronized{
      var completed = false
      while(! completed) {
        if (isEmpty) this.wait()
        if (nonEmpty) {
          completed = true
        }
      }
      val result = value.get
      value = None
      this.notifyAll()
      result
    }

    def putWait(t:T):Unit = this.synchronized{
      var completed = false
      while(! completed){
        if(nonEmpty) this.wait()
        if(isEmpty){
          value = Some(t)
          log("put: " + value)
          completed = true
        }
      }
      this.notify()
    }

    private def isEmpty:Boolean = value.isEmpty

    private def nonEmpty:Boolean = ! isEmpty
  }

  final class SyncQueue[T](val capacity:Int){
    @volatile
    private var queue:java.util.concurrent.LinkedBlockingQueue[T] = new LinkedBlockingQueue[T](capacity)

    def getWait():T = queue.take()

    def putWait(t:T):Unit = queue.put(t)
  }

  final class SyncQueue2[T](val capacity:Int){
    @volatile
    private var queue:mutable.Queue[T] = new mutable.Queue[T]()

    def getWait():T = this.synchronized{
      while(queue.isEmpty){
        this.wait()
      }
      this.notifyAll()
      queue.dequeue()
    }

    def putWait(t:T):Unit = this.synchronized{
      while(queue.size >= capacity){
        this.wait()
      }
      queue.enqueue(t)
      log("size: " + queue.size)
      this.notifyAll()
    }
  }

  def main(args:Array[String]):Unit = {
    val queue = new SyncQueue2[Any](10)

    val producer1 = thread{
      (1 to 500) foreach (i => queue.putWait(System.currentTimeMillis().toString))
    }

    val producer2 = thread{
      (1 to 500) foreach (i => queue.putWait(i))
    }

    val consumer1 = thread{
      while(true){
        log(queue.getWait())
      }
    }

    val consumer2 = thread{
      while(true){
        log(queue.getWait())
      }
    }

    producer1.join()
    producer2.join()
    consumer1.join()
    consumer2.join()

  }

}
