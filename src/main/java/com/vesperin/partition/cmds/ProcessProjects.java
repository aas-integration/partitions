package com.vesperin.partition.cmds;

import com.github.rvesse.airline.HelpOption;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vesperin.partition.BasicCli;
import com.vesperin.partition.spi.Git;
import com.vesperin.partition.utils.IO;
import com.vesperin.partition.utils.Projects;
import com.vesperin.partition.utils.Threads;
import com.vesperin.text.Grouping;
import com.vesperin.text.Project;
import com.vesperin.text.Selection;
import com.vesperin.text.spi.BasicExecutionMonitor;
import com.vesperin.text.spi.ExecutionMonitor;
import com.vesperin.text.utils.Samples;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static com.vesperin.text.Selection.Word;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * @author Huascar Sanchez
 */
@SuppressWarnings("FieldCanBeLocal") @Command(name = "p", description = "Process a list of projects")
public class ProcessProjects implements BasicCli.CliCommand {

  private static final ExecutionMonitor MONITOR = BasicExecutionMonitor.get();

  @Inject private HelpOption<ProcessProjects> help;

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

  @Option(name = {"-g", "--groupby"}, arity = 1,
    description = "Grouping strategy: (0) WordSet intersection -- default, (1) WordSet similarity, and (2) Kmeans")
  private int grouping = 0;


  @Option(name = {"-k", "--topk"}, arity = 1, description = "Select top k words from each project. Default is 75.")
  private int topk = 75;

  @Option(name = {"-i", "--inc"}, description = "Partitions projects in small increments: [[[1], 2], 3]")
  private boolean inc = false;


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

        final List<Project> projects = Projects.buildProjects(topk, scope, outDir, projectNames);


        final Map<String, Project> index = new HashMap<>();

        for(Project each : projects){
          index.put(each.name(), each);
          MONITOR.info(String.format("%s's typical words: %s", each.name(), each.wordSet()));
        }

        MONITOR.info(String.format("Processed %d projects.", index.size()));

        if(inc){

          MONITOR.info("Ignored "
            + out
            + "; multiple files will be produced instead: "
            + "e.g., step1.json, step2.json."
          );

          final Collection<WriteToFile> tasks = Lists.newArrayList();

          final int n = projects.size() + 1;
          for(int step = 1; step <= n; step++){
            final List<Project> sublist = projects.stream()
              .limit(step)
              .collect(toList());

            final String filename = Objects.isNull(out) ? null : ("step" + step + ".json");
            final String stepStr  = "Step " + step;

            final Grouping.Groups groups = groupBy(grouping, overlap, sublist);
            tasks.add(new WriteToFile(stepStr, filename, projects(groups)));
          }

          final ExecutorService service = Threads.scaleExecutor(projects.size());
          final CompletionService<Void> async = new ExecutorCompletionService<>(service);

          tasks.forEach(async::submit);

          try {

            for (int t = 0; t < tasks.size(); t++) {
              Future<Void> tick;
              while((tick = async.poll()) == null){}

              tick.get();
            }

          } catch (InterruptedException | ExecutionException e){
            MONITOR.error("Unexpected error occurred: ", e);
            Thread.currentThread().interrupt();
          } finally {
            Threads.shutdownService(service);
          }

        } else {
          formClusters(out, projects);
        }


      } catch (Exception e){
        e.printStackTrace(System.err);
        return -1;
      }

    }

    return 0;
  }


  private void formClusters(String out, List<Project> projects) throws IOException {


    final Grouping.Groups groups = groupBy(grouping, overlap, projects);
    final List<List<Project>> pGroups = projects(groups);


    final Clusters clusters = new Clusters("Step 0", pGroups);
    final Gson gson = new GsonBuilder()
      .setPrettyPrinting()
      .create();

    processFile("Step 0", out, clusters, gson);
  }

  private static void processFile(String label, String out, Clusters clusters, Gson gson) throws IOException {
    if (Objects.isNull(out)) {

      MONITOR.info((label + "\n") + gson.toJson(clusters));

    } else {
      final Path newFile = Paths.get(out);
      Files.deleteIfExists(newFile);

      IO.writeFile(newFile, gson.toJson(clusters).getBytes());

      MONITOR.info(String.format("%s: %s was created.", label, out));
    }
  }

  private static List<List<Project>> projects(Grouping.Groups groups){
    final List<List<Project>> pGroups = Lists.newArrayList();
    for(Grouping.Group each : groups){
      final List<Project> pList = Lists.newArrayList();
      for(Object o : each){
        final Project p = (Project) o;
        pList.add(p);
      }

      pGroups.add(pList);

    }

    return pGroups;
  }

  private static Grouping.Groups groupBy(int grouping, int overlap, List<Project> projects){
    switch (grouping){
      case 0: return Grouping.groupProjectsBySetIntersection(overlap, projects);
      case 1: return Grouping.groupProjectsBySetSimilarity(overlap, projects);
      case 2: return Grouping.groupProjectsByKmeans(projects);
      default: throw new NoSuchElementException("Unknown clustering strategy: " + grouping);
    }
  }

  private static class Clusters {
    List<Cluster> clusters;

    Clusters(String label, List<List<Project>> groups){
      clusters = Lists.newArrayList();

      for(List<Project> each : groups){
        final Set<String> words = Sets.newHashSet();

        final List<Set<Word>> sorted = each.stream()
          .sorted((a, b) -> Ints.compare(a.wordSet().size(), b.wordSet().size()))
          .map(Project::wordSet)
          .collect(toList());

        final Set<Selection.Word> ws = Samples.getCommonElements(sorted);

        words.addAll(ws.stream().map(Word::element).collect(toSet()));

        if(!words.isEmpty()){ // only the ones with actual words
          clusters.add(
            new Cluster(
              words,
              each.stream()
                .map(Project::name)
                .collect(toSet())
            )
          );
        }

      }

      extraInfo(label, clusters);
    }

    private static void extraInfo(String label, List<Cluster> clusters) {
      if(Objects.isNull(clusters)) return;
      if(clusters.isEmpty()) return;

      if(MONITOR.isActive()){ // placed on purpose to avoid extra processing

        MONITOR.info(String.format("%s: Produced %d clusters", label, clusters.size()));

        final int avgSize     = (clusters.stream().mapToInt(s -> s.projectSet().size())).sum()/(clusters.size());
        final int singletons  = (clusters.stream().filter(c -> c.projectSet().size() == 1).mapToInt(s -> s.projectSet().size())).sum();

        MONITOR.info(String.format("%s: Cluster average size: %d ", label, avgSize));
        MONITOR.info(String.format("%s: Total number of singleton clusters: %d ", label, singletons));

      }
    }


    List<Cluster> clusterList(){
      return clusters;
    }

    void setClusters(List<Cluster> clusters){
      this.clusters = clusters;
    }

  }

  private static class WriteToFile implements Callable<Void> {

    private final String              label;
    private final String              out;
    private final List<List<Project>> pGroups;

    WriteToFile(String label, String out, List<List<Project>> pGroups){
      this.pGroups  = pGroups;
      this.label    = label;
      this.out      = out;

    }

    @Override public Void call() throws Exception {
      formClusters();
      return null;
    }

    private void formClusters() throws IOException {

      final Clusters clusters = new Clusters(label, pGroups);
      final Gson gson     = new GsonBuilder()
        .setPrettyPrinting()
        .create();

      processFile(label, out, clusters, gson);
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
