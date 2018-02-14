package com.vesperin.partition.cmds;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.vesperin.partition.spi.Git;
import com.vesperin.partition.utils.Projects;
import com.vesperin.text.Grouping;
import com.vesperin.text.Index;
import com.vesperin.text.Project;
import com.vesperin.text.Query;
import com.vesperin.text.Selection;
import com.vesperin.text.Selection.Document;
import com.vesperin.text.Selection.Word;
import com.vesperin.text.spelling.StopWords;
import com.vesperin.text.utils.Samples;
import edu.mit.jwi.morph.SimpleStemmer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * @author Huascar Sanchez
 */
public class InterestingClasses  {
  private final static Set<String> DICT = getEnglishDict();

  public static void main(String[] args) {

    final Path corpusJson = Paths.get("./../corpus1.json")
      .toAbsolutePath();

    final Path outDir     = Paths.get("/Users/hsanchez/dev/trashit/cluster")
      .toAbsolutePath();

    final List<String> projectNames = Git.processJson(corpusJson, outDir);
    final List<Project> projects = Projects.buildProjects(500, "c", outDir, projectNames, Projects.Relevance.TYPICALITY);

//    for(Project e : projects){
//      System.out.printing
//    }

    final Set<String> track = Sets.newHashSet(
      "bilinear",
      "normalized",
      "transformer",
      "learning",
      "euclidean",
      "classification",
      "nearest",
      "interpolation",
      "correlation",
      "normalization",
      "variance",
      "classifier",
      "integration",
      "geometric",
      "cosine",
      "kernel",
      "shrink",
      "sauvola",
      "wavelet",
      "gradient",
      "grayscale",
      "gray"
    );

    final Set<Word> universe = new HashSet<>();
    for(Project each : projects){
      universe.addAll(each.wordSet());
    }

    final List<Word> u2 = universe.stream().collect(toList());
    final Word ker = Selection.createWord("kernel");
    ker.container().add("Catalano.Imaging.Tools.Kernel");
    ker.container().add("boofcv.struct.convolve.Kernel1D");
    ker.container().add("boofcv.struct.convolve.Kernel1D_F32");
    u2.add(ker);

    StopWords.isStopWord(StopWords.all(), "kernel");

//    final List<Word> queryDoc = intersectWords(projects, track).stream()
//      .collect(Collectors.toList());
    final List<Word> queryDoc = u2.stream().filter(w -> track.contains(w.element())).collect(toList());

    final Index index = Index.createIndex(u2);

    final Query.Result              result    = Query.documents(queryDoc, index);
    final List<Document>  documents = Query.Result.items(result, Document.class);

    System.out.println();
    System.out.println(documents);

    // cluster all these documents

    Grouping.Groups groups = byInter(documents);

    final List<Grouping.Group> singletons = new ArrayList<>();
    int count = 1; for(Grouping.Group each : groups){
      final List<Document> g = Grouping.Group.items(each, Document.class);
      System.out.println(String.format("%d. %s", count, g));

      if(g.size() == 1){
        singletons.add(each);
      }

      count++;
    }

    System.out.println("Singleton clusters: " + singletons.size());

//    final Map<String, Set<Document>> clusters = strategy3(documents);
//    System.out.println(clusters);
  }

  private static Set<Word> intersectWords(List<Project> projects, Set<String> track){
    final Set<Word> words = new HashSet<>();
    final List<Set<Word>> sorted = projects.stream()
      .sorted((a, b) -> Ints.compare(a.wordSet().size(), b.wordSet().size()))
      .map(Project::wordSet)
      .collect(toList());

    final Set<Word> ws = Samples.getCommonElements(sorted);
    words.addAll(ws);


    return words.stream().filter(w -> track.contains(w.element()))
      .collect(Collectors.toSet());
  }

  private static Map<String, Set<Document>> strategy3(List<Document> documents) {
    final Set<String> dict        = getEnglishDict();
    final Set<String> ignoreWords = Sets.newHashSet("package");

    Map<String, Set<Document>> clusters = new LinkedHashMap<>();

    for (Document sc : documents) {

      if (sc.shortName().contains("$")) {
        // ignore nested classes
        continue;
      }

      final List<String> stemmedWords = splitIntoWords(sc.shortName(), dict);

//      if (sc.resolvingLevel() >= SootClass.HIERARCHY && sc.hasSuperclass()
//        && sc.getSuperclass().isApplicationClass()) {
//        List<String> stemmedParentWords = splitIntoWords(sc.getSuperclass().getJavaStyleName(), dict);
//        int sharedWords = 0;
//
//        for (String s : stemmedParentWords) {
//          if (stemmedWords.contains(s)) {
//            sharedWords++;
//          }
//        }
//
//        if (sharedWords > 0) {
//          stemmedWords.retainAll(stemmedParentWords);
//        }
//      }

      stemmedWords.removeAll(ignoreWords);
      if (!stemmedWords.isEmpty()) {
        final String key = makeKey(stemmedWords);

        if (!clusters.containsKey(key)) {
          clusters.put(key, new HashSet<>());
        }

        clusters.get(key).add(sc);
      }
    }

    System.out.println("Total clusters: " + clusters.size());

    List<String> toRemove = new LinkedList<String>();

    for (Map.Entry<String, Set<Document>> entry : clusters.entrySet()) {
      if (entry.getValue().size() <= 1) {
        toRemove.add(entry.getKey());
      }
    }
    for (String s : toRemove) {
      clusters.remove(s);
    }

    System.out.println("Total clusters >1: " + clusters.size());
    int ttword = 0;
    for (Map.Entry<String, Set<Document>> entry : clusters.entrySet()) {
      ttword += entry.getValue().size();
    }
    System.out.println("Relabeled terms : " + ttword);

    return clusters;
  }

