package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
    static BoatGrader bg;
    // 条件变量
    private static Lock lock;
    private static Condition2 boatCondition;
    private static Condition2 adultsCondition;
    private static Condition2 startChildrenCondition, endChildrenContidion;

    // 起点人数
    private static int startAdultsCount, startChildrenCount;
    private static int endChildrenCount;
    // 船上人数
    private static int boatAdultsCount, boatChildrenCount;

    // 船是否在起点
    private static boolean boatStart;

    // 是否都到达目的地
    private static boolean success;

    public static void selfTest()
    {
//	BoatGrader b = new BoatGrader();
//
//	System.out.println("\n ***Testing Boats with only 2 children***");
//	begin(0, 2, b);
//        System.out.println("\n<--- 题目 6 开始测试 --->\n");
        begin(1, 2);

//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
//  	begin(1, 2, b);

//  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
//  	begin(3, 3, b);
    }

    public static void begin( int adults, int children)
    {
	// Store the externally generated autograder in a class
	// variable to be accessible by children.
	//bg = b;

	// Instantiate global variables here
	
	// Create threads here. See section 3.4 of the Nachos for Java
	// Walkthrough linked from the projects page.
// 参数校验
        if (adults < 0 || children < 2) {
            System.out.println("数据异常");
            return;
        }

        // 初始化条件变量
        lock = new Lock();
        boatCondition = new Condition2(lock);
        adultsCondition = new Condition2(lock);
        startChildrenCondition = new Condition2(lock);
        endChildrenContidion = new Condition2(lock);

        // 初始化初始人数
        startAdultsCount = adults;
        startChildrenCount = children;

        // 初始化船的位置
        boatStart = true;

        // 创建并运行大人线程
        for (int i = 0; i < adults; i++) {
            new KThread(new Runnable() {
                @Override
                public void run() {
                    AdultItinerary();
                }
            }).fork();
        }

        // 创建并运行小孩线程
        for (int i = 0; i < children; i++) {
            new KThread(new Runnable() {
                @Override
                public void run() {
                    ChildItinerary();
                }
            }).fork();
        }
//	Runnable r = new Runnable() {
//	    public void run() {
//                SampleItinerary();
//            }
//        };
//        KThread t = new KThread(r);
//        t.setName("Sample Boat Thread");
//        t.fork();

    }

    static void AdultItinerary()
    {
	//bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE.
        // 获得锁
        lock.acquire();

        // 如果终点没有小孩，等待
        if (boatStart && endChildrenCount == 0) {
            adultsCondition.sleep();
        }

        // 大人划船去终点（省略上下船过程）
        System.out.println("大人划船去了终点");
        boatAdultsCount --;
        startAdultsCount--;
        boatStart = false;

        // 唤醒一个小孩
        endChildrenContidion.wake();

        // 释放锁
        lock.release();
	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	*/
    }

    static void ChildItinerary()
    {
	//bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE.
        // 只要没全部到达终点，就一直循环
        while (!success) {
            // 获得锁
            lock.acquire();

            // 如果船在起点
            if (boatStart) {
                // 如果船上人满了
                if (boatAdultsCount == 1 || boatChildrenCount == 2) {
                    // 在起点睡眠，等待小孩唤醒
                    startChildrenCondition.sleep();
                } else if (boatChildrenCount == 0) {
                    // 上船
                    startChildrenCount--;
                    boatChildrenCount++;
                    // 唤醒一下在起始点睡眠的小孩
                    System.out.println("小孩：来个人开船");
                    startChildrenCondition.wake();
                    // 在船上睡眠，等别的小孩叫我
                    boatCondition.sleep();
                    // 被叫醒之后，到终点
                    System.out.println("小孩坐船去了终点");
                    boatChildrenCount--;
                    endChildrenCount++;
                    // 唤醒一个在终点的小孩
                    System.out.println("小孩：检查一下人全了吗？不全的话回去接人");
                    endChildrenContidion.wake();
                    // 在终点睡眠，需要接人或全员到终点之后被唤醒
                    endChildrenContidion.sleep();
                } else {
                    boatStart = false;
                    // 叫醒那个在船上睡眠的小孩
                    System.out.println("小孩：我上船了，我带你去终点");
                    boatCondition.wake();
                    // 到达终点
                    System.out.println("小孩划船去了终点");
                    startChildrenCount--;
                    endChildrenCount++;
                    // 在终点睡眠，需要接人或全员到终点之后被唤醒
                    endChildrenContidion.sleep();
                }
            } else {
                // 人员全部转移完毕
                if (startChildrenCount == 0 && startAdultsCount == 0) {
                    success = true;
                    System.out.println("小孩：人全了");
                    System.out.println("\n<--- 题目 6 结束测试 --->\n");
                    // 叫醒所有小孩
                    endChildrenContidion.wakeAll();
                } else if (boatChildrenCount == 0) {
                    // 划船回去（省略上下船过程）
                    System.out.println("小孩：现在起点还有 " + startAdultsCount + " 个大人");
                    System.out.println("小孩：现在起点还有 " + startChildrenCount + " 个小孩");
                    System.out.println("小孩：我得回起点去接他们");
                    System.out.println("小孩划船回到了起点");
                    endChildrenCount--;
                    startChildrenCount++;
                    boatStart = true;
                    // 有大人还在起始点且终点还有小孩
                    if (startAdultsCount != 0 && endChildrenCount != 0) {
                        // 唤醒一个大人
                        System.out.println("小孩：让大人上船");
                        boatAdultsCount++;
                        adultsCondition.wake();
                        // 让大人划船，这个小孩先睡觉
                        startChildrenCondition.sleep();
                    }
                }
            }

            // 释放锁
            lock.release();
        }
    }

    static void SampleItinerary()
    {
	// Please note that this isn't a valid solution (you can't fit
	// all of them on the boat). Please also note that you may not
	// have a single thread calculate a solution and then just play
	// it back at the autograder -- you will be caught.
	System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
	bg.AdultRowToMolokai();
	bg.ChildRideToMolokai();
	bg.AdultRideToMolokai();
	bg.ChildRideToMolokai();
    }
    
}
