package nachos.threads;

import nachos.machine.*;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
    /**
     * Allocate a new lottery scheduler.
     */
    public LotteryScheduler() {
    }

    /**
     * Allocate a new lottery thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer tickets from waiting threads
     *					to the owning thread.
     * @return	a new lottery thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new LotteryQueue(transferPriority);
    }
    protected class LotteryThreadState extends ThreadState {
        public LotteryThreadState(KThread thread) {
            super(thread);
        }

        public int getEffectivePriority() {
            // 尝试使用之前保存的数据
            if (KThread.getPriorityStatus()) {
                return effectivePriority;
            }

            // 重新计算有效优先级
            effectivePriority = priority;

            // 遍历该线程的等待线程列表
            for (KThread waitThread : thread.getWaitThreadList()) {
                // 等待线程的有效优先级
                effectivePriority += getThreadState(waitThread).getEffectivePriority();
            }

            return effectivePriority;
        }
    }
    protected class LotteryQueue extends PriorityQueue {
        LotteryQueue(boolean transferPriority) {
            super(transferPriority);
        }

        protected ThreadState pickNextThread() {
            // 计算彩票总数
            int lotterySum = 0;
            for (ThreadState lotteryThreadState : waitlist) {
                if (transferPriority) {
                    lotterySum += lotteryThreadState.getEffectivePriority();
                } else {
                    lotterySum += lotteryThreadState.getPriority();
                }

            }

            // 当前存在可运行的线程
            if (lotterySum != 0) {
                // 指定获胜彩票
                int winLottery = Lib.random(lotterySum) + 1;

                // 当前彩票计数
                int currentLotteryNum = 0;

                // 遍历所有线程，直到找到持有中奖彩票的线程
                for (ThreadState lotteryThreadState: waitlist) {
                    if (transferPriority) {
                        currentLotteryNum += lotteryThreadState.getEffectivePriority();
                    } else {
                        currentLotteryNum += lotteryThreadState.getPriority();
                    }

                    // 找到获奖彩票
                    if (currentLotteryNum >= winLottery) {
                        return lotteryThreadState;
                    }
                }
            }

            return null;
        }
    }


    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null)
            thread.schedulingState = new LotteryThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }


}
