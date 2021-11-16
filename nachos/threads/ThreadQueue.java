package nachos.threads;

/**
 * Schedules access to some sort of resource with limited access constraints. A
 * thread queue can be used to share this limited access among multiple
 * threads.
 *
 * <p>
 * Examples of limited access in Nachos include:
 *
 * <ol>
 * <li>the right for a thread to use the processor. Only one thread may run on
 * the processor at a time.
 *
 * <li>the right for a thread to acquire a specific lock. A lock may be held by
 * only one thread at a time.
 *
 * <li>the right for a thread to return from <tt>Semaphore.P()</tt> when the
 * semaphore is 0. When another thread calls <tt>Semaphore.V()</tt>, only one
 * thread waiting in <tt>Semaphore.P()</tt> can be awakened.
 *
 * <li>the right for a thread to be woken while sleeping on a condition
 * variable. When another thread calls <tt>Condition.wake()</tt>, only one
 * thread sleeping on the condition variable can be awakened.
 *
 * <li>the right for a thread to return from <tt>KThread.join()</tt>. Threads
 * are not allowed to return from <tt>join()</tt> until the target thread has
 * finished.
 * </ol>
 *
 * All these cases involve limited access because, for each of them, it is not
 * necessarily possible (or correct) for all the threads to have simultaneous
 * access. Some of these cases involve concrete resources (e.g. the processor,
 * or a lock); others are more abstract (e.g. waiting on semaphores, condition
 * variables, or join).
 *
 * <p>
 * All thread queue methods must be invoked with <b>interrupts disabled</b>.
 */
public abstract class ThreadQueue {
    /**
     * Notify this thread queue that the specified thread is waiting for
     * access. This method should only be called if the thread cannot
     * immediately obtain access (e.g. if the thread wants to acquire a lock
     * but another thread already holds the lock).
     *
     * <p>
     * A thread must not simultaneously wait for access to multiple resources.
     * For example, a thread waiting for a lock must not also be waiting to run
     * on the processor; if a thread is waiting for a lock it should be
     * sleeping.
     *
     * <p>
     * However, depending on the specific objects, it may be acceptable for a
     * thread to wait for access to one object while having access to another.
     * For example, a thread may attempt to acquire a lock while holding
     * another lock. Note, though, that the processor cannot be held while
     * waiting for access to anything else.
     *
     * @param	thread	the thread waiting for access.
     */
    /**
     * 通知这个线程队列，指定的线程正在等待
     *访问。这个方法只应该在线程不能
     * 立即获得访问权（例如，如果该线程想获得一个锁
     * 但另一个线程已经持有该锁）。
     *
     * <p>
     * 一个线程不能同时等待对多个资源的访问。
     * 例如，一个等待锁的线程不能同时等待运行
     * 在处理器上；如果一个线程在等待一个锁，它应该是
     * 睡觉。
     *
     * <p>
     * 然而，根据具体的对象，一个线程等待访问一个对象可能是可以接受的。
     * 线程在访问一个对象的同时等待访问另一个对象。
     * 例如，一个线程可以在持有一个锁的同时试图获得一个锁
     * 另一个锁。但是，请注意，处理器不能在等待访问其他东西的时候被锁住。
     * 等待对其他东西的访问。
     *
     * @param thread 等待访问的线程。
     */
    public abstract void waitForAccess(KThread thread);

    /**
     * Notify this thread queue that another thread can receive access. Choose
     * and return the next thread to receive access, or <tt>null</tt> if there
     * are no threads waiting.
     *
     * <p>
     * If the limited access object transfers priority, and if there are other
     * threads waiting for access, then they will donate priority to the
     * returned thread.
     *
     * @return	the next thread to receive access, or <tt>null</tt> if there
     *		are no threads waiting.
     */

    /**
     * 通知这个线程队列，另一个线程可以接受访问。选择
     * 并返回下一个接受访问的线程，如果没有线程等待，则返回<tt>null</tt>。
     * 没有线程在等待。
     *
     * <p>
     * 如果有限的访问对象转移了优先权，并且如果有其他的
     * 如果有其他线程在等待访问，那么它们将把优先权捐给
     * 返回的线程。
     *
     *返回下一个接受访问的线程，如果没有线程等待，则返回<tt>null</tt>。
     * 没有线程在等待。
     */
    public abstract KThread nextThread();

    /**
     * Notify this thread queue that a thread has received access, without
     * going through <tt>request()</tt> and <tt>nextThread()</tt>. For example,
     * if a thread acquires a lock that no other threads are waiting for, it
     * should call this method.
     *
     * <p>
     * This method should not be called for a thread returned from
     * <tt>nextThread()</tt>.
     *
     * @param	thread	the thread that has received access, but was not
     * 			returned from <tt>nextThread()</tt>.
     */
    /**
     * 通知这个线程队列，一个线程已经收到了访问，而不需要
     * 通过<tt>request()</tt>和<tt>nextThread()</tt>。比如说。
     * 如果一个线程获得了一个没有其他线程在等待的锁，它应该调用这个方法。
     * 应该调用这个方法。
     *
     * <p>
     * 这个方法不应该被调用给一个从
     * <tt>nextThread()</tt>。
     *
     * @param thread 该线程已收到访问权限，但没有被
     *从<tt>nextThread()</tt>返回。
     */
    public abstract void acquire(KThread thread);

    /**
     * Print out all the threads waiting for access, in no particular order.
     */
    public abstract void print();

}
