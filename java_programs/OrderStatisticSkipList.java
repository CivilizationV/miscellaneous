package com.spicdt.party.admin.biz.publish.service;

import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

public class OrderStatisticSkipList<V> {

    /**
     * The topmost head index of the skiplist.
     */
    private transient volatile HeadIndex<V> head;

    /**
     * The size of the List (the number of elements it contains).
     */
    private int size;

    final Comparator<? super V> comparator;

    private void initialize() {
        head = new HeadIndex<V>(new Node<V>(null, null),
                null, null, 1);
    }

    private boolean updateHead(HeadIndex<V> val) {
        this.head = val;
        return true;
    }

    /* ---------------- Nodes -------------- */

    /**
     * Nodes hold values , and are singly linked in sorted
     * order. The list is
     * headed by a dummy node accessible as head.node.
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
        private int distance;

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
         *
         * @param succ    the expected current successor
         * @param newSucc the new successor
         */
        final void link(Index<V> succ, Index<V> newSucc) {
            newSucc.right = succ;
            updateRight(newSucc);
            int i = 0;
            Node<V> node = this.node;
            while (node != newSucc.node) {
                node = node.next;
                i++;
            }
            newSucc.distance = i;
            if (succ != null) {
                succ.distance = succ.distance - i;
            }
        }

        /**
         * Set right field to skip over apparent successor
         * succ.
         *
         * @param succ the current successor
         */
        final void unlink(Index<V> succ) {
            updateRight(succ.right);
            if (succ.right != null) {
                succ.right.distance = succ.right.distance + succ.distance;
            }
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
        return (c != null) ? c.compare(x, y) : ((Comparable) x).compareTo(y);
    }

    /* ---------------- Traversal -------------- */

