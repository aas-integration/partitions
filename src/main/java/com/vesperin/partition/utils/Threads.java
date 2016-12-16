package com.vesperin.partition.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Huascar Sanchez
 */
public class Threads {
  private Threads(){}

  public static ExecutorService scaleExecutor(int scale){
    final int cpus       = Runtime.getRuntime().availableProcessors();
    scale                = scale > 10 ? 10 : scale;
    final int maxThreads = ((cpus * scale) > 0 ? (cpus * scale) : 1);

    return Executors.newFixedThreadPool(maxThreads);
  }

  public static void shutdownService(ExecutorService service){
    shutdownService(500, service);
  }

  private static void shutdownService(long timeout, ExecutorService service){
    // wait for all of the executor threads to finish
    service.shutdown();

    try {
      if (!service.awaitTermination(timeout, TimeUnit.SECONDS)) {
        // pool didn't terminate after the first try
        service.shutdownNow();
      }

      if (!service.awaitTermination(timeout * 2, TimeUnit.SECONDS)) {
        // pool didn't terminate after the second try
        System.out.println("ERROR: executor service did not terminate after a second try.");
      }
    } catch (InterruptedException ex) {
      service.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }
}
