package com.spicdt.party.admin.biz.publish.service;

import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

public class SkipList<V> {

    /**
     * The topmost head index of the skiplist.
     */
    private transient volatile HeadIndex<V> head;

    final Comparator<? super V> comparator;

    private void initialize() {
        head = new HeadIndex<V>(new Node<V>(null, null),
                                  null, null, 1);
    }

    private void updateHead(HeadIndex<V> val) {
        this.head = val;
    }

    /* ---------------- Nodes -------------- */

    /**
     * Nodes hold keys , and are singly linked in sorted
     * order. The list is headed by a dummy node accessible as head.node.
     */
    static final class Node<V> {
        final V value;
        volatile boolean deleted;
        volatile Node<V> next;

        /**
         * Creates a new regular node.
         */
        Node(V value, Node<V> next) {
            this.value = value;
            this.next = next;
        }

        void setDeleted() {
            this.deleted = true;
        }

        void updateNext(Node<V> val) {
            this.next = val;
        }


    }

    /* ---------------- Indexing -------------- */

    /**
     * Index nodes represent the levels of the skip list.  Note that
     * even though both Nodes and Indexes have forward-pointing
     * fields, they have different types and are handled in different
     * ways, that can't nicely be captured by placing field in a
     * shared abstract class.
     */
    static class Index<V> {
        final Node<V> node;
        final Index<V> down;
        volatile Index<V> right;

        /**
         * Creates index node with given values.
         */
        Index(Node<V> node, Index<V> down, Index<V> right) {
            this.node = node;
            this.down = down;
            this.right = right;
        }

        /**
         * Set right field
         */
        final void updateRight(Index<V> val) {
            this.right = val;
        }

        /**
         * Set newSucc as successor.
         * @param succ the expected current successor
         * @param newSucc the new successor
         */
        final void link(Index<V> succ, Index<V> newSucc) {
            newSucc.right = succ;
            updateRight(newSucc);
        }

        /**
         * Tries to set right field to skip over apparent successor
         * succ.
         * @param succ the current successor
         */
        final void unlink(Index<V> succ) {
            updateRight(succ.right);
        }

    }

    /* ---------------- Head nodes -------------- */

    /**
     * Nodes heading each level keep track of their level.
     */
    static final class HeadIndex<V> extends Index<V> {
        final int level;
        HeadIndex(Node<V> node, Index<V> down, Index<V> right, int level) {
            super(node, down, right);
            this.level = level;
        }
    }

    /* ---------------- Comparison utilities -------------- */

    /**
     * Compares using comparator or natural ordering if null.
     * Called only by methods that have performed required type checks.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static final int cpr(Comparator c, Object x, Object y) {
        return (c != null) ? c.compare(x, y) : ((Comparable)x).compareTo(y);
    }

    /* ---------------- Traversal -------------- */

    /**
     * Returns a base-level node with key strictly less than given key,
     * or the base-level header if there is no such node.  Also
     * unlinks indexes to deleted nodes found along the way.  Callers
     * rely on this side-effect of clearing indices to deleted nodes.
     * @param key the key
     * @return a predecessor of key
     */
    private Node<V> findPredecessor(Object key, Comparator<? super V> cmp) {
        if (key == null)
            throw new NullPointerException(); // don't postpone errors
        for (Index<V> q = head, r = q.right, d; ; ) {
            if (r != null) {
                Node<V> n = r.node;
                V k = n.value;
                if (n.deleted) {
                    q.unlink(r);
                    r = q.right;         // reread r
                    continue;
                }
                if (cpr(cmp, key, k) > 0) {
                    q = r;
                    r = r.right;
                    continue;
                }
            }
            if ((d = q.down) == null)
                return q.node;
            q = d;
            r = d.right;
        }
    }

    /**
     * Returns node holding key or null if no such, clearing out any
     * deleted nodes seen along the way.  Repeatedly traverses at
     * base-level looking for key starting at predecessor returned
     * from findPredecessor, processing base-level deletions as
     * encountered. Some callers rely on this side-effect of clearing
     * deleted nodes.
     *
     * Restarts occur, at traversal step centered on node n, if:
     *
     *       n's value field is null, indicating n is deleted, in
     *       which case we help out an ongoing structural deletion
     *       before retrying.  Even though there are cases where such
     *       unlinking doesn't require restart, they aren't sorted out
     *       here because doing so would not usually outweigh cost of
     *       restarting.
     *
     * The traversal loops in doPut, doRemove all
     * include the same three kinds of checks. They can't easily share code because each uses the
     * reads of fields held in locals occurring in the orders they
     * were performed.
     *
     * @param key the key
     * @return node holding key, or null if no such
     */

    private Node<V> findNode(Object key) {
        if (key == null)
            throw new NullPointerException(); // don't postpone errors
        Comparator<? super V> cmp = comparator;
        for (Node<V> b = findPredecessor(key, cmp), n = b.next; ; ) {
            int c;
            if (n == null)
                break;
            Node<V> f = n.next;
            if ((c = cpr(cmp, key, n.value)) == 0)
                return n;
            if (c < 0)
                break;
            b = n;
            n = f;
        }
        return null;
    }


