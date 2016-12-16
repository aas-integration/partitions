package com.vesperin.partition.cmds;

import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.google.common.collect.Lists;
import com.vesperin.partition.BasicCli;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
      final WordsTokenizer tokenizer  = Tokenizers.tokenizeString(StopWords.all());

      final Collection<Callable<Corpus<String>>> tasks = Lists.newArrayList();
      final List<File> files = IO.collectFiles(path, "json");

      MONITOR.info(String.format("About to process %d", files.size()));

      files.forEach(c -> tasks.add(() -> buildCorpus(c)));

      final ExecutorService service = Threads.scaleExecutor(files.size());

      try {

        final List<Future<Corpus<String>>> results = service.invokeAll(tasks);
        for (Future<Corpus<String>> each : results){
          global.add(each.get());
        }
      } catch (InterruptedException | ExecutionException e){
        Thread.currentThread().interrupt();
      }

      Threads.shutdownService(service);

      final List<Word> words = Introspector.typicalityQuery(500, global, tokenizer);
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
