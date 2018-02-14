package com.vesperin.partition.spi;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.vesperin.text.Selection;
import com.vesperin.text.spelling.StopWords;
import com.vesperin.text.tokenizers.WordsTokenizer;
import com.vesperin.text.utils.Strings;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author Huascar Sanchez
 */
public class SignatureTokenizer implements WordsTokenizer {

  private final Set<StopWords> stopWords;
  private final List<Selection.Word> words;

  public SignatureTokenizer(Set<StopWords> stopWords){
    this.stopWords = stopWords;
    this.words     = Lists.newArrayList();
  }

  @Override public void clear() {
    wordsList().clear();
  }

  @Override public List<Selection.Word> wordsList() {
    return this.words;
  }

  @Override public Set<StopWords> stopWords() {
    return stopWords;
  }

  @Override public boolean isLightweightTokenizer() {
    return true;
  }

  @Override public String[] tokenize(String s) {
    if(Objects.isNull(s) || s.isEmpty()) return new String[0];

    String[] output = Strings.wordSplit(Iterables.getFirst(Splitter.on("$").split(s), ""));

    output = Arrays.stream(output).map(SignatureTokenizer::firstLetterCaps).toArray(String[]::new);

    return output;
  }

  private static String firstLetterCaps ( String data ) {
    String firstLetter = data.substring(0,1).toUpperCase();
    String restLetters = data.substring(1).toLowerCase();
    return firstLetter + restLetters;
  }

  @Override public String toString() {
    return this.wordsList().toString();
  }

  public static void main(String[] args) {
    final SignatureTokenizer tokenizer = new SignatureTokenizer(Sets.newHashSet());

    String classname = "Huascar$Best123";
    Iterable<String> o = Splitter.on("$").split(classname);
    classname = Iterables.getFirst(o, "");

    System.out.println(Arrays.toString(tokenizer.tokenize(classname)));

  }
}
