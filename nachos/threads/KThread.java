package nachos.threads;

import nachos.machine.*;

/**
 * A KThread is a thread that can be used to execute Nachos kernel code. Nachos
 * allows multiple threads to run concurrently.
 *
 * To create a new thread of execution, first declare a class that implements
 * the <tt>Runnable</tt> interface. That class then implements the <tt>run</tt>
 * method. An instance of the class can then be allocated, passed as an
 * argument when creating <tt>KThread</tt>, and forked. For example, a thread
 * that computes pi could be written as follows:
 *
 * <p><blockquote><pre>
 * class PiRun implements Runnable {
 *     public void run() {
 *         // compute pi
 *         ...
 *     }
 * }
 * </pre></blockquote>
 * <p>The following code would then create a thread and start it running:
 *
 * <p><blockquote><pre>
 * PiRun p = new PiRun();
 * new KThread(p).fork();
 * </pre></blockquote>
 */
public class KThread {
    /**
     * Get the current thread.
     *
     * @return	the current thread.
     */
    public static KThread currentThread() {
	Lib.assertTrue(currentThread != null);
	return currentThread;
    }
    
    /**
     * Allocate a new <tt>KThread</tt>. If this is the first <tt>KThread</tt>,
     * create an idle thread as well.
     */
    public KThread() {
	if (currentThread != null) {
	    tcb = new TCB();
	}	    
	else {//是否允许优先级传递
	    readyQueue = ThreadedKernel.scheduler.newThreadQueue(true);
	    readyQueue.acquire(this);	    

	    currentThread = this;
	    tcb = TCB.currentTCB();
	    name = "main";
	    restoreState();

	    createIdleThread();
	}
    }

    /**
     * Allocate a new KThread.
     *
     * @param	target	the object whose <tt>run</tt> method is called.
     */
    public KThread(Runnable target) {
	this();
	this.target = target;
    }

    /**
     * Set the target of this thread.
     *
     * @param	target	the object whose <tt>run</tt> method is called.
     * @return	this thread.
     */
    public KThread setTarget(Runnable target) {
	Lib.assertTrue(status == statusNew);
	
	this.target = target;
	return this;
    }

    /**
     * Set the name of this thread. This name is used for debugging purposes
     * only.
     *
     * @param	name	the name to give to this thread.
     * @return	this thread.
     */
    public KThread setName(String name) {
	this.name = name;
	return this;
    }

    /**
     * Get the name of this thread. This name is used for debugging purposes
     * only.
     *
     * @return	the name given to this thread.
     */     
    public String getName() {
	return name;
    }

    /**
     * Get the full name of this thread. This includes its name along with its
     * numerical ID. This name is used for debugging purposes only.
     *
     * @return	the full name given to this thread.
     */
    public String toString() {
	return (name + " (#" + id + ")");
    }

    /**
     * Deterministically and consistently compare this thread to another
     * thread.
     */
    public int compareTo(Object o) {
	KThread thread = (KThread) o;

	if (id < thread.id)
	    return -1;
	else if (id > thread.id)
	    return 1;
	else
	    return 0;
    }

    /**
     * Causes this thread to begin execution. The result is that two threads
     * are running concurrently: the current thread (which returns from the
     * call to the <tt>fork</tt> method) and the other thread (which executes
     * its target's <tt>run</tt> method).
     */
    public void fork() {
	Lib.assertTrue(status == statusNew);
	Lib.assertTrue(target != null);
	
	Lib.debug(dbgThread,
		  "Forking thread: " + toString() + " Runnable: " + target);

	boolean intStatus = Machine.interrupt().disable();

	tcb.start(new Runnable() {
		public void run() {
		    runThread();
		}
	    });

	ready();
	
	Machine.interrupt().restore(intStatus);
    }

    private void runThread() {
	begin();
	target.run();
	finish();
    }

    private void begin() {
	Lib.debug(dbgThread, "Beginning thread: " + toString());
	
	Lib.assertTrue(this == currentThread);

	restoreState();

	Machine.interrupt().enable();
    }

