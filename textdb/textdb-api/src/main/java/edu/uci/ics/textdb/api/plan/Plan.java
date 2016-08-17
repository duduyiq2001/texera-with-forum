package edu.uci.ics.textdb.api.plan;

import edu.uci.ics.textdb.api.dataflow.ISink;

/**
 * Created by chenli on 5/11/16.
 * <p>
 * A query plan is a tree of operators except the root, which is an ISink object
 * that consumes all the tuples generated by its subtree.
 */
public class Plan {

    private final ISink root;


    public Plan(ISink root) {
        this.root = root;
    }


    public ISink getRoot() {
        return root;
    }
}
