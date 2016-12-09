package com.vesperin.partition.spi;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.vesperin.partition.utils.Strings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Huascar Sanchez
 */
public class Command {

  private static final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();

  private ExecutionLog log;
  private List<String> args;

  private final Map<String, String> environment;
  private final File workingDirectory;
  private final boolean permitNonZeroExitStatus;
  private final PrintStream console;


  private volatile Process  process;
  private volatile boolean  destroyed;
  private volatile long     timeoutNanoTime;

  /**
   * Constructs a new Command object for a list of arguments.
   * @param args the list of arguments.
   */
  public Command(List<String> args){
    this(new BasicExecutionLog(System.out), args);
  }

  /**
   * Constructs a new command.
   *
   * @param log the execution log
   * @param args the list of arguments needed by the command
   */
  public Command(ExecutionLog log, List<String> args){
    this.log  = log;
    this.args = new ArrayList<>(args);
    this.environment = Collections.emptyMap();

    this.workingDirectory         = null;
    this.permitNonZeroExitStatus  = false;
    this.console = null;
  }

  /**
   * Constructs a new Command using elements specified in its builder.
   *
   * @param builder the command builder.
   */
  private Command(Builder builder){
    final Builder nonNullBuilder = Preconditions.checkNotNull(builder);

    this.log  = nonNullBuilder.log;
    this.args = new ArrayList<>(nonNullBuilder.args);
    this.environment = nonNullBuilder.env;

    this.workingDirectory         = nonNullBuilder.workingDirectory;
    this.permitNonZeroExitStatus  = nonNullBuilder.permitNonZeroExitStatus;
    this.console = nonNullBuilder.console;

    // checks if we maxed out the number of budgeted arguments
    if (nonNullBuilder.maxCommandLength != -1) {
      final String string = toString();
      if (string.length() > nonNullBuilder.maxCommandLength) {
        throw new IllegalStateException("Maximum command length " + nonNullBuilder.maxCommandLength
          + " exceeded by: " + string);
      }
    }
  }


  /**
   * Creates a Command.Builder object
   *
   * @param log the execution log
   * @return a new command builder object
   */
  public static Builder of(ExecutionLog log){
    return new Builder(log);
  }

  /**
   * starts the command
   *
   * @throws IOException if unable to start command.
   */
  public void start() throws IOException {
    if(isStarted()){
      throw new IllegalStateException("Already started!");
    }

    log.info("starting command");

    final ProcessBuilder processBuilder = new ProcessBuilder()
      .command(args)
      .redirectErrorStream(true);

    if(workingDirectory != null){
      processBuilder.directory(workingDirectory);
    }

    processBuilder.environment().putAll(environment);

    process = processBuilder.start();
  }

  /**
   * @return true if the process has started; false otherwise.
   */
  public boolean isStarted() {
    return process != null;
  }

  /**
   * @return the current input stream used by running process.
   */
  public InputStream getInputStream() {
    if (!isStarted()) {
      throw new IllegalStateException("Not started!");
    }

    return process.getInputStream();
  }

