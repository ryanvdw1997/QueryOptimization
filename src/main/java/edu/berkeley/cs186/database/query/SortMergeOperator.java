package edu.berkeley.cs186.database.query;

import java.util.*;

import edu.berkeley.cs186.database.TransactionContext;
import edu.berkeley.cs186.database.common.iterator.BacktrackingIterator;
import edu.berkeley.cs186.database.databox.DataBox;
import edu.berkeley.cs186.database.table.Record;

class SortMergeOperator extends JoinOperator {
    SortMergeOperator(QueryOperator leftSource,
                      QueryOperator rightSource,
                      String leftColumnName,
                      String rightColumnName,
                      TransactionContext transaction) {
        super(leftSource, rightSource, leftColumnName, rightColumnName, transaction, JoinType.SORTMERGE);

        this.stats = this.estimateStats();
        this.cost = this.estimateIOCost();
    }

    @Override
    public Iterator<Record> iterator() {
        return new SortMergeIterator();
    }

    @Override
    public int estimateIOCost() {
        //does nothing
        return 0;
    }

    /**
     * An implementation of Iterator that provides an iterator interface for this operator.
     *    See lecture slides.
     *
     * Before proceeding, you should read and understand SNLJOperator.java
     *    You can find it in the same directory as this file.
     *
     * Word of advice: try to decompose the problem into distinguishable sub-problems.
     *    This means you'll probably want to add more methods than those given (Once again,
     *    SNLJOperator.java might be a useful reference).
     *
     */
    private class SortMergeIterator extends JoinIterator {
        /**
         * Some member variables are provided for guidance, but there are many possible solutions.
         * You should implement the solution that's best for you, using any member variables you need.
         * You're free to use these member variables, but you're not obligated to.
         */
        private BacktrackingIterator<Record> leftIterator;
        private BacktrackingIterator<Record> rightIterator;
        private Record leftRecord;
        private Record nextRecord;
        private Record rightRecord;
        private boolean marked;

        private SortMergeIterator() {
            super();
            // TODO(proj3_part1): implement
            // Hint: you may find the helper methods getTransaction() and getRecordIterator(tableName)
            // in JoinOperator useful here.

            SortOperator leftSort = new SortOperator(getTransaction(), this.getLeftTableName(), new LeftRecordComparator());
            String leftTableSorted = leftSort.sort();
            SortOperator rightSort = new SortOperator(getTransaction(), this.getRightTableName(), new RightRecordComparator());
            String rightTableSorted = rightSort.sort();

            this.leftIterator = SortMergeOperator.this.getRecordIterator(leftTableSorted);
            this.rightIterator = SortMergeOperator.this.getRecordIterator(rightTableSorted);

            this.leftRecord = leftIterator.hasNext() ? leftIterator.next() : null;
            this.rightRecord = rightIterator.hasNext() ? rightIterator.next() : null;

            this.nextRecord = null;

            marked = false;
            try {
                fetchNextRecord();
            } catch (NoSuchElementException e) {
                this.nextRecord = null;
            }
        }


        private void fetchNextRecord() {

            if (leftRecord == null) {
                return;
            }
            nextRecord = null;

            do {
                if (!marked) {
                    // while r < s
                    while (leftRecord.getValues().get(SortMergeOperator.this.getRightColumnIndex()).compareTo(
                            rightRecord.getValues().get(SortMergeOperator.this.getRightColumnIndex())) < 0) {
                        leftRecord = leftIterator.hasNext() ? leftIterator.next() : null;
                        if (leftRecord == null) {
                            break;
                        }
                    }
                    // while r > s
                    while (leftRecord.getValues().get(SortMergeOperator.this.getRightColumnIndex()).compareTo(
                            rightRecord.getValues().get(SortMergeOperator.this.getRightColumnIndex())) > 0) {
                        rightRecord = rightIterator.hasNext() ? rightIterator.next() : null;
                        if (rightRecord == null) {
                            break;
                        }
                    }
                    rightIterator.markPrev();
                    marked = true;
                }
                // r == s
                if (rightRecord != null && leftRecord.getValues().get(SortMergeOperator.this.getRightColumnIndex()).compareTo(
                        rightRecord.getValues().get(SortMergeOperator.this.getRightColumnIndex())) == 0) {
                    List<DataBox> leftValues = new ArrayList<>(this.leftRecord.getValues());
                    List<DataBox> rightValues = new ArrayList<>(this.rightRecord.getValues());
                    leftValues.addAll(rightValues);
                    rightRecord = rightIterator.hasNext() ? rightIterator.next() : null;
                    this.nextRecord = new Record(leftValues);
                }
                // rightRecord == null
                else {
                    this.rightIterator.reset();
                    rightRecord = rightIterator.next();
                    this.leftRecord = leftIterator.hasNext() ? leftIterator.next() : null;
                    if (this.leftRecord == null) {
                        break;
                    }
                    marked = false;
                }
            } while(!hasNext());

        }

        /**
         * Checks if there are more record(s) to yield
         *
         * @return true if this iterator has another record to yield, otherwise false
         */
        @Override
        public boolean hasNext() {
            // TODO(proj3_part1): implement
            return this.nextRecord != null;
        }

        /**
         * Yields the next record of this iterator.
         *
         * @return the next Record
         * @throws NoSuchElementException if there are no more Records to yield
         */
        @Override
        public Record next() {
            // TODO(proj3_part1): implement
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            }

            Record nextRecord = this.nextRecord;
            try {
                this.fetchNextRecord();
            } catch (NoSuchElementException e) {
                this.nextRecord = null;
            }
            return nextRecord;

        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        private class LeftRecordComparator implements Comparator<Record> {
            @Override
            public int compare(Record o1, Record o2) {
                return o1.getValues().get(SortMergeOperator.this.getLeftColumnIndex()).compareTo(
                        o2.getValues().get(SortMergeOperator.this.getLeftColumnIndex()));
            }
        }

        private class RightRecordComparator implements Comparator<Record> {
            @Override
            public int compare(Record o1, Record o2) {
                return o1.getValues().get(SortMergeOperator.this.getRightColumnIndex()).compareTo(
                        o2.getValues().get(SortMergeOperator.this.getRightColumnIndex()));
            }
        }
    }
}