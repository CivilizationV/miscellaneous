package com.spicdt.party.admin.biz.publish.service;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class RangeQuerySkipList<K> {

    /**
     * The topmost head index of the skiplist.
     */
    private transient volatile HeadIndex<K> head;

    /**
     * The size of the List (the number of elements it contains).
     */
    private int size;

    final Comparator<? super K> comparator;

    private void initialize() {
        head = new HeadIndex<K>(new Node<K>(null, 0, null),
                null, null, 1);
    }

    private boolean updateHead(HeadIndex<K> val) {
        this.head = val;
        return true;
    }

    /* ---------------- Nodes -------------- */

    /**
     * Nodes hold values , and are singly linked in sorted
     * order. The list is
     * headed by a dummy node accessible as head.node.
     */
    static final class Node<K> {
        final K key;
        final double value;
        volatile boolean deleted;
        volatile Node<K> next;

        /**
         * Creates a new regular node.
         */
        Node(K key, double value, Node<K> next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }

        void setDeleted() {
            this.deleted = true;
        }

        void updateNext(Node<K> val) {
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
    static class Index<K> {
        final Node<K> node;
        final Index<K> down;
        volatile Index<K> right;
        private int spanCount;
        private double spanSum;
        private double spanMin;
        private double spanMax;

        /**
         * Creates index node with given values.
         */
        Index(Node<K> node, Index<K> down, Index<K> right) {
            this.node = node;
            this.down = down;
            this.right = right;
        }

        /**
         * Set newSucc as successor.
         *
         * @param succ    the expected current successor
         * @param newSucc the new successor
         */
        final void link(Index<K> succ, Index<K> newSucc) {
            newSucc.right = succ;
            this.right = newSucc;
            int i = 0;
            double sum = 0;
            double min = newSucc.node.value;
            double max = newSucc.node.value;
            Node<K> node = this.node;
            while (node != newSucc.node) {
                node = node.next;
                i++;
                sum = sum + node.value;
                min = Math.min(min, node.value);
                max = Math.max(max, node.value);
            }
            newSucc.spanCount = i;
            newSucc.spanSum = sum;
            newSucc.spanMin = min;
            newSucc.spanMax = max;
            if (succ != null) {
                succ.spanCount = succ.spanCount - i;
                succ.spanSum = succ.spanSum - sum;
                if (succ.spanMin >= min || succ.spanMax <= max) {
                    Node<K> succNode = newSucc.node;
                    double succMin = succ.node.value;
                    double succMax = succ.node.value;
                    while (succNode != succ.node) {
                        succNode = succNode.next;
                        succMin = Math.min(succMin, succNode.value);
                        succMax = Math.max(succMax, succNode.value);
                    }
                    succ.spanMin = succMin;
                    succ.spanMax = succMax;
                }
            }
        }

        /**
         * Set right field to skip over apparent successor
         * succ.
         *
         * @param succ the current successor
         */
        final void unlink(Index<K> succ) {
            this.right = succ.right;
            if (succ.right != null) {
                succ.right.spanCount = succ.right.spanCount + succ.spanCount;
                succ.right.spanSum = succ.right.spanSum + succ.spanSum;
                if (succ.spanMin < succ.right.spanMin) {
                    succ.right.spanMin = succ.spanMin;
                }
                if (succ.spanMax > succ.right.spanMax) {
                    succ.right.spanMax = succ.spanMax;
                }
            }
        }

    }

    /* ---------------- Head nodes -------------- */

    /**
     * Nodes heading each level keep track of their level.
     */
    static final class HeadIndex<K> extends Index<K> {
        final int level;

        HeadIndex(Node<K> node, Index<K> down, Index<K> right, int level) {
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
     * Returns a base-level node with key strictly less than given key,
     * or the base-level header if there is no such node.
     *
     * @param key the key
     * @return a predecessor of key
     */
    private Node<K> findPredecessor(Object key, Comparator<? super K> cmp) {
        if (key == null)
            throw new NullPointerException(); // don't postpone errors
        for (Index<K> q = head, r = q.right, d; ; ) {
            if (r != null) {
                Node<K> n = r.node;
                K k = n.key;
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
     * Determining the rank of an element
     *
     * @param key
     * @return
     */
    public int rank(Object key) {
        if (key == null)
            throw new NullPointerException(); // don't postpone errors
        Comparator<? super K> cmp = comparator;

        Node<K> b;
        int rank = 0;
        for (Index<K> q = head, r = q.right, d; ; ) {
            if (r != null) {
                Node<K> n = r.node;
                K k = n.key;
                if (cpr(cmp, key, k) > 0) {
                    rank = rank + r.spanCount;
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
        for (Node<K> n = b.next; ; ) {
            int c;
            if (n == null)
                break;
            Node<K> f = n.next;
            if ((c = cpr(cmp, key, n.value)) == 0)
                return ++rank;
            if (c < 0)
                break;
            n = f;
            rank++;
        }
        return -1;
    }

    public RangeQueryResult rangeQueryRecursive(K start, K end) {
        if (cpr(this.comparator, start, end) > 0)
            throw new IllegalArgumentException("start > end"); // don't postpone errors
        return rangeQuery(head, start, end);
    }

    private RangeQueryResult rangeQuery(Index<K> index, K start, K end) {
        Comparator<? super K> cmp = comparator;
        for (Index<K> q = index, r = q.right, d; ; ) {
            if (r == null) {
                if ((d = q.down) == null) {
                    return baseRangeQuery(q.node, start, end);
                }
                return rangeQuery(d, start, end);
            } else {
                Node<K> n = r.node;
                K k = n.key;
                // start > k
                if (cpr(cmp, start, k) > 0) {
                    q = r;
                    r = r.right;
                    continue;
                }
                // end < k
                if (cpr(cmp, end, k) < 0) {
                    if ((d = q.down) == null) {
                        return baseRangeQuery(q.node, start, end);
                    }
                    return rangeQuery(d, start, end);
                }
                // start <= k <= end
                RangeQueryResult leftResult;
                if (cpr(cmp, start, k) == 0) {
                    leftResult = new RangeQueryResult(1, n.value, n.value, n.value);
                } else if ((d = q.down) == null) {
                    leftResult = baseRangeQuery(q.node, start, k);
                } else {
                    leftResult = rangeQuery(d, start, k);
                }
                RangeQueryResult result = new RangeQueryResult(0, 0, Double.MAX_VALUE, Double.MIN_VALUE);
                for (Index<K> s = r.right; ; ) {
                    if (s != null && cpr(cmp, s.node.key, end) <= 0) {
                        result.count = result.count + s.spanCount;
                        result.sum = result.sum + s.spanSum;
                        result.min = Math.min(result.min, s.spanMin);
                        result.max = Math.max(result.max, s.spanMax);
                    } else {
                        break;
                    }
                    r = s;
                    s = s.right;
                }
                RangeQueryResult rightResult;
                if (cpr(cmp, r.node.key, end) == 0) {
                    rightResult = new RangeQueryResult(0, 0, Double.MAX_VALUE, Double.MIN_VALUE);
                } else if ((d = r.down) == null) {
                    rightResult = baseRangeQuery(r.node, r.node.key, end);
                } else {
                    rightResult = rangeQuery(d, r.node.key, end);
                }
                result.count = result.count + leftResult.count + rightResult.count;
                result.sum = result.sum + leftResult.sum + rightResult.sum;
                result.min = Math.min(Math.min(result.min, leftResult.min), rightResult.min);
                result.max = Math.max(Math.max(result.max, leftResult.max), rightResult.max);
                return result;
            }
        }
    }

    private RangeQueryResult baseRangeQuery(Node<K> b, K start, K end) {
        RangeQueryResult result = new RangeQueryResult(0, 0, Double.MAX_VALUE, Double.MIN_VALUE);
        Comparator<? super K> cmp = comparator;
        for (Node<K> m = b.next; ; ) {
            if (m == null || cpr(cmp, end, m.key) < 0)
                return result;
            if ((cpr(cmp, start, m.key)) <= 0) {
                result.count = result.count + 1;
                result.sum = result.sum + m.value;
                if (m.value < result.min) {
                    result.min = m.value;
                }
                if (m.value > result.max) {
                    result.max = m.value;
                }
            }
            m = m.next;
        }
    }

    public K selectRecursive(int rank) {
        if (rank <= 0)
            throw new IllegalArgumentException(); // don't postpone errors
        return select(head, rank);
    }

    private K select(Index<K> index, int rank) {
        Index<K> right = index.right;
        if (right != null) {
            int domain = right.spanCount;
            if (domain == rank) {
                return right.node.key;
            } else if (domain < rank) {
                return select(right, rank - domain);
            }
        }
        Index<K> down = index.down;
        if (down != null) {
            return select(down, rank);
        } else {
            Node<K> node = index.node;
            int i = rank;
            while (node != null && i > 0) {
                node = node.next;
                i--;
            }
            return node == null ? null : node.key;
        }
    }

    /**
     * Main insertion method.  Adds element if not present.
     * @param key the key
     * @param value the value
     */
    public void insert(K key, double value) {
        Node<K> z;             // added node
        if (key == null)
            throw new NullPointerException();
        Comparator<? super K> cmp = comparator;
        Node<K> b;
        int rank = 0;
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        for (Index<K> q = head, r = q.right, d; ; ) {
            if (r != null) {
                Node<K> n = r.node;
                K k = n.key;
                if (cpr(cmp, key, k) > 0) {
                    rank = rank + r.spanCount;
                    sum = sum + r.spanSum;
                    min = Math.min(min, r.spanMin);
                    max = Math.max(max, r.spanMax);
                    q = r;
                    r = r.right;
                    continue;
                } else {
                    r.spanCount++;
                    r.spanSum += value;
                    if (value < r.spanMin) {
                        r.spanMin = value;
                    }
                    if (value > r.spanMax) {
                        r.spanMax = value;
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
        for (Node<K> n = b.next; ; ) {
            if (n != null) {
                Node<K> f = n.next;
                if (cpr(cmp, key, n.key) > 0) {
                    rank++;
                    sum = sum + n.value;
                    min = Math.min(min, n.value);
                    max = Math.max(max, n.value);
                    b = n;
                    n = f;
                    continue;
                }
                // else c <= 0; fall through
            }

            z = new Node<>(key, value, n);
            b.updateNext(z);
            size++;
            rank++;
            sum = sum + value;
            min = Math.min(min, value);
            max = Math.max(max, value);
            break;
        }

        int rnd = ThreadLocalRandom.current().nextInt();
//        if ((rnd & 0x80000001) == 0) { // test highest and lowest bits
        if ((rnd & 0x00000001) == 0) { // test lowest bits
            int level = 1, maxLevel;
            while (((rnd >>>= 1) & 1) != 0)
                ++level;
            Index<K> idx = null;
            HeadIndex<K> h = head;
            if (level <= (maxLevel = h.level)) {
                for (int i = 1; i <= level; ++i) {
                    idx = new Index<>(z, idx, null);
                }
            } else { // try to grow by one level
                level = maxLevel + 1; // hold in array and later pick the one to use
                @SuppressWarnings("unchecked") Index<K>[] idxs = (Index<K>[]) new Index<?>[level + 1];
                for (int i = 1; i <= level; ++i)
                    idxs[i] = idx = new Index<>(z, idx, null);
                idxs[level].spanCount = rank;
                idxs[level].spanSum = sum;
                idxs[level].spanMin = min;
                idxs[level].spanMax = max;
                int oldLevel = h.level;
                Node<K> oldbase = h.node;
                HeadIndex<K> newh = new HeadIndex<>(oldbase, h, idxs[level], level); // top level
                updateHead(newh);
                h = newh;
                idx = idxs[level = oldLevel];
            }
            // find insertion points and splice in
            int insertionLevel = level;
            int j = h.level;
            for (Index<K> q = h, r = q.right, t = idx; ; ) {
                if (t == null)
                    break;
                if (r != null) {
                    Node<K> n = r.node;
                    int c = cpr(cmp, key, n.key);
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
     * @param key the value
     * @return true, or false if not found
     */
    public final boolean delete(Object key) {
        if (key == null)
            throw new NullPointerException();
        Comparator<? super K> cmp = comparator;
        for (Node<K> b = findPredecessor(key, cmp), n = b.next; ; ) {
            int c;
            if (n == null)
                break;
            Node<K> f = n.next;
            if ((c = cpr(cmp, key, n.key)) < 0)
                break;
            if (c > 0) {
                b = n;
                n = f;
                continue;
            }
            n.setDeleted();
            b.updateNext(f);
            --size;
            for (Index<K> q = head, r = q.right, d; ; ) {
                if (r != null) {
                    Node<K> m = r.node;
                    K k = m.key;
                    if (m.deleted) {
                        q.unlink(r);
                        r = q.right;         // reread r
                        continue;
                    }
                    if (cpr(cmp, key, k) > 0) {
                        q = r;
                        r = r.right;
                        continue;
                    } else {
                        r.spanCount--;
                        r.spanSum -= n.value;
                        if (r.spanMin == n.value) {
                            Node<K> node = q.node;
                            double min = r.node.value;
                            while (node != r.node) {
                                node = node.next;
                                min = Math.min(min, node.value);
                            }
                            r.spanMin = min;
                        }
                        if (r.spanMax == n.value) {
                            Node<K> node = q.node;
                            double max = r.node.value;
                            while (node != r.node) {
                                node = node.next;
                                max = Math.max(max, node.value);
                            }
                            r.spanMax = max;
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
        HeadIndex<K> h = head;
        HeadIndex<K> d;
        HeadIndex<K> e;
        if (h.level > 3 &&
            (d = (HeadIndex<K>)h.down) != null &&
            (e = (HeadIndex<K>)d.down) != null &&
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

    public RangeQuerySkipList() {
        this.comparator = null;
        initialize();
    }

    public RangeQuerySkipList(Comparator<? super K> comparator) {
        this.comparator = comparator;
        initialize();
    }

    public static class RangeQueryResult {
        private int count;
        private double sum;
        private double min;
        private double max;

        public RangeQueryResult(int count, double sum, double min, double max) {
            this.count = count;
            this.sum = sum;
            this.min = min;
            this.max = max;
        }

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }

        public double getSum() {
            return sum;
        }

        public void setSum(double sum) {
            this.sum = sum;
        }

        public double getMin() {
            return min;
        }

        public void setMin(double min) {
            this.min = min;
        }

        public double getMax() {
            return max;
        }

        public void setMax(double max) {
            this.max = max;
        }
    }

    public static void main(String[] args) {
        RangeQuerySkipList<Integer> list = new RangeQuerySkipList<>();
        list.insert(60, 60.0);
        list.insert(20, 20.0);
        list.insert(10, 10.0);
        list.insert(40, 40.0);
        list.insert(30, 30.0);
        list.insert(50, 50.0);
        list.insert(70, 70.0);
        list.insert(80, 80.0);
        RangeQueryResult res1 = list.rangeQueryRecursive(0, 100);
        RangeQueryResult res2 = list.rangeQueryRecursive(0, 35);
        RangeQueryResult res3 = list.rangeQueryRecursive(0, 30);
        RangeQueryResult res4 = list.rangeQueryRecursive(35, 100);
        RangeQueryResult res5 = list.rangeQueryRecursive(30, 100);
        RangeQueryResult res6 = list.rangeQueryRecursive(15, 35);
        RangeQueryResult res7 = list.rangeQueryRecursive(20, 50);
        RangeQueryResult res8 = list.rangeQueryRecursive(10, 30);
        RangeQueryResult res9 = list.rangeQueryRecursive(10, 60);
        RangeQueryResult res10 = list.rangeQueryRecursive(30, 60);
        RangeQueryResult res11 = list.rangeQueryRecursive(30, 30);
        RangeQueryResult res12 = list.rangeQueryRecursive(0, 0);
        RangeQueryResult res14 = list.rangeQueryRecursive(10, 10);
        RangeQueryResult res13 = list.rangeQueryRecursive(100, 100);
        list.delete(80);
        list.delete(10);
        list.delete(30);
        list.insert(100, 100.0);
        list.insert(90, 90.0);
        RangeQueryResult res21 = list.rangeQueryRecursive(0, 100);
        RangeQueryResult res22 = list.rangeQueryRecursive(0, 35);
        RangeQueryResult res23 = list.rangeQueryRecursive(0, 30);
        RangeQueryResult res24 = list.rangeQueryRecursive(35, 100);
        RangeQueryResult res25 = list.rangeQueryRecursive(30, 100);
        RangeQueryResult res26 = list.rangeQueryRecursive(15, 35);
        RangeQueryResult res27 = list.rangeQueryRecursive(20, 50);
        RangeQueryResult res28 = list.rangeQueryRecursive(10, 30);
        RangeQueryResult res29 = list.rangeQueryRecursive(10, 60);
        RangeQueryResult res30 = list.rangeQueryRecursive(30, 60);
        RangeQueryResult res31 = list.rangeQueryRecursive(30, 30);
        RangeQueryResult res32 = list.rangeQueryRecursive(0, 0);
        RangeQueryResult res34 = list.rangeQueryRecursive(10, 10);
        RangeQueryResult res33 = list.rangeQueryRecursive(100, 100);
    }
}
