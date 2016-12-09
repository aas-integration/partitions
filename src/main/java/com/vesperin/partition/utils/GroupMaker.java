package com.vesperin.partition.utils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.vesperin.text.Grouping;
import com.vesperin.text.Project;
import com.vesperin.text.Selection.Word;
import com.vesperin.text.utils.Node;
import com.vesperin.text.utils.Tree;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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


  public static <T> List<Mapping<T>> partition(List<Project<T>> projects){


    final Tree<Mapping<T>>        T = new Tree<>();
    final Deque<Project<T>>       Q = makeQueue(projects);
    final List<Node<Mapping<T>>>  C = new ArrayList<>();

    while(!Q.isEmpty()){
      final Project<T> project = Q.remove();

      if(C.isEmpty() && "Union".equals(project.name())){
        final Node<Mapping<T>> rootMapping = Node.newNode(null);
      }

    }



    return new ArrayList<>();
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

  private static <T> Project<T> unionProject(List<Project<T>> projectList){
    final Set<Word> union = projectList.stream()
      .flatMap(listContainer -> listContainer.wordSet().stream())
      .collect(Collectors.toSet());

    return Project.createProject("Union", union);
  }

  private static <T> Deque<Project<T>> makeQueue(List<Project<T>> rest){
    final Deque<Project<T>> Q = Queues.newArrayDeque(Collections.singletonList(unionProject(rest)));
    Q.addAll(rest);
    return Q;
  }

  public static class Mapping <T> {

    private final Set<Word>       shared;
    private final Set<Project<T>> projects;

    Mapping(Set<Word> shared, Set<Project<T>> projects){
      this.shared   = shared;
      this.projects = projects;
    }

    public Set<Word> wordSet(){
      return shared;
    }

    public Set<Project<T>> projectSet(){
      return projects;
    }

    @Override public int hashCode() {
      return projectSet().hashCode();
    }

    @Override public boolean equals(Object obj) {
      if(!(obj instanceof Mapping)) return false;

      final Mapping<T> other = (Mapping<T>)obj;

      final Set<String> names       = projectSet().stream().map(Project::name).collect(Collectors.toSet());
      final Set<String> otherNames  = other.projectSet().stream().map(Project::name).collect(Collectors.toSet());

      return names.containsAll(otherNames);
    }

    @Override public String toString() {
      final StringBuilder name = new StringBuilder();
      projects.stream()
        .sorted((a, b) -> Ints.compare(b.wordSet().size(), a.wordSet().size()))
        .forEachOrdered(e -> name.append(e.name()).append(";"));
      return name.toString();
    }
  }

  public static void main(String[] args) {
    Set<String> a  = Stream.of("a", "b", "c", "d").collect(Collectors.toSet());
    Set<String> b  = Stream.of("b", "c").collect(Collectors.toSet());
    Set<String> c  = Stream.of("c", "d", "e").collect(Collectors.toSet());
    Set<String> d  = Stream.of("c", "d", "e").collect(Collectors.toSet());
    Set<String> e  = Stream.of("c", "d", "e").collect(Collectors.toSet());
    Set<String> f  = Stream.of("c", "d", "e").collect(Collectors.toSet());

    Set<String> u = Sets.union(Sets.union(a, b), c);

    final Map<String, Set<Item>> index = new HashMap<>();

    Deque<Item> Q = Queues.newArrayDeque();
    Q.add(new Item("[A+B+C]", u));
    Q.add(new Item("A", a));
    Q.add(new Item("B", b));
    Q.add(new Item("C", c));

    Set<Item> P = ImmutableSet.of(new Item("A", a), new Item("B", b), new Item("C", c));

    final Set<Item>         V = Sets.newHashSet();
    final Tree<Item>        T = new Tree<>();
    final List<Node<Item>>  C = Lists.newArrayList();

    while (!Q.isEmpty()){
      final Item w = Q.remove();

      if(C.isEmpty()){

        final Node<Item> r = Node.newNode(w);
        C.add(r);
        T.setRoot(r);

      } else {
        if(!V.contains(w)){

          V.add(w);

          final List<Node<Item>> children = ImmutableList.copyOf(C);

          for(Node<Item> each : children){

            final Set<String> common = Sets.intersection(w.data, each.getData().data);
            final Set<String> diffs  = Sets.difference(each.getData().data, w.data);

            if(!common.isEmpty() && common.size() >= 2){

              final Set<String> X = items(P, common);

              if(!X.isEmpty()){

                final String xName = X.stream().sorted(String::compareTo).collect(Collectors.toSet()).toString();

                final Item xItem = new Item(xName, common);
                final Node<Item> left = Node.newNode(xItem);
                each.addChild(left);
                C.add(left);

                if(index.containsKey(xName)){
                  index.get(xName).add(xItem);
                } else {
                  index.put(xName, Sets.newHashSet(xItem));
                }

              }

            }

            if(!diffs.isEmpty() && diffs.size() >= 2){

              final Set<String> Y = items(P, diffs);

              if(!Y.isEmpty()){

                final String yName = Y.stream().sorted(String::compareTo).collect(Collectors.toSet()).toString();

                final Item yItem = new Item(yName, diffs);

                final Node<Item> right = Node.newNode(yItem);
                each.addChild(right);
                C.add(right);

                if(index.containsKey(yName)){
                  index.get(yName).add(yItem);
                } else {
                  index.put(yName, Sets.newHashSet(yItem));
                }

              }


            }

          }
        }
      }
    }


    System.out.println();
    print(T.getRoot(), "", true);

    System.out.println();
    System.out.println("Candidate groupings");

    for(String each : index.keySet()){
      final Set<Item> unsorted = index.get(each);

      final List<Set<String>> sorted   = unsorted.stream().sorted((n, m) -> Ints.compare(m.data.size(), n.data.size())).map(i -> i.data).collect(Collectors.toList());

      System.out.println(each + " => " + Iterables.get(sorted, 0));

    }

    System.out.println();


//    List<Set<String>> lists = new ArrayList<>();
//    lists.add(a);
//    System.out.println("Common in A: " + getCommonElements(lists));
//    System.out.println("Common in U - A: " + Sets.difference(u, a));
//
//    lists.add(b);
//    System.out.println("Common in A & B: " + getCommonElements(lists));
//    System.out.println("Common in (U - A) & B: " + Sets.intersection(Sets.difference(u, a), b));
//
//    lists.add(c);
//    System.out.println("Common in A & B & C: " + getCommonElements(lists));
//
//    lists.remove(a);
//    System.out.println("Common in B & C: " + getCommonElements(lists));
  }


  private static void print(Node<Item> T, String prefix, boolean isTail) {

    System.out.println(prefix + (isTail ? "└── " : "├── ") + T.getData());

    for (int i = 0; i < T.getChildren().size() - 1; i++) {

      print(T.getChildren().get(i), prefix + (isTail ? "    " : "│   "), false);

    }

    if (T.getChildren().size() > 0) {
        print(T.getChildren().get(T.getChildren().size() - 1), prefix + (isTail ?"    " : "│   "), true);
    }
  }

  static class Item {
    final String      name;
    final Set<String> data;

    Item(String name, Set<String> data){
      this.name = name;
      this.data = data;
    }

    @Override public boolean equals(Object obj) {
      if(!(obj instanceof Item)) return false;

      final Item other = (Item) obj;

      return Objects.equals(data, other.data);
    }

    @Override public int hashCode() {
      return data.hashCode();
    }

    @Override public String toString() {
      return name + " => " + data;
    }
  }

  static Set<String> items(Set<Item> items, Set<String> words){
    final Set<String> result = Sets.newHashSet();
    for( Item each : items){

      final Set<String> eachSet = each.data;

      final Set<String> intersect = Sets.intersection(eachSet, words);
      final Set<String> union     = Sets.union(eachSet, words);

      final double similarity = ((intersect.size() * 1.0D)/(union.size() * 1.0D));

      if(Double.compare(similarity, 0.6) > 0){
        result.add(each.name);
      }

      //final Set<String> shared = Sets.intersection(each.data, words);
      //if(!shared.isEmpty() && shared.size() >= 2){
//      if(each.data.containsAll(words)){
//        result.add(each.name);
//      }
    }

    return result;
  }

}