  private static Set<String> getEnglishDict() {
    final File unixDict = new File("/usr/share/dict/words");
    final Set<String> words = new HashSet<>();

    try (BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(unixDict), "UTF8"));) {
      String line;
      while ((line = in.readLine()) != null) {
        words.add(line.replace(System.getProperty("line.separator"), "").toLowerCase());
      }
    } catch (IOException x) {
      System.err.format("IOException: %s%n", x);
    }

    return words;
  }


  private static List<String> splitIntoWords(final String identifierName, Set<String> dict) {
    // split the came case first
    List<String> words = new LinkedList<String>();
    for (String word : identifierName.split("(?<!(^|[A-Z]))(?=[A-Z])|(?<!^)(?=[A-Z][a-z])")) {
      String lowerCaseWord = word.toLowerCase();
      String longestWordFwd = null;
      for (int i = 0; i <= lowerCaseWord.length(); i++) {
        String subStr = lowerCaseWord.substring(0, i);
        if (subStr.length() > 2) {
          if (dict.contains(subStr)) {
            longestWordFwd = subStr;
          }
        }
      }
      if (longestWordFwd == null) {
        longestWordFwd = lowerCaseWord;
      }
      wordNetStuff(longestWordFwd);
      words.add(longestWordFwd);
    }

    return words;
  }

  private static String wordNetStuff(String word) {
    SimpleStemmer ss = new SimpleStemmer();
    String shortStem = word.toLowerCase();
    try {
      for (String s : ss.findStems(word, null)) {
        if (s.length() < shortStem.length()) {
          shortStem = s.toLowerCase();
        }
      }
    } catch (IllegalArgumentException e) {
      System.err.println("Something bad in " + shortStem);
    }
    return shortStem;
  }

  private static String makeKey(List<String> words) {
    List<String> stemmedWords = new LinkedList<>(words);
    Collections.sort(stemmedWords);
    StringBuilder sb = new StringBuilder();
    for (String s : stemmedWords) {
      sb.append(s);
      sb.append(";");
    }
    return sb.toString();
  }

  static Grouping.Groups byInter(List<Document> documents) {
    final int threshold = Math.min(Math.max(Math.max(0, 1), 1), 30);

    final Map<String, Document> map   = Maps.newHashMap();
    final Map<String, Set<String>> index = Maps.newHashMap();
    final Set<String> missed = Sets.newHashSet();

    documents.forEach(p -> map.put(p.shortName(), p));

    for(Document a : documents) {
      Document max = null;

      for(Document b: documents) {
        if(Objects.equals(a, b)) continue;

        if(Objects.isNull(max)){
          max = b;
        } else {

          if(Double.compare(score(a, b), score(a, max)) > 0 ){
            max = b;
          }
        }
      }

      if(Objects.isNull(max)) continue;

      populateIndex(threshold, index, missed, a, max);
    }

    if(!missed.isEmpty()){
      for(String each : missed){
        index.put(each, Sets.newHashSet(each));
      }

    }

    final Set<Grouping.Group> groups = Sets.newHashSet();
    for(String key : index.keySet()){
      final Grouping.Group group = Grouping.newGroup();
      final Document head = map.get(key);
      final Set<Document> tail = index.get(key).stream()
        .map(map::get)
        .collect(Collectors.toSet());

      group.add(head);
      tail.forEach(group::add);

      groups.add(group);
    }


    return Grouping.Groups.of(groups.stream().collect(Collectors.toList()));
  }

  private static void populateIndex(int threshold, Map<String, Set<String>> index,
                             Set<String> missed, Document a, Document max) {

    final Set<String> aa = splitIntoWords(a.shortName(), DICT).stream().collect(Collectors.toSet());
    final Set<String> bb = splitIntoWords(max.shortName(), DICT).stream().collect(Collectors.toSet());

    final Set<String> common = Sets.intersection(bb, aa);

    if(common.size() > threshold){

      if(!index.containsKey(a.shortName())){

        if(index.containsKey(max.shortName())){
          final Set<String> other = index.get(max.shortName());
          if(!other.contains(a.shortName())) {
            index.get(max.shortName()).add(a.shortName());
          }
        } else {
          index.put(a.shortName(), Sets.newHashSet(max.shortName()));
        }
      } else {
        index.get(a.shortName()).add(max.shortName());
      }
    } else {
      if(!common.isEmpty()){
        index.put(a.shortName(), Sets.newHashSet(max.shortName()));
      } else {
        missed.add(a.shortName());
      }
    }
  }

  private static double score(Document a, Document b) {
    final Set<String> aa = splitIntoWords(a.shortName(), DICT).stream().collect(Collectors.toSet());
    final Set<String> bb = splitIntoWords(b.shortName(), DICT).stream().collect(Collectors.toSet());

    return 1.0D * Sets.intersection(aa, bb).size();
  }

}
