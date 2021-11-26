package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.util.LinkedList;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
    /**
     * Allocate a new user kernel.
     */
	private static LinkedList<Integer> AllFreePageNums;
    public UserKernel() {
	super();
		// 初始化内存列表
		AllFreePageNums = new LinkedList<>();

		// 获取物理页数
		int numPhysPages = Machine.processor().getNumPhysPages();

		// 为空闲页编号
		for (int i = 0; i < numPhysPages; i++) {
			AllFreePageNums.add(i);
		}
    }
	public static LinkedList<Integer> getFreePageNums(int numPages) {
		// 声明并初始化一个空闲页号链表
		LinkedList<Integer> freePageNums = new LinkedList<>();

		// 如果空闲页足够
		if (AllFreePageNums.size() >= numPages) {
			// 从空闲页中取出指定数量的页号，并添加到 freePages 中
			for (int i = 0; i < numPages; i++) {
				freePageNums.add(AllFreePageNums.removeFirst());
			}
		}

		return freePageNums;
	}

	// 归还空闲页
	public static void releaseOwnPageNums(LinkedList<Integer> ownPageNums){
		// 如果进程没有占有页，直接返回
		if (ownPageNums == null || ownPageNums.isEmpty()) {
			return;
		}

		// 将进程中页号转换成空闲页号
		for (int i = 0; i < ownPageNums.size(); i ++) {
			AllFreePageNums.add(ownPageNums.removeFirst());
		}
	}

    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
	super.initialize(args);

	console = new SynchConsole(Machine.console());
	
	Machine.processor().setExceptionHandler(new Runnable() {
		public void run() { exceptionHandler(); }
	    });
    }

    /**
     * Test the console device.
     */	
    public void selfTest() {
	super.selfTest();

//	System.out.println("Testing the console device. Typed characters");
//	System.out.println("will be echoed until q is typed.");
//
//	char c;
//
//	do {
//	    c = (char) console.readByte(true);
//	    console.writeByte(c);
//	}
//	while (c != 'q');
//
//	System.out.println("");
    }

    /**
     * Returns the current process.
     *
     * @return	the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
	if (!(KThread.currentThread() instanceof UThread))
	    return null;
	
	return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
	Lib.assertTrue(KThread.currentThread() instanceof UThread);

	UserProcess process = ((UThread) KThread.currentThread()).process;
	int cause = Machine.processor().readRegister(Processor.regCause);
	process.handleException(cause);
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see	nachos.machine.Machine#getShellProgramName
     */
    public void run() {
	super.run();

	UserProcess process = UserProcess.newUserProcess();
	
	String shellProgram = Machine.getShellProgramName();	
	Lib.assertTrue(process.execute(shellProgram, new String[] { }));

	KThread.currentThread().finish();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
	super.terminate();
    }

    /** Globally accessible reference to the synchronized console. */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;
}
