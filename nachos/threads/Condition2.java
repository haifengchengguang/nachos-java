package nachos.threads;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
	this.conditionLock = conditionLock;
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());//检测当前线程是否获得锁
    boolean status=Machine.interrupt().disable();//关中断
	conditionLock.release();//释放锁
	if(KThread.currentThread()!=null)
    waitqueue.waitForAccess(KThread.currentThread());//放入等待队列
        KThread.currentThread().sleep();//将当前线程睡眠

	conditionLock.acquire();//获得锁
    Machine.interrupt().restore(status);//开中断
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
	    Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        //start
        boolean status = Machine.interrupt().disable();
        KThread thread = waitqueue.nextThread();
        if(!(thread==null))
            thread.ready();//只是将线程加入就绪队列但不释放锁
//	    else if(thread==null)
//		System.out.println("111.txt");
        Machine.interrupt().restore(status);
        //end
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
	    Lib.assertTrue(conditionLock.isHeldByCurrentThread());//当前进程有锁
        //start
        boolean status = Machine.interrupt().disable();
        KThread thread = waitqueue.nextThread();//唤醒全部进程
        while(thread!=null){
            thread.ready();
            thread=waitqueue.nextThread();
        }
        Machine.interrupt().restore(status);
        //end
    }

    private Lock conditionLock;

    private ThreadQueue waitqueue=ThreadedKernel.scheduler.newThreadQueue(true);;
}
