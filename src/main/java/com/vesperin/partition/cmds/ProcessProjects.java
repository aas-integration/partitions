package com.vesperin.partition.cmds;

import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vesperin.base.Source;
import com.vesperin.partition.BasicCli;
import com.vesperin.partition.spi.Git;
import com.vesperin.partition.utils.GroupMaker;
import com.vesperin.partition.utils.IO;
import com.vesperin.partition.utils.Sources;
import com.vesperin.partition.utils.WordMaker;
import com.vesperin.text.Corpus;
import com.vesperin.text.Grouping;
import com.vesperin.text.Introspector;
import com.vesperin.text.Project;
import com.vesperin.text.Selection;
import com.vesperin.text.spelling.StopWords;
import com.vesperin.text.spi.BasicExecutionMonitor;
import com.vesperin.text.spi.ExecutionMonitor;
import com.vesperin.text.tokenizers.Tokenizers;
import com.vesperin.text.tokenizers.WordsTokenizer;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.vesperin.text.Selection.Word;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

/**
 * @author Huascar Sanchez
 */
@SuppressWarnings("FieldCanBeLocal") @Command(name = "p", description = "Process a list of projects")
public class ProcessProjects implements BasicCli.CliCommand {

  final ExecutionMonitor MONITOR = BasicExecutionMonitor.get();

  @Inject HelpOption<ProcessProjects> help;

  @Option(name = {"-f", "--from"}, arity = 1, description = "locates the corpus.json input")
  private String from = null;

  @Option(name = {"-o", "--out"}, arity = 1, description = "output json file")
  private String out = null;

  @Option(name = {"-t", "--to"}, arity = 1, description = "locates path of output folder")
  private String to = null;

  @Option(name = {"-s", "--scope"}, arity = 1, description = "Search scope: (c)lassname (default), (m)ethodname, method (b)ody")
  private String scope = "c";

  @Option(name = {"-m", "--min"}, arity = 1, description = "Desired minimum of words shared by projects. Default is 3.")
  private int overlap = 3;


  @Option(name = {"-v", "--verbose"}, description = "Prints logging messages")
  private boolean verbose = false;

  // vip p -f corpus.json -t path/to/folder/ => to file
  // vip p -f corpus.json => screen
  // vip p -f corpus.json -v => screen (verbose mode)
  // vip p -f corpus.json -v -o foo.json => provides an output json file

  @Override public Integer call() throws Exception {
    if(!help.showHelpIfRequested()){

      try {
        if(BasicCli.allNull(1, from)) {
          System.err.println("Unable to locate corpus.json file.");
          return -1;
        }

        if(BasicCli.allNull(1, to)) {
          System.err.println("Unable to locate output folder.");
          return -1;
        }


        if(verbose){ Introspector.enableMonitor(); } else {
          Introspector.disableMonitor();
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

        final List<Map<String, Corpus<Source>>> projectMetadata = Lists.newArrayList();

        for(String name : projectNames){
          final Path            start   = Paths.get(outDir.toFile().getAbsolutePath() + "/" + name);

          final Corpus<Source>  corpus  = Corpus.ofSources();
          corpus.addAll(Sources.from(IO.collectFiles(start, "java", "Test", "test", "package-info" )));

          final Map<String, Corpus<Source>> entry = Collections.singletonMap(name, corpus);
          projectMetadata.add(entry);
        }

//        final WordsTokenizer tokenizer = tokenizer(scope, );
//        if(Objects.isNull(tokenizer)){
//          System.err.println("ERROR: Unable to construct a tokenizer matching the given scope");
//          return -1;
//        }

        final List<Project<Source>> projects = Lists.newArrayList();
        for(Map<String, Corpus<Source>> each : projectMetadata){

          final String          key = Iterables.get(each.keySet(), 0);
          final Corpus<Source>  val = Iterables.get(each.values(), 0);

          final WordsTokenizer tokenizer = tokenizer(scope, key);
          if(Objects.isNull(tokenizer)){
            System.err.println("ERROR: Unable to construct a tokenizer matching the given scope");
            return -1;
          }

          projects.add(Project.createProject(key, val, tokenizer));

        }

        final List<List<Project<Source>>> pGroups = Lists.newArrayList();

        final Map<String, Project<Source>> index = new HashMap<>();
        projects.forEach(p -> index.put(p.name(), p));

        final Grouping.Groups groups = GroupMaker.makeGroups(overlap, projects);
        for(Grouping.Group each : groups){
          final List<Project<Source>> pList = Lists.newArrayList();
          for(Object o : each){
            final Project<Source> p = (Project<Source>) o;
            pList.add(p);
          }

          pGroups.add(pList);
        }


        final Clusters  clusters = new Clusters(pGroups);
        final Gson      gson     = new GsonBuilder()
          .setPrettyPrinting()
          .create();

        if(Objects.isNull(out)){

          MONITOR.info(gson.toJson(clusters));

        } else {

          final Path newFile = Paths.get(out);
          Files.deleteIfExists(newFile);

          Files.write(
            newFile,
            gson.toJson(clusters).getBytes(),
            CREATE,
            APPEND
          );

          MONITOR.info(String.format("%s was created.", out));
        }
      } catch (Exception e){
        e.printStackTrace(System.err);
        return -1;
      }

    }

    return 0;
  }

  private static WordsTokenizer tokenizer(String scope, String name){

    final Set<StopWords> words = WordMaker.generateStopWords(name);

    switch (scope){
      case "c": return Tokenizers.tokenizeTypeDeclarationName(words);
      case "m": return Tokenizers.tokenizeMethodDeclarationName(words);
      case "b": return Tokenizers.tokenizeMethodDeclarationBody(words);
      default: return null;
    }
  }

  private static class Clusters {
    List<Cluster> clusters;

    Clusters(List<List<Project<Source>>> groups){
      clusters = Lists.newArrayList();

      for(List<Project<Source>> each : groups){
        final Set<String> words = Sets.newHashSet();

        final List<Set<Word>> sorted = each.stream().sorted((a, b) -> Ints.compare(a.wordSet().size(), b.wordSet().size())).map(Project::wordSet).collect(Collectors.toList());

        final Set<Selection.Word> ws = GroupMaker.getCommonElements(sorted);
        if(ws.size() > 15){
          System.out.print("");
        }

        words.addAll(ws.stream().map(Word::element).collect(Collectors.toSet()));

        clusters.add(new Cluster(words, each.stream().map(Project::name).collect(Collectors.toSet())));
      }

    }


    List<Cluster> clusterList(){
      return clusters;
    }

    void setClusters(List<Cluster> clusters){
      this.clusters = clusters;
    }

  }

  private static class Cluster {
    Set<String> words;
    Set<String> projects;

    Cluster(Set<String> words, Set<String> projects){
      this.words    = words;
      this.projects = projects;
    }

    Set<String> wordSet(){
      return words;
    }

    Set<String> projectSet(){
      return projects;
    }


    void setWords(Set<String> words){
      this.words = words;
    }

    void setProjects(Set<String> projects){
      this.projects = projects;
    }

  }
}
