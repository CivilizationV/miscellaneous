package com.spicdt.party.admin.biz.publish.service;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

public class IntervalSkipList {

    /**
     * The topmost head index of the skiplist.
     */
    private transient volatile HeadIndex head;

    /**
     * The size of the List (the number of elements it contains).
     */
    private int size;

    private void initialize() {
        head = new HeadIndex(new Node(null, null),
                                  null, null, 1);
    }

    private void updateHead(HeadIndex val) {
        this.head = val;
    }

    /* ---------------- Interval -------------- */

    public static class Interval implements Comparable<Interval> {
        final Integer low;
        final Integer high;

        /**
         * Creates a new regular interval.
         */
        Interval(Integer low, Integer high) {
            if (low > high) {
                throw new IllegalArgumentException("low must not be greater than high");
            }
            this.low = low;
            this.high = high;
        }

        @Override
        public int compareTo(@NotNull Interval o) {
            int c = Integer.compare(this.low, o.low);
            if (c == 0) {
                return Integer.compare(this.high, o.high);
            } else {
                return c;
            }
        }
    }

    /* ---------------- Nodes -------------- */

    /**
     * Nodes hold keys , and are singly linked in sorted
     * order. The list is headed by a dummy node accessible as head.node.
     */
    static final class Node {
        final Interval value;
        volatile boolean deleted;
        volatile Node next;

        /**
         * Creates a new regular node.
         */
        Node(Interval value, Node next) {
            this.value = value;
            this.next = next;
        }

        void setDeleted() {
            this.deleted = true;
        }

