package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */

    public UserProcess() {
		this.openFiles = new OpenFile[max_openfile_num];
		this.openFiles[0] = UserKernel.console.openForReading();
		this.openFiles[1] = UserKernel.console.openForWriting();
// 设置当前进程 id
		this.processId = processIdCounter;

		// 进程 id 计数器自增
		processIdCounter++;

		// 将该进程添加到进程映射表中
		processMap.put(this.processId, this);

		// -1 表示无父进程
		parentProcessId = -1;

		// 子进程 id 链表初始化
		childrenProcessId = new LinkedList<>();

		// 初始化 join 用到的锁和条件变量
		joinLock = new Lock();
		joinCondition = new Condition(joinLock);
		//this.processID = processesCreated++;
		//this.childrenCreated = new HashSet<Integer>();
//	int numPhysPages = Machine.processor().getNumPhysPages();
//	pageTable = new TranslationEntry[numPhysPages];
//	for (int i=0; i<numPhysPages; i++)
//	    pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
		// 偏移量和长度非负，且不能越界
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		// 获取物理内存
		byte[] memory = Machine.processor().getMemory();

		// 传输的字节数过多，会导致虚拟内存越界
		if (length > pageSize * numPages - vaddr) {
			// 截取不超过越界的部分
			length = pageSize * numPages - vaddr;
		}

		// 不断读取虚拟内存，直到读完指定长度的数据
		int successRead = 0;
		while (successRead < length) {
			// 计算页号
			int pageNum = Processor.pageFromAddress(vaddr + successRead);

			// 检查是否越界
			if (pageNum < 0 || pageNum >= pageTable.length) {
				return successRead;
			}

			// 计算页偏移量
			int pageOffset = Processor.offsetFromAddress(vaddr + successRead);

			// 计算当页剩余容量
			int pageRemain = pageSize - pageOffset;

			// 比较未读取的内容与当页未使用的空间，取较小值用于数据转移
			int amount = Math.min(length - successRead, pageRemain);

			// 计算真实地址
			int realAddress = pageTable[pageNum].ppn * pageSize + pageOffset;

			// 将数据从内存复制到指定数组
			System.arraycopy(memory, realAddress, data, offset + successRead, amount);

			// 成功读取的数据量
			successRead = successRead + amount;
		}

		return successRead;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
		// 偏移量和长度非负，且不能越界
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		// 获取物理内存
		byte[] memory = Machine.processor().getMemory();

		// 传输的字节数过多，会导致虚拟内存越界
		if (length > pageSize * numPages - vaddr) {
			// 截取不超过越界的部分
			length = pageSize * numPages - vaddr;
		}

		// 不断写入虚拟内存，直到写完指定长度的数据
		int successWrite = 0;
		while (successWrite < length) {
			// 计算页号
			int pageNum = Processor.pageFromAddress(vaddr + successWrite);

			// 检查是否越界
			if (pageNum < 0 || pageNum >= pageTable.length) {
				return successWrite;
			}

			// 计算页偏移量
			int pageOffset = Processor.offsetFromAddress(vaddr + successWrite);

			// 计算当页剩余容量
			int pageRemain = pageSize - pageOffset;

			// 比较未读取的内容与当页未使用的空间，取较小值用于数据转移
			int amount = Math.min(length - successWrite, pageRemain);

			// 计算真实地址
			int realAddress = pageTable[pageNum].ppn * pageSize + pageOffset;

			// 如果当前页为只读状态，终止数据转移
			if (pageTable[pageNum].readOnly) {
				return successWrite;
			}

			// 将数据从内存复制到指定数组
			System.arraycopy(data, offset + successWrite, memory, realAddress, amount);

			// 成功读取的数据量
			successWrite = successWrite + amount;
		}

		return successWrite;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counter initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
	private LinkedList<Integer> ownPageNums;
    protected boolean loadSections() {
	if (numPages > Machine.processor().getNumPhysPages()) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    return false;
	}
		// 获取空闲页号
		ownPageNums = UserKernel.getFreePageNums(numPages);

		// 检查空闲页是否充足
		if (ownPageNums.isEmpty()) {
			return false;
		}

		// 页表数组初始化
		pageTable = new TranslationEntry[numPages];

		// 将数组中的页表初始化
		for (int i = 0; i < numPages; i++) {
			pageTable[i] = new TranslationEntry(i, ownPageNums.get(i), true, false, false, false);
		}

		// 加载用户程序到内存
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				// 装入页
				section.loadPage(i, pageTable[vpn].ppn);
			}
		}


		return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
		// 关闭当前进程正在执行的文件
		coff.close();

		// 将该进程拥有的页转换为空闲页
		UserKernel.releaseOwnPageNums(ownPageNums);
    }    

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {

	Machine.halt();
	
	Lib.assertNotReached("Machine.halt() did not halt machine!");
	return 0;
    }
	/* 文件管理系统调用: creat, open, read, write, close, unlink
	 *
	 * 文件描述符是一个小的非负整数，它引用磁盘上的文件或流（例如控制台输入、控制台输出和网络连接）
	 * 可以将文件描述符传递给 read() 和 write()，以读取/写入相应的文件/流
	 * 还可以将文件描述符传递给 close()，以释放文件描述符和任何相关资源
	 */

	/**
	 * 尝试打开给定名称的磁盘文件，如果该文件不存在，则创建该文件，并返回可用于访问该文件的文件描述符
	 * <p>
	 * 注意 creat() 只能用于在磁盘上创建文件，永远不会返回引用流的文件描述符
	 *
	 * @param fileAddress 目标文件地址
	 * @return 新的文件描述符，如果发生错误，则为 -1
	 */
	private int handleCreate(int fileAddress) {
		// 从该进程的虚拟内存中读取字符串
		String fileName = readVirtualMemoryString(fileAddress, MAX_FILENAME_LENGTH);

		// 非空检验
		if (fileName == null || fileName.length() == 0) {
			return -1;
		}

		// 与该文件相关联的文件描述符
		int fileDescriptor = -1;

		// 遍历 openFiles
		for (int i = 0; i < this.openFiles.length; i++) {
			// 找出一个暂时未与进程打开文件相关联的文件描述符
			if (this.openFiles[i] == null) {
				// 此时该文件描述符未进行关联
				if (fileDescriptor == -1) {
					// 设置关联
					fileDescriptor = i;
				}
				continue;
			}

			// 检查该文件是否已经打开
			if (this.openFiles[i].getName().equals(fileName)) {
				return i;
			}
		}

		// 暂时没有空闲的文件描述符
		if (fileDescriptor == -1) {
			return -1;
		}

		// 打开该文件，如果文件不存在，则创建一个文件
		OpenFile openFile = ThreadedKernel.fileSystem.open(fileName, true);

		// 使空闲的文件描述符与该文件相关联
		this.openFiles[fileDescriptor] = openFile;

		return fileDescriptor;
	}

	/**
	 * 尝试打开指定名称的文件并返回文件描述符
	 * <p>
	 * 注意 open() 只能用于打开磁盘上的文件，永远不会返回引用流的文件描述符
	 *
	 * @param fileAddress 目标文件地址
	 * @return 新的文件描述符，如果发生错误，则为 -1
	 */
	private int handleOpen(int fileAddress) {
		// 从该进程的虚拟内存中读取以 null 结尾的字符串
		String fileName = readVirtualMemoryString(fileAddress, MAX_FILENAME_LENGTH);

		// 非空检验
		if (fileName == null || fileName.length() == 0) {
			return -1;
		}

		// 与该文件相关联的文件描述符
		int fileDescriptor = -1;

		// 遍历 openFiles
		for (int i = 0; i < this.openFiles.length; i++) {
			// 找出一个暂时未与进程打开文件相关联的文件描述符
			if (this.openFiles[i] == null) {
				// 此时该文件描述符未进行关联
				if (fileDescriptor == -1) {
					// 设置关联
					fileDescriptor = i;
				}
				continue;
			}

			// 检查该文件是否已经打开
			if (this.openFiles[i].getName().equals(fileName)) {
				return i;
			}
		}

		// 暂时没有空闲的文件描述符
		if (fileDescriptor == -1) {
			return -1;
		}

		// 打开该文件，如果文件不存在，则返回 null
		OpenFile openFile = ThreadedKernel.fileSystem.open(fileName, false);
		if (openFile == null) {
			return -1;
		}

		// 使空闲的文件描述符与该文件相关联
		this.openFiles[fileDescriptor] = openFile;

		return fileDescriptor;
	}


	/**
	 * 尝试从 fileDescriptor 指向的文件或流中读取数个字节到缓冲区
	 * <p>
	 * 成功时，返回读取的字节数
	 * 如果文件描述符引用磁盘上的文件，则文件地址将按此数字前进
	 * <p>
	 * 如果此数字小于请求的字节数，则不一定是错误
	 * 如果文件描述符引用磁盘上的文件，则表示已到达文件末尾
	 * 如果文件描述符引用一个流，这表示现在实际可用的字节比请求的字节少，但将来可能会有更多的字节可用
	 * 注意 read() 从不等待流有更多数据，它总是尽可能立即返回
	 * <p>
	 * 出现错误时，返回 -1，新文件地址为未定义，发生这种情况的原因可能是：
	 * fileDescriptor 无效、缓冲区的一部分为只读或无效、网络流已被远程主机终止且没有更多可用数据
	 *
	 * @param fileDescriptor 文件描述符
	 * @param vaddr 虚拟内存地址
	 * @param length 读取内容长度
	 * @return 成功读取的字节数，如果失败，则为 -1
	 */
	private int handleRead(int fileDescriptor, int vaddr, int length) {
		// 检查文件描述符是否有效
		if (openFiles[fileDescriptor] == null) {
			return -1;
		}

		// 获取该文件
		OpenFile openFile = openFiles[fileDescriptor];

		// 用于存储读取内容的缓冲区
		byte[] buf = new byte[length];

		// 将内容读取到 buf，并获得成功读取的字节数
		int successRead = openFile.read(buf, 0, length);

		// 将数据从缓冲区写入到该进程的虚拟内存，并获得成功写入的字节数
		int successWrite = writeVirtualMemory(vaddr, buf, 0, successRead);

		// 检查传输的完整性
		if (successRead != successWrite) {
			return -1;
		}

		return successRead;
	}

	/**
	 * 尝试将缓冲区中的数个字节写入到 fileDescriptor 所引用的文件或流
	 * write() 可以在字节实际流动到文件或流之前返回
	 * 但是，如果内核队列暂时已满，则对流的写入可能会阻塞
	 * <p>
	 * 成功时，将返回写入的字节数（ 表示未写入任何内容），文件位置将按此数字前进
	 * 如果此数字小于请求的字节数，则为错误
	 * 对于磁盘文件，这表示磁盘已满
	 * 对于流，这表示在传输所有数据之前，远程主机终止了流
	 * <p>
	 * 出现错误时，返回 -1，新文件地址为未定义，发生这种情况的原因可能是：
	 * fileDescriptor 无效、缓冲区的一部分为只读或无效、网络流已被远程主机终止
	 *
	 * @param fileDescriptor 文件描述符
	 * @param vaddr 虚拟内存地址
	 * @param length 写入内容长度
	 * @return 成功读取的字节数，如果失败，则为 -1
	 */
	private int handleWrite(int fileDescriptor, int vaddr, int length) {
		// 检查文件描述符是否有效
		if (openFiles[fileDescriptor] == null) {
			return -1;
		}

		// 获取该文件
		OpenFile openFile = openFiles[fileDescriptor];

		// 用于存储读取内容的缓冲区
		byte[] buf = new byte[length];

		// 将数据从该进程的虚拟内存读取到缓冲区，并获得成功读取的字节数
		int successRead = readVirtualMemory(vaddr, buf);

		// 将内容写入到该文件，并获得成功写入的字节数
		int successWrite = openFile.write(buf, 0, successRead);

		// 检查传输的完整性
		if (successRead != successWrite) {
			return -1;
		}

		return successRead;
	}

	/**
	 * 关闭文件描述符，使其不再引用任何文件或流，并且可以重用
	 *
	 * 如果文件描述符引用一个文件，则 write() 写入的所有数据将在 close() 返回之前转移到磁盘
	 * 如果文件描述符引用流，则 write() 写入的所有数据最终都将转移（除非流被远程终止），但不一定在 close() 返回之前
	 *
	 * 与文件描述符关联的资源将被释放
	 * 如果描述符是使用 unlink 删除的磁盘文件的最后一个引用，则该文件将被删除（此详细信息由文件系统实现处理）
	 *
	 * @param fileDescriptor 文件描述符
	 * @return 成功时为 0，错误发生时为 -1
	 */
	private int handleClose(int fileDescriptor) {
		// 检查文件描述符是否有效
		if (openFiles[fileDescriptor] == null) {
			return -1;
		}

		// 获取该文件
		OpenFile openFile = openFiles[fileDescriptor];

		// 取消文件描述符与该文件的关联
		openFiles[fileDescriptor] = null;

		// 关闭此文件并释放所有相关的系统资源
		openFile.close();

		return 0;
	}

	/**
	 * 从文件系统中删除文件
	 * 如果没有进程打开该文件，则会立即删除该文件，并使其使用的空间可供重用
	 *
	 * 如果任何进程仍然打开该文件，则该文件将一直存在，直到引用它的最后一个文件描述符关闭为止
	 * 但是，在删除该文件之前，creat() 和 open() 将无法返回新的文件描述符
	 *
	 * @param fileAddress 文件地址
	 * @return 成功时为 0，失败时为 -1
	 */
	private int handleUnlink(int fileAddress) {
		// 从该进程的虚拟内存中读取以 null 结尾的字符串
		String fileName = readVirtualMemoryString(fileAddress, MAX_FILENAME_LENGTH);

		// 非空检验
		if (fileName == null || fileName.length() == 0) {
			return -1;
		}

		for (int i = 0; i < openFiles.length; i++) {
			if (openFiles[i] != null && openFiles[i].getName().equals(fileName)) {
				openFiles[i] = null;
				break;
			}
		}

		// 移除文件
		boolean removeSuccess = ThreadedKernel.fileSystem.remove(fileName);

		// 检测移除是否成功
		if (!removeSuccess) {
			return -1;
		}

		return 0;
	}

	/**
	 * function:
	 * 		从openfile中找到一个空的文件描述符位。
	 * @return
	 * 			数组下标,如果没有空的，则返回-1
	 */
	private int findEmpty() {
		for (int i = 0; i < 16; i++) {
			if (openFiles[i] == null)
				return i;
		}
		return -1;
	}
	/* 进程管理系统调用: exit, exec, join */

	/**
	 * 立即终止当前进程
	 * 属于该进程的任何打开的文件描述符都将关闭
	 * 进程的任何子进程都不再具有父进程
	 *
	 * status 作为此进程的退出状态返回给父进程，并且可以使用 join 系统调用收集
	 * 正常退出的进程应（但不要求）将状态设置为 0
	 *
	 * exit() 永不返回
	 *
	 * @param status 退出状态
	 * @return 不返回
	 */
	private int handleExit(int status) {
		// 设置进程运行状态
		this.status = status;

		// 关闭可执行文件
		coff.close();

		// 关闭所有打开的文件
		for (int i = 0; i < openFiles.length; i++) {
			// 如果打开文件非空
			if (openFiles[i] != null) {
				// 关闭该文件
				openFiles[i].close();
			}
		}

		// 释放内存
		unloadSections();

		// 如果这是最后一个进程
		if (processMap.size() == 1) {
			// 终止内核
			Kernel.kernel.terminate();
			return 0;
		}

		// 如果该进程存在父进程
		if (this.parentProcessId != -1) {
			// 获取父进程
			UserProcess parentProcess = processMap.get(this.parentProcessId);

			// 将该进程从父进程的子进程列表中移除（注意：不要直接传数字，否则会被视为索引）
			parentProcess.childrenProcessId.remove(new Integer(this.processId));
		}

		// 遍历该进程的子进程
		for (int childProcessId : childrenProcessId) {
			// 将子进程的父进程设置为无
			processMap.get(childProcessId).parentProcessId = -1;
		}

		// 将该进程从映射表中移除
		processMap.remove(this.processId);

		// 设置正常退出状态
		this.normalExit = true;

		// 获得锁
		joinLock.acquire();

		// 唤醒在这个条件变量上等待的线程（可能是父进程的线程）
		joinCondition.wake();

		// 释放锁
		joinLock.release();

		// 终止线程
		KThread.finish();

		return 0;
	}

	/**
	 * 在新的子进程中使用指定的参数执行存储在指定文件中的程序
	 * 子进程有一个新的唯一进程 ID，以标准输入作为文件描述符 0 打开，标准输出作为文件描述符 1 打开开始
	 *
	 * file 是以 null 结尾的字符串，指定包含可执行文件的文件名
	 * 请注意，此字符串必须包含 .coff 扩展名
	 *
	 * argc 指定要传递给子进程的参数的数量
	 * 此数字必须为非负数
	 *
	 * argv 是指向以 null 结尾的字符串的指针数组，这些字符串表示要传递给子进程的参数
	 * argv[0] 指向第一个参数，argv[argc-1] 指向最后一个参数
	 *
	 * exec() 返回子进程的进程 ID，该 ID 可以传递给 join()
	 * 出现错误时，返回 -1
	 *
	 * @param fileAddress 文件地址
	 * @param argc 要传递给子进程的参数的数量
	 * @param argvAddress 要传递给子进程的参数的地址
	 * @return 子进程的 id，出现错误时为 -1
	 */
	private int handleExec(int fileAddress, int argc, int argvAddress) {
		// 从该进程的虚拟内存中读取以 null 结尾的字符串
		String fileName = readVirtualMemoryString(fileAddress, MAX_FILENAME_LENGTH);

		// 非空检验
		if (fileName == null || fileName.length() == 0) {
			return -1;
		}

		// 读取子进程的参数
		String[] args = new String[argc];
		for (int i = 0; i < argc; i++) {
			byte[] argsAddress = new byte[4];
			// 从虚拟内存中读取参数地址
			if (readVirtualMemory(argvAddress + i * 4, argsAddress) > 0) {
				// 根据读取到的地址找相应的字符串
				args[i] = readVirtualMemoryString(Lib.bytesToInt(argsAddress, 0), 256);
			}
		}

		// 创建子进程
		UserProcess newUserProcess = UserProcess.newUserProcess();

		// 执行子进程
		boolean executeSuccess = newUserProcess.execute(fileName, args);

		// 检测是否执行成功
		if (!executeSuccess) {
			return -1;
		}

		// 将新进程的父进程设置为该进程
		newUserProcess.parentProcessId = this.processId;

		// 将新进程添加到该进程的子进程中
		this.childrenProcessId.add(new Integer(newUserProcess.processId));

		// 返回新进程 id
		return newUserProcess.processId;
	}

	/**
	 * 暂停当前进程的执行，直到 processID 参数指定的子进程退出
	 * 如果在调用时孩子已经退出，则立即返回
	 * 当该进程恢复时，它会断开子进程的连接，因此 join() 不能再次用于该进程
	 *
	 * processID 是子进程的进程 ID，由 exec() 返回
	 *
	 * statusAddress 指向一个整数，子进程的退出状态将存储在该整数中
	 * 这是子进程传递给 exit() 的值
	 * 如果子进程由于未处理的异常而退出，则存储的值为未定义
	 *
	 * 如果子进程正常退出，则返回 1
	 * 如果子进程由于未处理的异常而退出，则返回 0
	 * 如果 processID 未引用当前进程的子进程，则返回 -1
	 *
	 * @param processID 子进程 id
	 * @param statusAddress 子进程退出状态的地址
	 * @return 1（正常退出），0（子进程异常退出），-1（processID 引用错误）
	 */
	private int handleJoin(int processID, int statusAddress) {
		// 获取子进程
		UserProcess childProcess = processMap.get(processID);

		// 只有一个进程的父进程才能 join 到它
		if (!(childProcess.parentProcessId == this.processId)) {
			return -1;
		}

		// 父进程持有子进程的锁
		childProcess.joinLock.acquire();

		// 该进程在该锁的条件变量上等待，直到子进程退出
		childProcess.joinCondition.sleep();

		// 把锁释放掉
		childProcess.joinLock.release();

		// 获取子进程的运行状态
		byte[] childstatus = Lib.bytesFromInt(childProcess.status);

		// 将子进程的状态写入内存中
		int successWrite = writeVirtualMemory(statusAddress, childstatus);

		// 判断子进程是否正常结束
		if (childProcess.normalExit && successWrite == 4) {
			return 1;
		}

		return 0;
	}

    private static final int
        syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
		case syscallHalt:
	    return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:

			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0,a1,a2);
		case syscallWrite:
			return handleWrite(a0,a1,a2);
		case syscallClose:

			return handleClose(a0);
		case syscallUnlink:

			return handleUnlink(a0);


	default:
		System.out.println("syscall"+syscall);
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");
	}
	return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
    
    private int initialPC, initialSP;
    private int argc, argv;
	
    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
	protected final OpenFile[] openFiles;
	private static int max_openfile_num = 16;
	private static final int MAX_FILENAME_LENGTH = 256;
	// 进程运行的状态
	private int status;

	// 进程 id 计数器（需要正整数）
	private static int processIdCounter = 1;

	// 当前进程的 id
	private int processId;

	// 进程 id 与进程的映射表
	private static Map<Integer, UserProcess> processMap = new HashMap<>();

	// 父子进程 id
	private int parentProcessId;
	private LinkedList<Integer> childrenProcessId;

	// join 中需要用到的锁和条件变量
	private Lock joinLock;
	private Condition joinCondition;

	// 是否正常退出
	private boolean normalExit = false;
}
