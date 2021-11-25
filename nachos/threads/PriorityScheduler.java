package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    }
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
		public LinkedList<ThreadState> waitlist=new LinkedList<>();

		PriorityQueue(boolean transferPriority) {
	    this.transferPriority = transferPriority;
	}
//   将需要等待获得资源的线程加入一个等待队列等待调度。
	public void waitForAccess(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).waitForAccess(this);
	}

	public void acquire(KThread thread) {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    getThreadState(thread).acquire(this);
	}
		//返回下一个要执行的线程，遍历队列，计算出所有线程的有效优先级
// （计算的时候是递归计算每个ThreadState队列中每个线程的有效优先级，
// 大于自己的优先级，则将它的有效优先级赋给自己），取出有效优先级最大的线程执行。
	public KThread nextThread() {
	    Lib.assertTrue(Machine.interrupt().disabled());
		ThreadState next = pickNextThread();//下一个选择的线程
		if(next == null)//如果为null,则返回null
			return null;
		waitlist.remove(next);
		return next.thread;
	}
		/** *返回<tt>nextThread()</tt>将返回的下一个线程，*不修改这个队列的状态。 * * @返回<tt>nextThread()</tt>将返回的下一个线程。
	/**
	 * Return the next thread that <tt>nextThread()</tt> would return,
	 * without modifying the state of this queue.
	 *
	 * @return	the next thread that <tt>nextThread()</tt> would
	 *		return.
	 */
		//找出优先级最大的线程
	protected ThreadState pickNextThread() {
		ThreadState nextThreadState = null;

		// 当前最大优先级
		int currentMaxPriority = -1;

		// 遍历优先级队列中的每一个线程
		for (ThreadState threadState: waitlist) {

			// 判断是否可以传递优先级
			int threadPriority;
			if (transferPriority) {
				// 获取线程包括其等待队列的最高优先级
				threadPriority = threadState.getEffectivePriority();
			} else {
				// 获取线程的优先级（未传递）
				threadPriority = threadState.getPriority();
			}

			// 选择优先级更高的线程
			if (threadPriority > currentMaxPriority) {
				nextThreadState = threadState;
				currentMaxPriority = nextThreadState.priority;
			}
		}

		return nextThreadState;
	}
	
	public void print() {
	    Lib.assertTrue(Machine.interrupt().disabled());
	    // implement me (if you want)
		for (Iterator i=waitlist.iterator();i.hasNext();){
			System.out.println(i.next()+"");
		}
	}

	/**
	 * <tt>true</tt> if this queue should transfer priority from waiting
	 * threads to the owning thread.
	 */
	public boolean transferPriority;
    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
	/**
	 * Allocate a new <tt>ThreadState</tt> object and associate it with the
	 * specified thread.
	 *
	 * @param	thread	the thread this state belongs to.
	 */
	public ThreadState(KThread thread) {
	    this.thread = thread;
	    
	    setPriority(priorityDefault);
	}

	/**
	 * Return the priority of the associated thread.
	 *
	 * @return	the priority of the associated thread.
	 */
	public int getPriority() {
	    return priority;
	}

	/**
	 * Return the effective priority of the associated thread.
	 *
	 * @return	the effective priority of the associated thread.
	 */
	public int getEffectivePriority() {
		// 如果该线程有等待队列
		if (thread.waitQueue != null) {
			// 创建一个链表来临时存放该线程的等待队列中的线程
			ThreadQueue newQueue = ThreadedKernel.scheduler.newThreadQueue(true);

			// 遍历该线程的等待队列中的所有线程
			KThread waitThread;
			int waitThreadPriority;
			while ((waitThread = thread.waitQueue.nextThread()) != null) {
				// 将取出的线程添加到队列中去
				newQueue.waitForAccess(waitThread);
				// 如果等待队列中的线程的优先级更高，就把优先级传递给该线程
				waitThreadPriority = getThreadState(waitThread).getPriority();
				if (priority < waitThreadPriority){
					priority = waitThreadPriority;
				}
			}

			// 完成等待队列的转移和交换
			thread.waitQueue = newQueue;
		}

		return priority;
	}

	/**
	 * Set the priority of the associated thread to the specified value.
	 *
	 * @param	priority	the new priority.
	 */
	public void setPriority(int priority) {
	    if (this.priority == priority)
		return;
	    
	    this.priority = priority;
	    
	    // implement me
	}

	/**
	 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
	 * the associated thread) is invoked on the specified priority queue.
	 * The associated thread is therefore waiting for access to the
	 * resource guarded by <tt>waitQueue</tt>. This method is only called
	 * if the associated thread cannot immediately obtain access.
	 *
	 * @param	waitQueue	the queue that the associated thread is
	 *				now waiting on.
	 *
	 * @see	nachos.threads.ThreadQueue#waitForAccess
	 */
	public void waitForAccess(PriorityQueue waitQueue) {
	    // implement me
		Lib.assertTrue(Machine.interrupt().disabled());

		// 将线程加入优先级队列中去
		waitQueue.waitlist.add(this);
	}

	/**
	 * Called when the associated thread has acquired access to whatever is
	 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
	 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
	 * <tt>thread</tt> is the associated thread), or as a result of
	 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
	 *
	 * @see	nachos.threads.ThreadQueue#acquire
	 * @see	nachos.threads.ThreadQueue#nextThread
	 *
	 */

	public void acquire(PriorityQueue waitQueue) {
	    // implement me

		Lib.assertTrue(Machine.interrupt().disabled());

		// 断言此时优先级队列中内容为空
		Lib.assertTrue(waitQueue.waitlist.isEmpty());
	}	

	/** The thread with which this object is associated. */	   
	protected KThread thread;

	/** The priority of the associated thread. */
	protected int priority;
    }
}
