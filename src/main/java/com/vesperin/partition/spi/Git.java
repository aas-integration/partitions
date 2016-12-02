package com.vesperin.partition.spi;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.vesperin.partition.utils.IO;

import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Huascar Sanchez
 */
public class Git {
  private static final ExecutionLog LOGGER = new BasicExecutionLog(System.out);

  private final Command.Builder builder;

  /**
   * Constructs the Git object. Project cloning made simple.
   */
  private Git(){
    this("git");
  }

  private Git(String git){
    this.builder  = Command.of(LOGGER);
    builder.arguments(git);
  }

  /**
   * Downloads (clones) a repo into a given directory (path).
   *
   * @param gitUrl git url
   */
  public static void download(String gitUrl){
    new Git().download(gitUrl, from(gitUrl), null);
  }

  /**
   * Downloads (clones) a repo into a given directory (path).
   *
   * @param gitUrl git url
   * @param to directory where clone will take place.
   */
  public static void download(String gitUrl, Path to){
    new Git().download(gitUrl, from(gitUrl), to);
  }

  /**
   * Locates the corpus file from resources.
   *
   * @param name name of corpus file.
   * @return the file locator
   * @throws URISyntaxException unexpected error has occurred.
   */
  public static Path fromResources(String name) throws URISyntaxException {
    return Paths.get((Git.class.getResource("/" + name).toURI()));
  }

  /**
   * Locates the corpus file from path.
   *
   * @param filepath the path where json file is located.
   * @return the file locator
   */
  public static Path fromPath(String filepath){
    return Paths.get(filepath);
  }

  /**
   * Process corpus.json file and clones each of the repositories
   * contained in this file; using their git-url field.
   *
   * @param json the path to corpus.json file
   * @param to the destination folder
   */
  public static void processJson(Path json, Path to){

    try {
      Gson gson = new Gson();
      final JsonReader reader = new JsonReader(new FileReader(json.toFile()));

      final Map<String, Map<String, Map<String, String>>> records = gson.fromJson(reader, Map.class);

      for(String eachRecord : records.keySet()){
        final Map<String, Map<String, String>> eachRecordEntry = records.get(eachRecord);
        for(String eachRepository : eachRecordEntry.keySet()){
          final Map<String, String> eachRepositoryEntry = eachRecordEntry.get(eachRepository);

          eachRepositoryEntry.keySet().stream()
            .filter("git-url"::equals)
            .forEach(eachEntryKey -> Git.download(eachRepositoryEntry.get(eachEntryKey), to));

        }
      }
    } catch (IOException e){
      log("Unable to read file", e);
    }
  }


  public static Path from(String gitUrl){
    if(Objects.isNull(gitUrl)) throw new IllegalArgumentException("null git url");
    if(gitUrl.isEmpty())       throw new IllegalArgumentException("empty git url");

    final int a = gitUrl.lastIndexOf("/");
    final int b = gitUrl.lastIndexOf(".git");
    final String name = "." + gitUrl.substring(a, b);

    return Paths.get(name);
  }

  public List<String> download(String gitUrl, Path from, Path to){
    final List<String> output = (builder.arguments("clone", gitUrl).execute());

    final boolean isToNull = Objects.isNull(to);
    if(!isToNull){
      if(Files.exists(from)){
        if(!Files.exists(to)){
          final Mkdir mkdir = new Mkdir();
          mkdir.mkdir(to.toFile());
        }

        final Rsync move = new Rsync();
        move.rsync(from.toFile(), to.toFile());
      }

      log(output);

      if(Files.exists(from)) {
        try {
          IO.deleteDirectory(from);
        } catch (IOException e) {
          log("unable to delete file", e);
          return Collections.emptyList();
        }
      }

    } else {
      log(output);
    }

    return output;
  }

  private static void log(String message, Throwable throwable){
    LOGGER.error(message, throwable);
  }

  private static void log(List<String> output){
    output.forEach(LOGGER::info);
  }

  public static void main(String[] args) throws URISyntaxException {
    final Path corpusJson   = Git.fromResources("corpus.json");
    final Path destination  = Git.fromPath("/Users/hsanchez/dev/trashit/fooo");

    Git.processJson(corpusJson, destination);

  }

}
