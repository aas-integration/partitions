package com.vesperin.partition.cmds;

/**
 * @author Huascar Sanchez
 */

import com.google.common.collect.Lists;

import java.io.File;
import java.util.List;

/**
 * A mkdir command.
 */
public final class Mkdir {
  public void mkdirs(File directory) {
    final List<String> args = Lists.newArrayList("mkdir", "-p", directory.getPath());
    new Command(args).execute();
  }
}
