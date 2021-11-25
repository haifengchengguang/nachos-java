package nachos.threads;

import nachos.machine.*;

import java.lang.management.ThreadInfo;
import java.util.LinkedList;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    /**
     * 定时器中断处理程序。这是由机器的定时器调用的
     * 定期调用（大约每500个时钟刻度）。导致当前的
     * 导致当前的线程放弃，如果有另一个线程需要运行，则强制进行上下文切换。
     * 应该被运行。
     */
    public void timerInterrupt() {
        boolean status=Machine.interrupt().disable();
        long currentTime=Machine.timer().getTime();//获取现在的时间
        int size = list.size();//遍历链表，将时间到了的线程唤醒
        if(size!=0){
            for (int i=0;i<size;i++)
            {
                if(currentTime>=list.get(i).getTime())
                {
                    KThread thread=list.get(i).getThread();
                    thread.ready();
                    list.remove(i);
                    size--;
                    i--;
                    currentTime=Machine.timer().getTime();

                }
            }
        }
	    KThread.currentThread().yield();//
        Machine.interrupt().restore(status);
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    /**
     * 让当前线程睡眠至少<i>x</i> ticks。
     * 在定时器中断处理程序中把它唤醒。该线程必须是
     * 在第一个定时器中被唤醒（放在调度器的准备集中）。
     * 中断的地方
     *

     *（当前时间）>=（WaitUntil调用时间）+（x）
     </blockquote> *（当前时间）>=（WaitUntil调用时间）+（x）。
     *
     * @param x 要等待的最小时钟刻度数。
     *
     * @see nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
	// for now, cheat just to get something working (busy waiting is bad)
        boolean status=Machine.interrupt().disable();//关中断
	    long wakeTime = Machine.timer().getTime() + x;//计算唤醒时间
        ThreadInfo threadInfo=new ThreadInfo(KThread.currentThread(),wakeTime);//线程及唤醒时间ThreadInfo
        list.add(threadInfo);//链表保存threadinfo
        KThread.currentThread().sleep();//sleep
        Machine.interrupt().restore(status);

//	while (wakeTime > Machine.timer().getTime())
//	    KThread.yield();


    }
    private LinkedList<ThreadInfo> list = new LinkedList();

    /**
     *存储等待线程的信息，包括线程号和等待时间
     *内部类,存放线程信息
     */
    private class ThreadInfo{
        private KThread thread;
        private long time;
        public ThreadInfo(KThread thread,long time){
            this.thread=thread;
            this.time=time;
        }
        public KThread getThread() {
            return thread;
        }
        public void setThread(KThread thread) {
            this.thread = thread;
        }
        public long getTime() {
            return time;
        }
        public void setTime(long time) {
            this.time = time;
        }
    }
}
