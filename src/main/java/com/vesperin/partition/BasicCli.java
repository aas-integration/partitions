package com.vesperin.partition;

import com.github.rvesse.airline.Cli;
import com.github.rvesse.airline.builder.CliBuilder;
import com.github.rvesse.airline.parser.errors.ParseException;
import com.vesperin.partition.cmds.CommonWords;
import com.vesperin.partition.cmds.ProcessProjects;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author Huascar Sanchez
 */
public interface BasicCli {

  static void main(String[] args) {
    BasicCli.executeCli(creates(), args);
  }

  /**
   * @return a new basic cli object.
   */
  static BasicCli creates(){
    return new BasicCliImpl(new Console());
  }

  /**
   * Executes the CLI of an Typicality object and its string arguments.
   *
   * @param introspector Typicality object.
   * @param args the command arguments.
   */
  static void executeCli(BasicCli introspector, String[] args){
    Objects.requireNonNull(introspector);
    Objects.requireNonNull(args);

    final Cli<CliCommand> cueCli  = introspector.buildCli();
    try {
      final CliCommand cmd = cueCli.parse(args);
      introspector.run(cmd);
    } catch (ParseException e) {
      System.err.println("Parser error: " + e.getMessage());
    } catch (Throwable e) {
      System.err.println("Unexpected error: " + e.getMessage());
      e.printStackTrace(System.err);
    }
  }

  /**
   * Checks if all the expected number of arguments are non null.
   *
   * @param args args to test.
   * @return true if all args are non null; false otherwise.
   */
  static boolean allNonNull(int expected, Object... args){
    if(Objects.isNull(args))    return false;
    if(expected < 1)            return false;
    if(expected > args.length)  return false;

    for(int idx = 0; idx < expected; idx++){
      if(Objects.isNull(args[idx])) return false;
    }

    return true;
  }

  /**
   * Checks if all the expected number of arguments are null.
   *
   * @param args args to test.
   * @return true if all args are null; false otherwise.
   */
  static boolean allNull(int expected, Object... args){

    if(Objects.isNull(args))    return true;
    if(expected < 1)            return true;
    if(expected > args.length)  return true;


    for(int idx = 0; idx < expected; idx++){
      if(!Objects.isNull(args[idx])) return false;
    }

    return true;
  }

  /**
   * @return a new CLI runner.
   */
  Runner getCliRunner();

  /**
   * Runs a {@link CliCommand CLI command}.
   *
   * @param command CLI command
   * @return Runner's result.
   */
  default Output run(CliCommand command) {
    return run(command, getCliRunner());
  }

  /**
   * Runs the configured CLI.
   *
   * @param withRunner CLI's runner strategy.
   * @return Runner's result.
   */
  default Output run(CliCommand command, Runner withRunner){
    Objects.requireNonNull(withRunner);
    Objects.requireNonNull(command);
    return withRunner.run(command);
  }

  /**
   * Builds its own 'default' CLI.
   *
   * @return the new Typicality's CLI
   */
  default Cli<CliCommand> buildCli(){
    return buildCli(Cli.<CliCommand>builder("vip")
      .withDescription("Project Partitioning CLI")
      .withCommand(ProcessProjects.class)
      .withCommand(CommonWords.class)
    );
  }

  /**
   * Builds its own CLI.
   *
   * @param builder non-configured CLI builder.
   * @return a configured CLI builder.
   */
  default Cli<CliCommand> buildCli(CliBuilder<CliCommand> builder){
    return Objects.requireNonNull(builder).build();
  }

  /**
   * CLI command.
   */
  interface CliCommand extends Callable<Integer> {
    /**
     * Runs the command and returns an exit code that
     * the application should return.
     *
     * @return exit code
     */
    @Override Integer call() throws Exception;
  }

  /**
   * CLI's Runner
   */
  interface Runner {
    /**
     * Runs the configured CLI.
     *
     * @param command CLI command.
     * @return output
     */
    Output run(CliCommand command);
  }

  /**
   * Runner's result object.
   */
  interface Output {
    @Override String toString();
  }

  class Console implements Runner {
    @Override public Output run(CliCommand command) {
      try {
        final ExecutorService service = Executors.newSingleThreadExecutor();
        final Future<Integer> result  = service.submit(command);
        int exitCode = result.get();
        System.out.println();
        System.out.println("Exiting with Code " + exitCode);
        System.exit(exitCode);
      } catch (Throwable e) {
        System.err.println("Command threw error: " + e.getMessage());
        e.printStackTrace(System.err);
      }

      return null;
    }
  }

  class BasicCliImpl implements BasicCli {
    final Runner runner;

    BasicCliImpl(Runner runner){
      this.runner = runner;
    }

    @Override public Runner getCliRunner() {
      return runner;
    }
  }
}
