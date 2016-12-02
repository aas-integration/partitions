package com.vesperin.partition.spi;

import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;

/**
 * @author Huascar Sanchez
 */
public class Rsync {
  public void rsync(File from, File to){
    final List<String> args = Lists.newArrayList("rsync", "-a", from.getPath(), to.getPath());
    new Command(args).execute();
  }
}