        void updateNext(Node val) {
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
    static class Index {
        final Node node;
        final Index down;
        volatile Index right;
        private int max = Integer.MIN_VALUE;

        /**
         * Creates index node with given values.
         */
        Index(Node node, Index down, Index right) {
            this.node = node;
            this.down = down;
            this.right = right;
        }

        /**
         * Set right field
         */
        final void updateRight(Index val) {
            this.right = val;
        }

        /**
         * Set newSucc as successor.
         * @param succ the expected current successor
         * @param newSucc the new successor
         */
        final void link(Index succ, Index newSucc) {
            newSucc.right = succ;
            updateRight(newSucc);
            updateMax(this, newSucc);
            if (succ != null && newSucc.max >= succ.max) {
                updateMax(newSucc, succ);
            }
        }

        /**
         * Tries to set right field to skip over apparent successor
         * succ.
         * @param succ the current successor
         */
        final void unlink(Index succ) {
            updateRight(succ.right);
            if (succ.right != null && succ.max >= succ.right.max) {
                updateMax(this, succ.right);
            }
        }

        static void updateMax(Index p, Index r) {
            int max = Integer.MIN_VALUE;
            Node node = p.node;
            while (node != r.node) {
                node = node.next;
                if (node.value.high > max) {
                    max = node.value.high;
                }
            }
            r.max = max;
        }

    }

    /* ---------------- Head nodes -------------- */

    /**
     * Nodes heading each level keep track of their level.
     */
    static final class HeadIndex extends Index {
        final int level;
        HeadIndex(Node node, Index down, Index right, int level) {
            super(node, down, right);
            this.level = level;
        }
    }

    /* ---------------- Traversal -------------- */

    /**
     * Returns a base-level node with value strictly less than given value,
     * or the base-level header if there is no such node.
     * @param value the value
     * @return a predecessor of value
     */
    private Node findPredecessor(Interval value) {
        if (value == null)
            throw new NullPointerException(); // don't postpone errors
        for (Index q = head, r = q.right, d; ; ) {
            if (r != null) {
                Node n = r.node;
                Interval k = n.value;
                if (value.compareTo(k) > 0) {
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
     * Main insertion method.  Adds element if not present.
     * @param value the value
     */
    public void intervalInsert(Interval value) {
        Node z;             // added node
        if (value == null)
            throw new NullPointerException();

        Node b;
        int currentMax = value.high;
        for (Index q = head, r = q.right, d; ; ) {
            if (r != null) {
                Node n = r.node;
                Interval k = n.value;
                if (value.compareTo(k) > 0) {
                    if (r.max > currentMax) {
                        currentMax = r.max;
                    }
                    q = r;
                    r = r.right;
                    continue;
                } else {
                    if (r.max < value.high) {
                        r.max = value.high;
                    }
                }
            }
            if ((d = q.down) == null) {
                b = q.node;
                break;
            }
            q = d;
            r = d.right;
        }
        for (Node n = b.next; ; ) {
            if (n != null) {
                Node f = n.next;
                if (value.compareTo(n.value) > 0) {
                    if (n.value.high > currentMax) {
                        currentMax = n.value.high;
                    }
                    b = n;
                    n = f;
                    continue;
                }
                // else c <= 0; fall through
            }

            z = new Node(value, n);
            b.updateNext(z);
            size++;
            break;
        }

        int rnd = ThreadLocalRandom.current().nextInt();
//        if ((rnd & 0x80000001) == 0) { // test highest and lowest bits
        if ((rnd & 0x00000001) == 0) { // test highest and lowest bits
            int level = 1, max;
            while (((rnd >>>= 1) & 1) != 0)
                ++level;
            Index idx = null;
            HeadIndex h = head;
            if (level <= (max = h.level)) {
                for (int i = 1; i <= level; ++i)
                    idx = new Index(z, idx, null);
            } else { // try to grow by one level
                level = max + 1; // hold in array and later pick the one to use
                @SuppressWarnings("unchecked") Index[] idxs = new Index[level + 1];
                for (int i = 1; i <= level; ++i)
                    idxs[i] = idx = new Index(z, idx, null);
                idxs[level].max = currentMax;
                int oldLevel = h.level;
                Node oldbase = h.node;
                HeadIndex newh = new HeadIndex(oldbase, h, idxs[level], level); // top level
                updateHead(newh);
                h = newh;
                idx = idxs[level = oldLevel];
            }
            // find insertion points and splice in
            int insertionLevel = level;
            int j = h.level;
            for (Index q = h, r = q.right, t = idx; ; ) {
                if (t == null)
                    break;
                if (r != null) {
                    Node n = r.node;
                    int c = value.compareTo(n.value);
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
     * returns a pointer to an element x in the interval list
     * such that x overlaps interval value, or null if no such
     * element belongs to the set.
     * @param value the value
     * @return interval that overlaps value, or null if not found
     */
    public Interval intervalSearch(Interval value) {
        if (value == null)
            throw new NullPointerException();
        Node b, e = null;
        for (Index q = head, r = q.right, d; ; ) {
            if (r != null && r.max < value.low) {
                q = r;
                r = r.right;
                continue;
            }
            if ((d = q.down) == null) {
                b = q.node;
                if (r != null) {
                    e = r.node;
                }
                break;
            }
            q = d;
            r = d.right;
        }
        for (Node n = b.next; ; ) {
            if (n == null)
                break;
            if (isOverlap(value, n.value)) {
                return n.value;
            } else if (n == e) {
                break;
            } else {
                n = n.next;
            }
        }
        return null;
    }

    private boolean isOverlap(Interval i, Interval j) {
        return i.low <= j.high && j.low <= i.high;
    }

    /**
     * Main deletion method. Locates node, unlinks predecessor, removes associated index
     * nodes, and possibly reduces head index level.
     *
     * Index nodes are cleared out simply by calling findPredecessor.
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
    public boolean intervalDelete(Interval value) {
        if (value == null)
            throw new NullPointerException();
        for (Node b = findPredecessor(value), n = b.next; ; ) {
            int c;
            if (n == null)
                break;
            Node f = n.next;
            if ((c = value.compareTo(n.value)) < 0)
                break;
            if (c > 0) {
                b = n;
                n = f;
                continue;
            }
            n.setDeleted();
            b.updateNext(f);
            --size;

            for (Index q = head, r = q.right, d; ; ) {
                if (r != null) {
                    Node m = r.node;
                    Interval k = m.value;
                    if (m.deleted) {
                        q.unlink(r);
                        r = q.right;         // reread r
                        continue;
                    }
                    if (value.compareTo(k) > 0) {
                        q = r;
                        r = r.right;
                        continue;
                    } else {
                        if (value.high == r.max) {
                            Index.updateMax(q, r);
                        }
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
        HeadIndex h = head;
        HeadIndex d;
        HeadIndex e;
        if (h.level > 3 &&
            (d = (HeadIndex)h.down) != null &&
            (e = (HeadIndex)d.down) != null &&
            e.right == null &&
            d.right == null &&
            h.right == null)
            updateHead(d);
    }

    public IntervalSkipList() {
        initialize();
    }

}