    /**
     * Finish the current thread and schedule it to be destroyed when it is
     * safe to do so. This method is automatically called when a thread's
     * <tt>run</tt> method returns, but it may also be called directly.
     *
     * The current thread cannot be immediately destroyed because its stack and
     * other execution state are still in use. Instead, this thread will be
     * destroyed automatically by the next thread to run, when it is safe to
     * delete this thread.
     */
    public static void finish() {
	Lib.debug(dbgThread, "Finishing thread: " + currentThread.toString());
	
	Machine.interrupt().disable();

	Machine.autoGrader().finishingCurrentThread();

	Lib.assertTrue(toBeDestroyed == null);
	toBeDestroyed = currentThread;


	currentThread.status = statusFinished;
		KThread thread = currentThread().waitQueue.nextThread();
		if (thread != null) {
			thread.ready();
		}
		// end
	sleep();
    }

    /**
     * Relinquish the CPU if any other thread is ready to run. If so, put the
     * current thread on the ready queue, so that it will eventually be
     * rescheuled.
     *
     * <p>
     * Returns immediately if no other thread is ready to run. Otherwise
     * returns when the current thread is chosen to run again by
     * <tt>readyQueue.nextThread()</tt>.
     *
     * <p>
     * Interrupts are disabled, so that the current thread can atomically add
     * itself to the ready queue and switch to the next thread. On return,
     * restores interrupts to the previous state, in case <tt>yield()</tt> was
     * called with interrupts disabled.
     */
    public static void yield() {
	Lib.debug(dbgThread, "Yielding thread: " + currentThread.toString());
	
	Lib.assertTrue(currentThread.status == statusRunning);
	
	boolean intStatus = Machine.interrupt().disable();

	currentThread.ready();

	runNextThread();
	
	Machine.interrupt().restore(intStatus);
    }

    /**
     * Relinquish the CPU, because the current thread has either finished or it
     * is blocked. This thread must be the current thread.
     *
     * <p>
     * If the current thread is blocked (on a synchronization primitive, i.e.
     * a <tt>Semaphore</tt>, <tt>Lock</tt>, or <tt>Condition</tt>), eventually
     * some thread will wake this thread up, putting it back on the ready queue
     * so that it can be rescheduled. Otherwise, <tt>finish()</tt> should have
     * scheduled this thread to be destroyed by the next thread to run.
     */
    public static void sleep() {
	Lib.debug(dbgThread, "Sleeping thread: " + currentThread.toString());
	
	Lib.assertTrue(Machine.interrupt().disabled());

	if (currentThread.status != statusFinished)
	    currentThread.status = statusBlocked;

	runNextThread();
    }

    /**
     * Moves this thread to the ready state and adds this to the scheduler's
     * ready queue.
     */
    public void ready() {
	Lib.debug(dbgThread, "Ready thread: " + toString());
	
	Lib.assertTrue(Machine.interrupt().disabled());
	Lib.assertTrue(status != statusReady);
	
	status = statusReady;
	if (this != idleThread)
	    readyQueue.waitForAccess(this);
	
	Machine.autoGrader().readyThread(this);
    }

    /**
     * Waits for this thread to finish. If this thread is already finished,
     * return immediately. This method must only be called once; the second
     * call is not guaranteed to return. This thread must not be the current
     * thread.
     */
//    public void join() {
//		// 线程B中有A.join()语句，则B等待A执行完才能执行
//
//		Lib.debug(dbgThread, "Joining to thread: " + toString());
//
//		//判断是不是当前线程调用了join
//		Lib.assertTrue(this != currentThread);
//		// start
////		Lib.assertTrue(join_counter == 0);
//		join_counter++;
//		//关中断
//		boolean status = Machine.interrupt().disable();
//		//如果调用join()的对象的status不为完成状态
//		if(!hasAcquired) {
//			waitQueue.acquire(this);
//			hasAcquired = true;
//		}
//		if (this.status != statusFinished) {
//			//将KThread下的current对象放入waitQueue
//			waitQueue.waitForAccess(KThread.currentThread());
//			//将当前线程睡眠
//
//			sleep();
//		}
//
//		//如果是Finish状态则直接返回
//		//开中断
//
//		Machine.interrupt().restore(status);
//		// end
//
//	}
	public void join(){
		Lib.debug(dbgThread, "Joining to thread: " + toString());

		Lib.assertTrue(this != currentThread);//调用该方法的进程与正在运行的进程不一致

		boolean status = Machine.interrupt().disable();//关中断
		waitQueue.acquire(this);
		if (this.status != statusFinished) {
			//将KThread下的current对象放入waitQueue
			waitQueue.waitForAccess(KThread.currentThread());
			//将当前线程睡眠
			sleep();
		}
		Machine.interrupt().restore(status);//开中断
	}


