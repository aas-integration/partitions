package com.vesperin.partition.cmds;

import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.google.common.collect.Lists;
import com.vesperin.partition.BasicCli;
import com.vesperin.partition.spi.SignatureTokenizer;
import com.vesperin.partition.utils.IO;
import com.vesperin.partition.utils.Threads;
import com.vesperin.text.Corpus;
import com.vesperin.text.Grouping;
import com.vesperin.text.Introspector;
import com.vesperin.text.Selection.Word;
import com.vesperin.text.spelling.StopWords;
import com.vesperin.text.spi.BasicExecutionMonitor;
import com.vesperin.text.spi.ExecutionMonitor;
import com.vesperin.text.tokenizers.Tokenizers;
import com.vesperin.text.tokenizers.WordsTokenizer;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author Huascar Sanchez
 */
@SuppressWarnings("FieldCanBeLocal") @Command(name = "k", description = "Process Kestrel's data of projects")
public class ProcessKestrelData implements BasicCli.CliCommand {

  private static final ExecutionMonitor MONITOR = BasicExecutionMonitor.get();

  @Inject HelpOption<ProcessProjects> help;

  @Option(name = {"-d", "--directory"}, arity = 1, description = "directory containing JSON files to process.")
  private String directory = null;

  @Option(name = {"-v", "--verbose"}, description = "shows dumping-of-files status.")
  private boolean verbose = false;


  @Override public Integer call() throws Exception {
    if(!help.showHelpIfRequested()){

      if(BasicCli.allNull(1, directory)) {
        System.err.println("-d | --directory <path/to/json-files> is missing.");
        return -1;
      }

      final Path filesDirectory = Paths.get(directory).toAbsolutePath();

      if(!Files.exists(filesDirectory)){
        System.err.println(String.format("%s does not exist!", filesDirectory));
        return -1;
      }

      if(verbose){ MONITOR.enable(); } else {
        MONITOR.disable();
      }

      new ProcessJSONFiles(filesDirectory).process();

    }

    return 0;
  }

  private static class ProcessJSONFiles {
    private final Path path;

    ProcessJSONFiles(Path path){
      this.path     = path;
    }

    void process() {

      final Corpus<String> global     = Corpus.ofStrings();
      final WordsTokenizer tokenizer  = new SignatureTokenizer(StopWords.all());

      final Collection<Callable<Corpus<String>>> tasks = Lists.newArrayList();
      final List<File> files = IO.collectFiles(path, "json");

      MONITOR.info(String.format("About to process %d", files.size()));
      final List<List<File>> partitions   = Lists.partition(files, 5);

      int count = 1; for(List<File> each : partitions){
        final List<String>     copyOnWrite  = Lists.newCopyOnWriteArrayList();

        MONITOR.info("PROCESSING Partition#" + (count++));

        each.forEach(c -> tasks.add(() -> buildCorpus(c)));

        final ExecutorService service = Threads.scaleExecutor(files.size());
        final CompletionService<Corpus<String>> async = new ExecutorCompletionService<>(service);

        tasks.forEach(async::submit);

        try {

          boolean before = false;
          for (int t = 0; t < tasks.size(); t++) {
            Future<Corpus<String>> tick;
            while((tick = async.poll()) == null){
              if(!before){
                before = true;
                MONITOR.info("WAITING");
              }

            }

            MONITOR.info("FINISH WAITING");

            copyOnWrite.addAll(tick.get().dataSet());
          }

        } catch (InterruptedException | ExecutionException e){
          MONITOR.error("Unexpected error occurred: ", e);
          Thread.currentThread().interrupt();
        } finally {
          Threads.shutdownService(service);
        }

        tasks.clear();
        MONITOR.info("Clearing tasks for partition#" + (count - 1));

        global.addAll(
          copyOnWrite.stream()
            .filter(e -> !Objects.isNull(e))
            .collect(Collectors.toSet())
        );
      }

      MONITOR.info("ABOUT TO GET FREQUENT WORDS");

      final List<Word> words = Introspector.frequentWords(global, tokenizer);
      final Grouping.Groups groups = Grouping.groupDocsUsingWords(words);

      if(MONITOR.isActive()){

        MONITOR.info(String.format("Produced %d clusters", groups.size()));
        final int avgSize     = (groups.groupList().stream().mapToInt(Grouping.Group::size)).sum()/(groups.size());
        final int singletons  = (groups.groupList().stream().filter(c -> c.size() == 1).mapToInt(Grouping.Group::size)).sum();

        MONITOR.info(String.format("Cluster average size: %d ", avgSize));
        MONITOR.info(String.format("Total number of singleton clusters: %d ", singletons));

      }

    }

    private static Corpus<String> buildCorpus(File file){
      final Corpus<String> local = Corpus.ofStrings();

      try {

        final List<String> lines = Files.readAllLines(file.toPath());
        if(lines.isEmpty()) return local;

        final String line = lines.get(0);

        Pattern logEntry = Pattern.compile("\\{(.*?)\\}");
        Matcher matchPattern = logEntry.matcher(line);

        while(matchPattern.find()) {
          final String content = matchPattern.group(1);
          int idx = content.lastIndexOf(":");
          final String classname = content.substring(idx + 3, content.length() - 2);
          if(classname.isEmpty() || classname.length() < 3)
            continue;

          local.add(content.substring(idx + 3, content.length() - 2));
        }

      } catch (IOException e) {
        MONITOR.error("Unable to read " + file.getName(), e);
      }

      return local;
    }
  }
}