  /**
   * Returns the output returned by process.
   *
   * @return the output on terminal.
   *
   * @throws IOException unexpected behavior occurred.
   * @throws InterruptedException unexpected behavior occurred.
   */
  public List<String> gatherOutput()
    throws IOException, InterruptedException {
    if (!isStarted()) {
      throw new IllegalStateException("Not started!");
    }

    try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(getInputStream(), "UTF-8"))) {
      final List<String> outputLines = new ArrayList<>();

      String outputLine;
      while ((outputLine = bufferedReader.readLine()) != null) {
        if (console != null) {
          console.println(outputLine);
        }

        outputLines.add(outputLine);
      }

      int exitValue = process.waitFor();

      if (exitValue != 0 && !permitNonZeroExitStatus) {
        throw new CommandFailedException(args, outputLines);
      }

      return outputLines;

    }
  }


  /**
   * @return the output displayed on the terminal.
   */
  public List<String> execute() {
    try {
      start();
      return gatherOutput();
    } catch (IOException e) {
      throw new RuntimeException("Failed to execute process: " + args, e);
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while executing process: " + args, e);
    }
  }


  /**
   * Executes a command with a specified timeout. If the process does not
   * complete normally before the timeout has elapsed, it will be destroyed.
   *
   * @param timeoutSeconds how long to wait, or 0 to wait indefinitely
   * @return the command's output, or null if the command timed out
   */
  public List<String> executeWithTimeout(int timeoutSeconds) throws TimeoutException {
    if (timeoutSeconds == 0) {
      return execute();
    }

    scheduleTimeout(timeoutSeconds);
    return execute();
  }

  /**
   * Destroys the underlying process and closes its associated streams.
   */
  private void destroy() {
    Process process = this.process;
    if (process == null) {
      throw new IllegalStateException();
    }
    if (destroyed) {
      return;
    }

    destroyed = true;
    process.destroy();
    try {
      process.waitFor();
      int exitValue = process.exitValue();
      log.info("received exit value " + exitValue + " from destroyed command " + this);
    } catch (IllegalThreadStateException | InterruptedException destroyUnsuccessful) {
      log.warn("couldn't destroy " + this);
    }
  }


  /**
   * Sets the time at which this process will be killed. If a timeout has
   * already been scheduled, it will be rescheduled.
   *
   * @param timeoutSeconds timeout value in seconds
   */
  private void scheduleTimeout(int timeoutSeconds) {
    timeoutNanoTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);

    new TimeoutTask() {
      @Override protected void onTimeout(Process process) {
        // send a quit signal immediately
        log.info("sending quit signal to command " + Command.this);
        sendQuitSignal(process);

        // hard kill in 2 seconds
        timeoutNanoTime = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        new TimeoutTask() {
          @Override protected void onTimeout(Process process) {
            log.info("killing timed out command " + Command.this);
            destroy();
          }
        }.schedule();
      }
    }.schedule();
  }

  private void sendQuitSignal(Process process) {
    final List<String> args = Lists.newArrayList(
      "kill", "-3", Integer.toString(getPid(process))
    );

    new Command(log, args).execute();
  }

  /**
   * Return the PID of this command's process.
   */
  private int getPid(Process process) {
    try {
      // See org.openqa.selenium.ProcessUtils.getProcessId()
      Field field = process.getClass().getDeclaredField("pid");
      field.setAccessible(true);
      return (Integer) field.get(process);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private boolean timedOut() {
    return System.nanoTime() >= timeoutNanoTime;
  }


  @Override public String toString() {
    String envString = !environment.isEmpty() ? (Strings.joinCollection(" ", environment.entrySet()) + " ") : "";
    return envString + Strings.joinCollection(" ", args);
  }


  /**
   * Command builder
   */
  static class Builder {
    private final ExecutionLog        log;
    private final List<String>        args;
    private final Map<String, String> env;

    private File        workingDirectory;
    private boolean     permitNonZeroExitStatus;
    private PrintStream console;
    private int         maxCommandLength;


    /**
     * Creates a command builder.
     *
     * @param log the execution log that monitors builder actions.
     */
    Builder(ExecutionLog log){
      this.log = Preconditions.checkNotNull(log);

      this.workingDirectory         = null;
      this.permitNonZeroExitStatus  = false;

      this.console = null;
      this.maxCommandLength = Integer.MAX_VALUE;

      this.args = new ArrayList<>();
      this.env  = new LinkedHashMap<>();
    }


    /**
     * Sets the command's arguments.
     *
     * @param args the command's arguments.
     * @return self
     */
    public Builder arguments(Object... args){
      return arguments(Arrays.asList(args));
    }

    /**
     * Sets the command's list of arguments.
     *
     * @param args the command's list of arguments.
     * @return self
     */
    public Builder arguments(List<?> args){
      for (Object eachObject : args) {
        this.args.add(eachObject.toString());
      }

      return this;
    }

    /**
     * Sets an environment's variable.
     *
     * @param key key identifying the variable
     * @param value the value of the variable
     * @return self
     */
    public Builder environment(String key, String value){
      env.put(Preconditions.checkNotNull(key), Preconditions.checkNotNull(value));
      return this;
    }

    /**
     * Sets the command's working directory.
     *
     * @param localWorkingDirectory the command's working directory.
     * @return self
     */
    public Builder workingDirectory(File localWorkingDirectory){
      this.workingDirectory = Preconditions.checkNotNull(localWorkingDirectory);
      return this;
    }

    /**
     * Prevents execute() from throwing if the invoked process returns a
     * nonzero exit code.
     *
     * @return self
     */
    public Builder permitNonZeroExitStatus() {
      this.permitNonZeroExitStatus = true;
      return this;
    }

    /**
     * Sets the command's print stream.
     *
     * @param printStream the used print stream; e.g., System.out or System.err.
     * @return self
     */
    public Builder console(PrintStream printStream) {
      console = printStream;
      return this;
    }

    /**
     * Sets the permitted length of a command in its String representation.
     *
     * @param maxLength the length of a command (string representation of command)
     * @return self.
     */
    public Builder maxCommandLength(int maxLength) {
      this.maxCommandLength = maxLength;
      return this;
    }

    /**
     * @return the built command.
     */
    public Command build(){
      return new Command(this);
    }

    /**
     * Shortcut to execute a command
     * @return a list of lines representing
     *    the output of the command.
     */
    public List<String> execute() {
      return build().execute();
    }

    @Override public String toString() {
      final String left  = Objects.isNull(this.args) ? "" : this.args.toString();
      final String right = Objects.isNull(workingDirectory) ? "" : workingDirectory.toString();
      return left + " : " + right;
    }
  }

  /**
   * Command failed to execute exception.
   */
  private static class CommandFailedException extends RuntimeException {

    /**
     * Construct a new CommandFailedException object.
     *
     * @param args list of command's args.
     * @param outputLines list of output lines displayed on terminal.
     */
    public CommandFailedException(List<String> args, List<String> outputLines) {
      super(formatMessage(args, outputLines));
    }

    /**
     * Turns a list of args and output lines into a formatted message.
     *
     * @param args list of command's args.
     * @param outputLines list of output lines displayed on terminal.
     * @return formatted message.
     */
    static String formatMessage(List<String> args, List<String> outputLines) {
      StringBuilder result = new StringBuilder();
      result.append("Command failed:");

      for (String arg : args) {
        result.append(" ").append(arg);
      }

      for (String outputLine : outputLines) {
        result.append("\n  ").append(outputLine);
      }

      return result.toString();
    }
  }

  /**
   * Runs some code when the command times out.
   */
  private abstract class TimeoutTask implements Runnable {
    final void schedule() {
      timer.schedule(this, System.nanoTime() - timeoutNanoTime, TimeUnit.NANOSECONDS);
    }

    protected abstract void onTimeout(Process process);

    @Override public final void run() {
      Process process = Command.this.process;

      // don't do anything if we have already destroyed this command
      if (destroyed) { return; }

      if (timedOut()) {
        onTimeout(process);
      } else {
        // reschedule the kill operation
        timer.schedule(this, System.nanoTime() - timeoutNanoTime, TimeUnit.NANOSECONDS);
      }
    }
  }
}
