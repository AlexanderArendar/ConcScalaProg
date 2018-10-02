package com.alexander.arendar.concscalaprog

import java.util.concurrent.LinkedBlockingQueue

import scala.annotation.tailrec
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

  case class Task(task:()=>Unit, priority:Int)

  class PriorityTaskPool(concurrencyLevel:Int)(importantPriority:Int){outer=>

    implicit val taskOrdering: math.Ordering[Task] = (x: Task, y: Task) => math.Ordering.Int.compare(x.priority, y.priority)

    private var terminated:Boolean = false

    private val queue:mutable.PriorityQueue[Task] = new mutable.PriorityQueue[Task]()

    private class Worker extends Thread{

      @tailrec
      private def dequeueRecursively():Option[Task] = {
        val item = if(!queue.isEmpty) Some(queue.dequeue()) else None
        item match{
          case Some(task) => if(task.priority >= importantPriority) item else dequeueRecursively()
          case None => None
        }
      }

      def poll():Option[Task] = outer.synchronized{
        while(queue.isEmpty && !terminated) outer.wait()
        if(!terminated) Some(queue.dequeue()) else dequeueRecursively()
      }

      @tailrec
      override final def run(): Unit = {
        poll() match{
          case Some(item) => item.task(); run()
          case None =>
        }
      }
    }

    private val workers:Iterable[Worker] = for(_ <- 1 to concurrencyLevel) yield new Worker()

    workers.foreach(_.start())

    def asynchronous(priority:Int)(task: =>Unit):Unit = {
      this.synchronized{
        if(!terminated){
          queue.enqueue(Task(() => task, priority))
          log(s"enqueued: $priority")
          this.notifyAll()
        }
      }
    }

    def shutDown():Unit = this.synchronized{
      terminated = true
      this.notifyAll()
    }
  }


  def main(args:Array[String]):Unit = {
    val taskPool = new PriorityTaskPool(3)(5)
    val job1 = thread{
      (50 to 150) foreach(i => taskPool.asynchronous(i)(log(i)))
    }

    val job2 = thread{
      (1 to 100) foreach(i => taskPool.asynchronous(i)(log(i)))
    }

    job1.join()
    taskPool.shutDown()
    job2.join()
  }

}
