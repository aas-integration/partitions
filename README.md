# Partitions

Partitions a list of projects L into N clusters based on their shared words.

The idea is to treat each project in L as a cluster. Then, each project p
is compared against all the projects in L (excluding p). The purpose of this
operation is to identify a project o that shares a number (e.g., > 3) of words
with p. If identified, this project o is added to p.

Notes:

1. Relocation of projects (already placed in a group) to other groups where they
shared more words would occur as needed.

2. Projects that could not be placed in other groups would be considered singleton clusters.


## Running Partitions

To run it (logging enabled -- printed to screen)

```

$ ./vip p -f path/to/corpus.json -t path/to/out-folder -v

```

To run it (logging enabled -- printed to file named projects.json)


```

$ ./vip p -f path/to/corpus.json -t path/to/out-folder -v -o projects.json

```

## Output

Please look at `projects.json` as an example of a json file produced by this project.




