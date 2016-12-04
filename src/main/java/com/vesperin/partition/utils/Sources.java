package com.vesperin.partition.utils;

import com.google.common.io.Files;
import com.vesperin.base.Context;
import com.vesperin.base.EclipseJavaParser;
import com.vesperin.base.JavaParser;
import com.vesperin.base.Source;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author Huascar Sanchez
 */
public class Sources {
  private static final JavaParser PARSER = new EclipseJavaParser();
  private static final String PACKAGE_INFO = "package-info";

  private Sources(){
    throw new Error("Cannot be instantiated");
  }

  /**
   * Parses a source code.
   *
   * @param code the source code to parse
   * @return the parsed context of the source code.
   */
  public static Context from(Source code){
    Objects.requireNonNull(code);
    return PARSER.parseJava(code);
  }

  /**
   * Converts a file into a source object.
   *
   * @param file the file to be converted.
   * @return a new source code object.
   */
  public static Source from(File file) {
    try {
      final String name     = Files.getNameWithoutExtension(file.getName());
      final String content  = Files.readLines(file, Charset.defaultCharset()).stream()
        .collect(Collectors.joining("\n"));

      return Source.from(name, content);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Converts a list of files into a list of source objects.
   *
   * @param files the files to be converted
   * @return the list source objects.
   */
  public static List<Source> from(List<File> files) {
    final Predicate<Source> noPackageInfoFiles = s -> !PACKAGE_INFO.equals(s.getName());

    return files.stream()
      .map(Sources::from)
      .filter(noPackageInfoFiles)
      .collect(Collectors.toList());
  }


  private static List<String> normalize(Iterable<String> docs){
    final List<String> n = new ArrayList<>();
    for(String each : docs){
      // normalize line endings
      n.add(each.replaceAll("\r\n", "\n"));
    }

    return n;
  }
}

