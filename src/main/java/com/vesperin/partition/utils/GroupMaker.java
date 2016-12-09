package com.vesperin.partition.utils;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.vesperin.text.Grouping;
import com.vesperin.text.Project;
import com.vesperin.text.Selection.Word;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Huascar Sanchez
 */
public class GroupMaker {
  private static final int OVERLAP     = 3;
  private static final int MAX_OVERLAP = 30;

  private GroupMaker(){}

  /**
   * Makes a list of groups from a list of projects. Each group contains
   * projects sharing a number of words greater than some overlapping factor.
   *
   * @param overlap overlapping factor (>= 3 and < 10).
   * @param projects list of projects to group.
   * @param <T> type of elements stored in a project.
   * @return a new group of groups.
   */
  public static <T> Grouping.Groups makeGroups(int overlap, List<Project<T>> projects){

    final int threshold = Math.min(Math.max(Math.max(0, overlap), OVERLAP), MAX_OVERLAP);

    final Map<String, Project<T>>  map   = Maps.newHashMap();
    final Map<String, Set<String>> index = Maps.newHashMap();
    final Set<String> missed = Sets.newHashSet();

    projects.forEach(p -> map.put(p.name(), p));

    for(Project<T> a : projects) {
      Project<T> max = null;

      for(Project<T> b: projects) {
        if(Objects.equals(a, b)) continue;

        if(Objects.isNull(max)){
          max = b;
        } else {

          final Set<Word> sharedMax = Sets.intersection(a.wordSet(), max.wordSet());
          final Set<Word> sharedB   = Sets.intersection(a.wordSet(), b.wordSet());

          if(sharedB.size() > sharedMax.size()){
            max = b;
          }
        }
      }

      if(Objects.isNull(max)) continue;


      final Set<Word> common = Sets.intersection(max.wordSet(), a.wordSet());

      if(common.size() > threshold){

        if(!index.containsKey(a.name())){

          if(index.containsKey(max.name())){
            final Set<String> other = index.get(max.name());
            if(!other.contains(a.name())) {
              index.get(max.name()).add(a.name());
            }
          } else {
            index.put(a.name(), Sets.newHashSet(max.name()));
          }
        } else {
          index.get(a.name()).add(max.name());
        }
      } else {
        if(!common.isEmpty()){
          index.put(a.name(), Sets.newHashSet(max.name()));
        } else {
          missed.add(a.name());
        }
      }
    }

    if(!missed.isEmpty()){
      final String missedProject = Iterables.get(missed, 0);
      index.put(missedProject, Sets.newHashSet(missedProject));
    }

    final List<Grouping.Group> groups = Lists.newArrayList();
    for(String key : index.keySet()){
      final Grouping.Group group = Grouping.newGroup();
      final Project<T> head = map.get(key);
      final Set<Project<T>> tail = index.get(key).stream()
        .map(map::get)
        .collect(Collectors.toSet());

      group.add(head);
      tail.forEach(group::add);

      groups.add(group);
    }


    return Grouping.Groups.of(groups);
  }

  public static <T> Set<Set<T>> getUniqueElements(Set<? extends Set<T>> collections) {

    List<Set<T>> allUniqueSets = new ArrayList<>();
    for (Collection<T> collection : collections) {
      Set<T> unique = new LinkedHashSet<>(collection);
      allUniqueSets.add(unique);
      for (Collection<T> otherCollection : collections) {
        if (!Objects.equals(collection, otherCollection)) {
          unique.removeAll(otherCollection);
        }
      }
    }

    return allUniqueSets.stream().collect(Collectors.toSet());
  }


  public static <T> Set<T> getCommonElements(List<? extends Set<T>> sortedList) {

    final Set<T> common = new LinkedHashSet<>();

    if (!sortedList.isEmpty()) {

      for(int idx = 0; idx < sortedList.size(); idx++){
        if(idx == 0) {
          common.addAll(sortedList.get(idx));
        } else {
          common.retainAll(sortedList.get(idx));

          if(common.isEmpty()){
            common.addAll(sortedList.get(idx));
          }
        }
      }
    }

    return common;
  }


}