    /**
     * Main insertion method.  Adds element if not present.
     * @param key the key
     */
    public void insert(V key) {
        Node<V> z;             // added node
        if (key == null)
            throw new NullPointerException();
        Comparator<? super V> cmp = comparator;
        for (Node<V> b = findPredecessor(key, cmp), n = b.next; ; ) {
            if (n != null) {
                Node<V> f = n.next;
                if (cpr(cmp, key, n.value) > 0) {
                    b = n;
                    n = f;
                    continue;
                }
                // else c <= 0; fall through
            }

            z = new Node<>(key, n);
            b.updateNext(z);
            break;
        }

        int rnd = ThreadLocalRandom.current().nextInt();
//        if ((rnd & 0x80000001) == 0) { // test highest and lowest bits
        if ((rnd & 0x00000001) == 0) { // test highest and lowest bits
            int level = 1, max;
            while (((rnd >>>= 1) & 1) != 0)
                ++level;
            Index<V> idx = null;
            HeadIndex<V> h = head;
            if (level <= (max = h.level)) {
                for (int i = 1; i <= level; ++i)
                    idx = new Index<>(z, idx, null);
            } else { // try to grow by one level
                level = max + 1; // hold in array and later pick the one to use
                @SuppressWarnings("unchecked") Index<V>[] idxs =
                        (Index<V>[]) new Index<?>[level + 1];
                for (int i = 1; i <= level; ++i)
                    idxs[i] = idx = new Index<>(z, idx, null);
                int oldLevel = h.level;
                Node<V> oldbase = h.node;
                HeadIndex<V> newh = new HeadIndex<>(oldbase, h, idxs[level], level); // top level
                updateHead(newh);
                h = newh;
                idx = idxs[level = oldLevel];
            }
            // find insertion points and splice in
            int insertionLevel = level;
            int j = h.level;
            for (Index<V> q = h, r = q.right, t = idx; ; ) {
                if (t == null)
                    break;
                if (r != null) {
                    Node<V> n = r.node;
                    // compare before deletion check avoids needing recheck
                    int c = cpr(cmp, key, n.value);
                    if (c > 0) {
                        q = r;
                        r = r.right;
                        continue;
                    }
                }

                if (j == insertionLevel) {
                    q.link(r, t);
                    if (--insertionLevel == 0)
                        break;
                }

                if (--j >= insertionLevel && j < level)
                    t = t.down;
                q = q.down;
                r = q.right;
            }
        }
    }

    /**
     * Main deletion method. Locates node, unlinks predecessor, removes associated index
     * nodes, and possibly reduces head index level.
     *
     * Index nodes are cleared out simply by calling findPredecessor.
     * which unlinks indexes to deleted nodes found along path to key,
     * which will include the indexes to this node.  This is done
     * unconditionally. We can't check beforehand whether there are
     * index nodes because it might be the case that some or all
     * indexes hadn't been inserted yet for this node during initial
     * search for it, and we'd like to ensure lack of garbage
     * retention, so must call to be sure.
     *
     * @param key the key
     * @return true, or false if not found
     */
    public final boolean delete(Object key) {
        if (key == null)
            throw new NullPointerException();
        Comparator<? super V> cmp = comparator;
        for (Node<V> b = findPredecessor(key, cmp), n = b.next; ; ) {
            int c;
            if (n == null)
                break;
            Node<V> f = n.next;
            if ((c = cpr(cmp, key, n.value)) < 0)
                break;
            if (c > 0) {
                b = n;
                n = f;
                continue;
            }
            n.setDeleted();
            b.updateNext(f);
            findPredecessor(key, cmp);      // clean index
            if (head.right == null)
                tryReduceLevel();
            return true;
        }
        return false;
    }

    /**
     * Possibly reduce head level if it has no nodes.  This method can
     * (rarely) make mistakes, in which case levels can disappear even
     * though they are about to contain index nodes. This impacts
     * performance, not correctness.  To minimize mistakes as well as
     * to reduce hysteresis, the level is reduced by one only if the
     * topmost three levels look empty.
     *
     * We put up with all this rather than just let levels grow
     * because otherwise, even a small map that has undergone a large
     * number of insertions and removals will have a lot of levels,
     * slowing down access more than would an occasional unwanted
     * reduction.
     */
    private void tryReduceLevel() {
        HeadIndex<V> h = head;
        HeadIndex<V> d;
        HeadIndex<V> e;
        if (h.level > 3 &&
            (d = (HeadIndex<V>)h.down) != null &&
            (e = (HeadIndex<V>)d.down) != null &&
            e.right == null &&
            d.right == null &&
            h.right == null)
            updateHead(d);
    }

    public SkipList() {
        this.comparator = null;
        initialize();
    }

    public SkipList(Comparator<? super V> comparator) {
        this.comparator = comparator;
        initialize();
    }

}
