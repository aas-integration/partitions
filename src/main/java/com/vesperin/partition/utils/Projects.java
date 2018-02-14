package com.vesperin.partition.utils;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.vesperin.base.Source;
import com.vesperin.text.Corpus;
import com.vesperin.text.Introspector;
import com.vesperin.text.Project;
import com.vesperin.text.Selection;
import com.vesperin.text.spelling.StopWords;
import com.vesperin.text.spi.BasicExecutionMonitor;
import com.vesperin.text.spi.ExecutionMonitor;
import com.vesperin.text.tokenizers.Tokenizers;
import com.vesperin.text.tokenizers.WordsTokenizer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Huascar Sanchez
 */
public class Projects {

  private static final ExecutionMonitor MONITOR = BasicExecutionMonitor.get();

  public enum Relevance { FREQUENCY, TYPICALITY }
  private Projects(){}

  public static List<Project> buildProjects(int topk, String scope, Path outDir, List<String> projectNames){
    return buildProjects(topk, scope, outDir, projectNames, Relevance.TYPICALITY);
  }

  public static List<Project> buildProjectsByFreQ(int topk, String scope, Path outDir, List<String> projectNames){
    return buildProjects(topk, scope, outDir, projectNames, Relevance.FREQUENCY);
  }

  public static List<Project> buildProjects(int topk, String scope, Path outDir, List<String> projectNames, Relevance relevance){

    final Path stops = Paths.get(outDir.toFile().getAbsolutePath() + "/" + Jsons.STOPS);

    final List<Map<String, Corpus<Source>>> projectMetadata = Lists.newArrayList();

    for(String name : projectNames){
      final Path start   = Paths.get(outDir.toFile().getAbsolutePath() + "/" + name);

      final Corpus<Source>  corpus  = Corpus.ofSources();
      corpus.addAll(Sources.from(IO.collectFiles(start, "java", "Test", "test", "package-info" )));

      MONITOR.info(String.format("%d source code files found in %s's corpus", corpus.size(), name));

      final Map<String, Corpus<Source>> entry = Collections.singletonMap(name, corpus);
      projectMetadata.add(entry);
    }

    final List<Project> projects = Lists.newArrayList();
    for(Map<String, Corpus<Source>> each : projectMetadata){

      final String          key = Iterables.get(each.keySet(), 0);
      final Corpus<Source>  val = Iterables.get(each.values(), 0);

      final WordsTokenizer tokenizer = tokenizer(scope, key, stops);
      if(Objects.isNull(tokenizer)){
        throw new NoSuchElementException("Unable to construct a tokenizer matching the given scope");
      }

      List<Selection.Word> rankedWords = collectWords(relevance, val, tokenizer);
      if(rankedWords.size() > topk){
        rankedWords = rankedWords.stream().limit(topk).collect(Collectors.toList());
      }

      MONITOR.info(String.format("Collected %d words from %s repository", rankedWords.size(), key));

      final Set<Selection.Word> typicalOnes = rankedWords.stream()
        .collect(Collectors.toSet());

      projects.add(Project.createProject(key, typicalOnes));
    }

    return projects;
  }

  private static <T> List<Selection.Word> collectWords(Relevance relevance, Corpus<T> corpus, WordsTokenizer tokenizer){
    switch (relevance){
      case FREQUENCY: return Introspector.frequentWords(corpus, tokenizer);
      case TYPICALITY: return Introspector.typicalityRank(corpus, tokenizer);
      default: throw new NoSuchElementException("Unable to understand relevance strategy: " + relevance);
    }
  }

  private static WordsTokenizer tokenizer(String scope, String name, Path stops){

    final Set<StopWords> words = WordMaker.generateStopWords(name, stops);

    switch (scope){
      case "c": return Tokenizers.tokenizeTypeDeclarationName(words);
      case "m": return Tokenizers.tokenizeMethodDeclarationName(words);
      case "b": return Tokenizers.tokenizeMethodDeclarationBody(words);
      default: return null;
    }
  }
}
