# Partitions

The goal is to divide N projects up into K clusters (based on their shared typical words) so that the the minimum number of shared words between projects in different clusters is maximized. K is calculated using the following formula: K = Math.floor(Math.sqrt(N)). 

Notes:

1. Partitions will relocate projects (already placed in a cluster) into other clusters as needed.

2. Unclustered projects will remain singleton clusters.


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

Please take a look at the `projects.json` file. This file is an example of
a json file produced by the 'Partitions' project.
