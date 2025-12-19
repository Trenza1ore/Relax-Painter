import java.time.LocalTime;

/**
 * @author Hugo (Jin Huang)
 * This TaskManager class displays information about the task that is currently being performed  
 * this also allows for sensible error messages to be thrown when an exception happens
 */
public class TaskManager 
{
    public int taskID = 0;
    private int startTime, subTaskID, startTimeSub, taskIDEnd, subTaskIDEnd, 
        startTimeTotal = LocalTime.now().toSecondOfDay();
    private String  timerTemplate = "> Done, took %d second(s)\n\n", 
                    finish = "Reminder:\n" +
                    "Gaussian blurred version of the results are only saved\n" +
                    "if sigma value (a positive integer) was passed in after\n" +
                    "the -g flag in the arguments.\n";
    private String[] msg = {
        " Parsing command line arguments...                      ",
        " Creating sobel magnitude map via pyramid...            ",
        " Creating sobel orientation map via filtered image...   ",
        " Creating multiple versions of the brushes...           ",
        " Calculating multi-scale difference of Gaussian...      ",
        " Rendering strokes onto the canvas...                   ",
        " Filling the unpainted areas with appropriate colours..."
    };


    public TaskManager() 
    {
        taskIDEnd = 7;
        subTaskID = 5;
        subTaskIDEnd = 0;
    }


    /**
     * Customized constructor, task ID starts from zero and is incremented each time a task 
     * is finished, sub task ID is decremented each time a sub task is finished
     * 
     * @param msg list of messages to display
     * @param taskIDEnd end of the taskID range (exclusive)
     * @param subTaskIDStart start of the subtaskID range (inclusive)
     * @param subTaskIDEnd end of the subtaskID range (exclusive)
     * @param finish the message to display after program finishes execution
     */
    public TaskManager(String[] msg, int taskIDEnd, int subTaskIDStart, int subTaskIDEnd, String finish) 
    {
        subTaskID = subTaskIDStart;
        this.msg = msg;
        this.taskIDEnd = taskIDEnd;
        this.subTaskIDEnd = subTaskIDEnd;
        this.finish = finish;
    }


    public void StartTask()
    {
        System.out.println("Task " + taskID + msg[taskID]);
        startTime = LocalTime.now().toSecondOfDay();
    }

    public void FinishTask()
    {
        System.out.printf(timerTemplate, LocalTime.now().toSecondOfDay() - startTime);
        taskID++;
        if (taskID < taskIDEnd) {
            StartTask();
        } else {
            System.out.println(finish);
            System.out.printf("This input image took %d second(s) in total to paint.\n", 
                LocalTime.now().toSecondOfDay() - startTimeTotal);
        }
    }

    public void StartSubTask()
    {
        System.out.printf("  - painting with brush size %d/5 of the original... \n", subTaskID);
        startTimeSub = LocalTime.now().toSecondOfDay();
    }

    public void FinishSubTask()
    {
        System.out.printf("  " + timerTemplate, LocalTime.now().toSecondOfDay() - startTimeSub);
        subTaskID--;
        if (subTaskID > subTaskIDEnd) {
            StartSubTask();
        }
    }
}