    /**
     * Create the idle thread. Whenever there are no threads ready to be run,
     * and <tt>runNextThread()</tt> is called, it will run the idle thread. The
     * idle thread must never block, and it will only be allowed to run when
     * all other threads are blocked.
     *
     * <p>
     * Note that <tt>ready()</tt> never adds the idle thread to the ready set.
     */
    private static void createIdleThread() {
	Lib.assertTrue(idleThread == null);
	
	idleThread = new KThread(new Runnable() {
	    public void run() { while (true) yield(); }
	});
	idleThread.setName("idle");

	Machine.autoGrader().setIdleThread(idleThread);
	
	idleThread.fork();
    }
    
    /**
     * Determine the next thread to run, then dispatch the CPU to the thread
     * using <tt>run()</tt>.
     */
    private static void runNextThread() {
	KThread nextThread = readyQueue.nextThread();
	if (nextThread == null)
	    nextThread = idleThread;

	nextThread.run();
    }

    /**
     * Dispatch the CPU to this thread. Save the state of the current thread,
     * switch to the new thread by calling <tt>TCB.contextSwitch()</tt>, and
     * load the state of the new thread. The new thread becomes the current
     * thread.
     *
     * <p>
     * If the new thread and the old thread are the same, this method must
     * still call <tt>saveState()</tt>, <tt>contextSwitch()</tt>, and
     * <tt>restoreState()</tt>.
     *
     * <p>
     * The state of the previously running thread must already have been
     * changed from running to blocked or ready (depending on whether the
     * thread is sleeping or yielding).
     *
     * @param	finishing	<tt>true</tt> if the current thread is
     *				finished, and should be destroyed by the new
     *				thread.
     */
    private void run() {
	Lib.assertTrue(Machine.interrupt().disabled());

	Machine.yield();

	currentThread.saveState();

	Lib.debug(dbgThread, "Switching from: " + currentThread.toString()
		  + " to: " + toString());

	currentThread = this;

	tcb.contextSwitch();

	currentThread.restoreState();
    }

    /**
     * Prepare this thread to be run. Set <tt>status</tt> to
     * <tt>statusRunning</tt> and check <tt>toBeDestroyed</tt>.
     */
    protected void restoreState() {
	Lib.debug(dbgThread, "Running thread: " + currentThread.toString());
	
	Lib.assertTrue(Machine.interrupt().disabled());
	Lib.assertTrue(this == currentThread);
	Lib.assertTrue(tcb == TCB.currentTCB());

	Machine.autoGrader().runningThread(this);
	
	status = statusRunning;

	if (toBeDestroyed != null) {
	    toBeDestroyed.tcb.destroy();
	    toBeDestroyed.tcb = null;
	    toBeDestroyed = null;
	}
    }

    /**
     * Prepare this thread to give up the processor. Kernel threads do not
     * need to do anything here.
     */
    protected void saveState() {
	Lib.assertTrue(Machine.interrupt().disabled());
	Lib.assertTrue(this == currentThread);
    }

    private static class PingTest implements Runnable {
	PingTest(int which) {
	    this.which = which;
	}
	
	public void run() {
	    for (int i=0; i<5; i++) {
		System.out.println("*** thread " + which + " looped "
				   + i + " times");
		currentThread.yield();
	    }
	}

	private int which;
    }