    /**
     * Returns a base-level node with value strictly less than given value,
     * or the base-level header if there is no such node.
     *
     * @param value the value
     * @return a predecessor of value
     */
    private Node<V> findPredecessor(Object value, Comparator<? super V> cmp) {
        if (value == null)
            throw new NullPointerException(); // don't postpone errors
        for (Index<V> q = head, r = q.right, d; ; ) {
            if (r != null) {
                Node<V> n = r.node;
                V k = n.value;
                if (cpr(cmp, value, k) > 0) {
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
     * Determining the rank of an element
     *
     * @param value
     * @return
     */
    public int rank(Object value) {
        if (value == null)
            throw new NullPointerException(); // don't postpone errors
        Comparator<? super V> cmp = comparator;

        Node<V> b;
        int rank = 0;
        for (Index<V> q = head, r = q.right, d; ; ) {
            if (r != null) {
                Node<V> n = r.node;
                V k = n.value;
                if (cpr(cmp, value, k) > 0) {
                    rank = rank + r.distance;
                    q = r;
                    r = r.right;
                    continue;
                }
            }
            if ((d = q.down) == null) {
                b = q.node;
                break;
            }
            q = d;
            r = d.right;
        }
        for (Node<V> n = b.next; ; ) {
            int c;
            if (n == null)
                break;
            Node<V> f = n.next;
            if ((c = cpr(cmp, value, n.value)) == 0)
                return ++rank;
            if (c < 0)
                break;
            b = n;
            n = f;
            rank++;
        }
        return -1;
    }

    /**
     * Retrieving the element with a given rank
     *
     * @param rank
     * @return the element with the given rank, or null if the rank is out of upper bound
     */
    public V select(int rank) {
        if (rank <= 0)
            throw new IllegalArgumentException(); // don't postpone errors
        int i = rank;
        for (Index<V> q = head, r = q.right, d; ; ) {
            if (r != null) {
                Node<V> n = r.node;
                V k = n.value;
                int interval = r.distance;
                if (interval < i) {
                    i = i - interval;
                    q = r;
                    r = r.right;
                    continue;
                } else if (interval == i) {
                    return k;
                }
            }
            if ((d = q.down) == null) {
                Node<V> node = q.node;
                while (i > 0 && node != null) {
                    i--;
                    node = node.next;
                }
                return node == null ? null : node.value;
            }
            q = d;
            r = d.right;
        }
    }

    public V selectRecursive(int rank) {
        if (rank <= 0)
            throw new IllegalArgumentException(); // don't postpone errors
        return select(head, rank);
    }

    private V select(Index<V> index, int rank) {
        Index<V> right = index.right;
        if (right != null) {
            int domain = right.distance;
            if (domain == rank) {
                return right.node.value;
            } else if (domain < rank) {
                return select(right, rank - domain);
            }
        }
        Index<V> down = index.down;
        if (down != null) {
            return select(down, rank);
        } else {
            Node<V> node = index.node;
            int i = rank;
            while (node != null && i > 0) {
                node = node.next;
                i--;
            }
            return node == null ? null : node.value;
        }
    }

    /**
     * Main insertion method.  Adds element if not present.
     * @param value the value
     */
    public void insert(V value) {
        Node<V> z;             // added node
        if (value == null)
            throw new NullPointerException();
        Comparator<? super V> cmp = comparator;
        Node<V> b;
        int rank = 0;
        for (Index<V> q = head, r = q.right, d; ; ) {
            if (r != null) {
                Node<V> n = r.node;
                V k = n.value;
                if (cpr(cmp, value, k) > 0) {
                    rank = rank + r.distance;
                    q = r;
                    r = r.right;
                    continue;
                } else {
                    r.distance++;
                }
            }
            if ((d = q.down) == null) {
                b = q.node;
                break;
            }
            q = d;
            r = d.right;
        }
        for (Node<V> n = b.next; ; ) {
            if (n != null) {
                Node<V> f = n.next;
                if (cpr(cmp, value, n.value) > 0) {
                    b = n;
                    n = f;
                    rank++;
                    continue;
                }
                // else c <= 0; fall through
            }

            z = new Node<>(value, n);
            b.updateNext(z);
            size++;
            rank++;
            break;
        }

        int rnd = ThreadLocalRandom.current().nextInt();
        if ((rnd & 0x80000001) == 0) { // test highest and lowest bits
            int level = 1, max;
            while (((rnd >>>= 1) & 1) != 0)
                ++level;
            Index<V> idx = null;
            HeadIndex<V> h = head;
            if (level <= (max = h.level)) {
                for (int i = 1; i <= level; ++i) {
                    idx = new Index<>(z, idx, null);
                }
            } else { // try to grow by one level
                level = max + 1; // hold in array and later pick the one to use
                @SuppressWarnings("unchecked") Index<V>[] idxs =
                        (Index<V>[]) new Index<?>[level + 1];
                for (int i = 1; i <= level; ++i)
                    idxs[i] = idx = new Index<>(z, idx, null);
                idxs[level].distance = rank;
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
                    int c = cpr(cmp, value, n.value);
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
     * Index nodes are cleared out.
     * which unlinks indexes to deleted nodes found along path to value,
     * which will include the indexes to this node.  This is done
     * unconditionally. We can't check beforehand whether there are
     * index nodes because it might be the case that some or all
     * indexes hadn't been inserted yet for this node during initial
     * search for it, and we'd like to ensure lack of garbage
     * retention, so must call to be sure.
     *
     * @param value the value
     * @return true, or false if not found
     */
    public final boolean delete(Object value) {
        if (value == null)
            throw new NullPointerException();
        Comparator<? super V> cmp = comparator;
        for (Node<V> b = findPredecessor(value, cmp), n = b.next; ; ) {
            int c;
            if (n == null)
                break;
            Node<V> f = n.next;
            if ((c = cpr(cmp, value, n.value)) < 0)
                break;
            if (c > 0) {
                b = n;
                n = f;
                continue;
            }
            n.setDeleted();
            b.updateNext(f);
            --size;
            for (Index<V> q = head, r = q.right, d; ; ) {
                if (r != null) {
                    Node<V> m = r.node;
                    V k = m.value;
                    if (m.deleted) {
                        q.unlink(r);
                        r = q.right;         // reread r
                        continue;
                    }
                    if (cpr(cmp, value, k) > 0) {
                        q = r;
                        r = r.right;
                        continue;
                    } else {
                        r.distance--;
                    }
                }
                if ((d = q.down) == null)
                    break;
                q = d;
                r = d.right;
            }
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

    /**
     * Returns the number of elements in this list.
     *
     * @return the number of elements in this list
     */
    public int size() {
        return size;
    }

    public OrderStatisticSkipList() {
        this.comparator = null;
        initialize();
    }

    public OrderStatisticSkipList(Comparator<? super V> comparator) {
        this.comparator = comparator;
        initialize();
    }
}
