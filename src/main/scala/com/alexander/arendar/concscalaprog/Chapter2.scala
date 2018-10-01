package com.alexander.arendar.concscalaprog

import java.util.concurrent.LinkedBlockingQueue

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

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

  object UniqueIdService{
    @volatile
    var counter:Int = 0

    def getUniqueId():Int = this.synchronized{
      counter += 1
      counter
    }
  }

  private val transfers:ArrayBuffer[String] = ArrayBuffer()

  def logTransfer(name:String, n:Int):Unit =
    transfers.synchronized{
      transfers += s"transfer to account '$name' = $n"
    }

  class Account(val name:String, var money:Int){
    val id = UniqueIdService.getUniqueId()

    override def toString: String = s"Account($name, $money)"
  }

  def add(account:Account, n:Int):Unit =
    account.synchronized{
      account.money += n
      if(n > 10) logTransfer(account.name, n)
    }

  def send(sender:Account, recepient:Account, n:Int):Unit = {
    def adjust():Unit = {
      sender.money -= n
      recepient.money += n
    }

    if(sender.id < recepient.id)
      sender.synchronized{
        while(sender.money == 0) sender.wait()
        recepient.synchronized{
          adjust()
          recepient.notifyAll()
        }
      }
    else
      recepient.synchronized{
        sender.synchronized{
          while(sender.money == 0) sender.wait()
          adjust()
        }
        recepient.notifyAll()
      }
  }

  def sendAll(accounts:Set[Account], target:Account):Unit = {
    accounts foreach (account => send(account, target, account.money))
  }

  def main(args:Array[String]):Unit = {
    val accounts = ArrayBuffer[Account]()
    for(i <- 1 to 100) accounts += new Account(s"account$i", 1000)
    val recepient = new Account("Alex", 0)
    val (half1, half2) = accounts.splitAt(50)
    val job1 = thread{sendAll(half1.toSet, recepient)}
    val job2 = thread{sendAll(half2.toSet, recepient)}
    val job3 = thread{
      half1 foreach {a => send(recepient, a, 10); Thread.sleep(5)}
    }
    val job4 = thread{
      half1 foreach {a => send(recepient, a, 10); Thread.sleep(5)}
    }
    job1.join()
    job2.join()
    job3.join()
    job4.join()
    accounts foreach println
    println(recepient)
  }

}