    /**
     * Tests whether this module is working.
     */
//	public static void test_join() {
//
//		//本题我简单的创建了两个进程A,B，首先执行B，在执行B的过程中对A执行join方法，
//		//因此B被挂起，A开始循环执行，等到A执行完毕，B才会返回执行并结束。
//		Lib.debug(dbgThread, "Enter KThread.selfTest");
//		boolean ints = Machine.interrupt().disable();
//		System.out.println("-----Now we begin to test join()!-----");
//		//fork只是将它们放到就绪队列并未开始执行
//		final KThread thread1 = new KThread(new PingTest(1));
//		//thread1.setName("forked thread").fork();
//		new KThread(new Runnable(){
//			public void run(){
//				System.out.println("*** 线程2运行开始");
//				thread1.join();
//				System.out.println("*** 线程2运行结束");
//			}
//		}).fork();
//		thread1.setName("forked thread").fork();
//		Machine.interrupt().restore(ints);
//	}
	public static void my_join_test(){
		//本题我简单的创建了两个进程A,B，首先执行B，在执行B的过程中对A执行join方法，
		//因此B被挂起，A开始循环执行，等到A执行完毕，B才会返回执行并结束。
		KThread threadA=new KThread(new Runnable() {
			@Override
			public void run() {
				System.out.println("线程A开始运行");
				for (int i=0;i<5;i++) {
					System.out.println(i);
				}
				System.out.println("线程A结束运行");
			}
		});
		KThread threadB=new KThread(new Runnable() {
			@Override
			public void run() {
				System.out.println("线程B开始运行");
				threadA.join();
				System.out.println("线程B结束运行");
			}
		});
		threadB.fork();
		threadA.fork();
	}
	public static void test_condition_var()
	{
		Lib.debug(dbgThread, "Enter KThread.selfTest");
		Lock lock=new Lock();
		Condition2 condition2_var=new Condition2(lock);
		KThread threadA=new KThread(new Runnable() {
			@Override
			public void run() {
				lock.acquire();
				System.out.println("A将要sleep");
				condition2_var.sleep();
				System.out.println("A被wake");
				lock.release();
				System.out.println("A执行成功");
			}
		});
		KThread threadB=new KThread(new Runnable() {
			@Override
			public void run() {
				lock.acquire();
				System.out.println("B将要sleep");
				condition2_var.sleep();
				System.out.println("B被wake");
				lock.release();
				System.out.println("B执行成功");
			}
		});
		KThread threadC=new KThread(new Runnable() {
			@Override
			public void run() {
				lock.acquire();
				System.out.println("C开始执行，C将要唤醒所有进程");
				condition2_var.wakeAll();
				lock.release();
				System.out.println("C唤醒了所有进程");
			}
		});
		threadA.fork();
		threadB.fork();
		threadC.fork();
	}
	public static void test_Alarm(){
		KThread threadA=new KThread(new Runnable() {
			int wait=10;
			@Override
			public void run() {
				System.out.println("ThreadA进入睡眠,时间:"+Machine.timer().getTime()+"等待时间:"+wait);
				ThreadedKernel.alarm.waitUntil(wait);
				System.out.println("ThreadA执行结束后的系统时间:"+Machine.timer().getTime());
			}
		});
		KThread threadB=new KThread(new Runnable() {
			int wait=150;
			@Override
			public void run() {
				System.out.println("ThreadB进入睡眠,时间:"+Machine.timer().getTime()+"等待时间:"+wait);
				ThreadedKernel.alarm.waitUntil(wait);
				System.out.println("ThreadB执行结束后的系统时间:"+Machine.timer().getTime());

			}
		});
		threadA.fork();
		threadB.fork();
	}
	public static void test_communicator(){
		Communicator communicator=new Communicator();
		KThread speaker1=new KThread(new Runnable() {
			@Override
			public void run() {
				communicator.speak(1);
				System.out.println("speaker1说1");
			}
		});
		KThread speaker2=new KThread(new Runnable() {
			@Override
			public void run() {
				communicator.speak(2);
				System.out.println("speaker2说2");
			}
		});
		KThread listener1=new KThread(new Runnable() {
			@Override
			public void run() {
				int hear=communicator.listen();
				System.out.println("listener1听到了"+hear);
			}
		});
		KThread listener2=new KThread(new Runnable() {
			@Override
			public void run() {
				int hear=communicator.listen();
				System.out.println("listener2听到了"+hear);
			}
		});
		speaker1.fork();
		listener1.fork();



	}
	public static void test_Priority(){
		boolean status=Machine.interrupt().disable();//关中断
		//线程A
		KThread kThreadA=new KThread(new Runnable() {
			@Override
			public void run() {
				System.out.println("A线程开始运行");
				System.out.println("A让出CPU");
				yield();
				System.out.println("A重新使用CPU");
				System.out.println("A线程结束运行");
			}
		}).setName("threadA");//创建线程A
		//线程B
		new PriorityScheduler().setPriority(kThreadA,2);//将线程A的优先级设为2
		System.out.println("threadA的优先级为："+new PriorityScheduler().getThreadState(kThreadA).priority);//输出A的优先级
		KThread kThreadB=new KThread(new Runnable() {
			@Override
			public void run() {
				System.out.println("B线程开始运行");
				System.out.println("B让出CPU");
				yield();
				System.out.println("B重新使用CPU");
				System.out.println("B线程结束运行");
			}
		}).setName("threadB");//创建线程B
		new PriorityScheduler().setPriority(kThreadB,4);//将线程B的优先级设为4
		System.out.println("threadB的优先级为："+new PriorityScheduler().getThreadState(kThreadB).priority);//输出B的优先级
		//线程C
		KThread kThreadC=new KThread(new Runnable() {
			@Override
			public void run() {
				System.out.println("C线程开始运行");
				System.out.println("C让出CPU");
				yield();
				System.out.println("C重新使用CPU");
				System.out.println("C线程等待A线程");
				kThreadA.join();
				System.out.println("C重新使用CPU");
				System.out.println("C线程结束运行");
				// 不允许优先级传递时打印下面的语句
				//System.out.println("\n<--- 题目 5 结束测试 --->\n");
			}
		}).setName("threadC");//创建线程C
		new PriorityScheduler().setPriority(kThreadC,6);//将线程C的优先级设为6
		System.out.println("threadC的优先级为："+new PriorityScheduler().getThreadState(kThreadC).priority);//输出C的优先级

		kThreadA.fork();//run
		kThreadB.fork();
		kThreadC.fork();



	}
	public static void test_Boat(){
		Boat.selfTest();
	}
    public static void selfTest() {
	Lib.debug(dbgThread, "Enter KThread.selfTest");
	
	//new KThread(new PingTest(1)).setName("forked thread").fork();
	//new PingTest(0).run();

	//my_join_test();
	//test_condition_var();
	//test_Alarm();
	//test_communicator();
    //test_Priority();
	//test_Boat();
	}

    private static final char dbgThread = 't';

    /**
     * Additional state used by schedulers.
     *
     * @see	nachos.threads.PriorityScheduler.ThreadState
     */
    public Object schedulingState = null;
	ThreadQueue waitQueue = ThreadedKernel.scheduler.newThreadQueue(true);
	private boolean hasAcquired = false;
    private static final int statusNew = 0;
    private static final int statusReady = 1;
    private static final int statusRunning = 2;
    private static final int statusBlocked = 3;
    private static final int statusFinished = 4;

    /**
     * The status of this thread. A thread can either be new (not yet forked),
     * ready (on the ready queue but not running), running, or blocked (not
     * on the ready queue and not running).
     */
    private int status = statusNew;
    private String name = "(unnamed thread)";
    private Runnable target;
    private TCB tcb;

    /**
     * Unique identifer for this thread. Used to deterministically compare
     * threads.
     */
    private int id = numCreated++;
    /** Number of times the KThread constructor was called. */
    private static int numCreated = 0;
	// start
	private int join_counter = 0;

	// end
    private static ThreadQueue readyQueue = null;
    private static KThread currentThread = null;
    private static KThread toBeDestroyed = null;
    private static KThread idleThread = null;
}
