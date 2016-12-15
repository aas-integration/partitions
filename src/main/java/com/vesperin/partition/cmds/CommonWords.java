package com.vesperin.partition.cmds;

import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vesperin.partition.BasicCli;
import com.vesperin.partition.spi.Git;
import com.vesperin.partition.utils.Jsons;
import com.vesperin.partition.utils.Projects;
import com.vesperin.text.Project;
import com.vesperin.text.Selection.Word;
import com.vesperin.text.spi.BasicExecutionMonitor;
import com.vesperin.text.spi.ExecutionMonitor;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.util.stream.Collectors.toList;

/**
 * @author Huascar Sanchez
 */
@SuppressWarnings("FieldCanBeLocal") @Command(name = "w", description = "Gathers common words across projects")
public class CommonWords implements BasicCli.CliCommand {
  private final ExecutionMonitor MONITOR = BasicExecutionMonitor.get();

  @Inject
  HelpOption<ProcessProjects> help;

  @Option(name = {"-f", "--from"}, arity = 1, description = "locates the corpus.json input")
  private String from = null;

  @Option(name = {"-d", "--dump"}, description = "dump words onto " + Jsons.STOPS + " file")
  private boolean dump = false;

  @Option(name = {"-t", "--to"}, arity = 1, description = "locates path of output folder")
  private String to = null;

  @Option(name = {"-v", "--verbose"}, description = "Prints logging messages")
  private boolean verbose = false;

  @Option(name = {"-k", "--topk"}, arity = 1, description = "Select top k words from each project. Default is 75.")
  private int topk = 75;

  @Option(name = {"-s", "--scope"}, arity = 1, description = "Search scope: (c)lassname (default), (m)ethodname, method (b)ody")
  private String scope = "c";

  @Override public Integer call() throws Exception {

    if(!help.showHelpIfRequested()){
      if(BasicCli.allNull(1, from)) {
        System.err.println("Unable to locate corpus.json file.");
        return -1;
      }

      if(BasicCli.allNull(1, to)) {
        System.err.println("Unable to locate output folder.");
        return -1;
      }

      if(verbose){ MONITOR.enable(); } else {
        MONITOR.disable();
      }

      final Path corpusJson = Paths.get(from).toAbsolutePath();

      if(!Files.exists(corpusJson)){
        System.err.println(
          String.format("ERROR: Unable to find %s ", corpusJson)
        );

        return -1;
      }

      final Path outDir     = Paths.get(to).toAbsolutePath();

      final List<String> projectNames = Git.processJson(corpusJson, outDir);
      if(projectNames.isEmpty()){

        System.err.println(
          "ERROR: Unable to download github projects in " + corpusJson.toFile().getName()
        );

        return -1;
      }

      try {
        final List<Project> projects = Projects.buildProjectsByFreQ(topk, scope, outDir, projectNames);

        final Map<Word, Word> map = Maps.newHashMap();

        for(Project p : projects){
          for(Word w : p.wordSet()){

            if(!map.containsKey(w)){
              map.put(w, w);
            } else {

              final Word existing = map.get(w);
              w.container().forEach(existing::add);
              existing.count(w.value());

              map.put(existing, existing);
            }
          }
        }

        final List<Word> words = map.keySet().stream()
          .sorted((a, b) -> Ints.compare(b.value(), a.value()))
          .filter(w -> w.value() >= 10)
          .collect(toList());

        final Map<String, List<String>> wordsMap = Maps.newHashMap();
        wordsMap.put("stops", Lists.newArrayList());

        words.forEach(w -> wordsMap.get("stops").add(w.element()));

        final Gson gson     = new GsonBuilder()
          .setPrettyPrinting()
          .create();


        MONITOR.info(String.format("Total words collected is %d ", map.keySet().size()));
        MONITOR.info(String.format("Top %d words selected from %d words", words.size(), map.keySet().size()));


        if(!dump){
          MONITOR.info(gson.toJson(wordsMap));

        } else {

          final Path newFile = Paths.get(outDir.toFile().getAbsolutePath() + "/" + Jsons.STOPS);
          Files.deleteIfExists(newFile);

          Files.write(
            newFile,
            gson.toJson(wordsMap).getBytes(),
            CREATE,
            APPEND
          );

          MONITOR.info(String.format("%s was created.", outDir.toFile().getAbsolutePath() + "/" + Jsons.STOPS));
        }



      } catch (Exception e){
        e.printStackTrace(System.err);
        return -1;
      }



    }

    return 0;
  }
}
