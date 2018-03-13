package cse417;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;


/**
 * Program that finds the longest "sideways" trend in price data. The option
 * {@code --max-pct-change} controls how far apart the high and low (closing)
 * prices can be, in percentage terms, during a period for it to be consider a
 * sideways trend. This defaults to 5%.
 */
public class SidewaysTrend {

    /**
     * Format for the dates used in the data files.
     */
    private static final DateFormat DATE_FORMAT =
            new SimpleDateFormat("dd-MMM-yy");

    /**
     * Entry point for a program to build a model of NFL teams.
     */
    public static void main(String[] args) throws Exception {
        ArgParser argParser = new ArgParser("SidewaysTrend");
        argParser.addOption("max-pct-change", Double.class);
        argParser.addOption("naive", Boolean.class);
        args = argParser.parseArgs(args, 1, 1);

        double maxPctChange = argParser.hasOption("max-pct-change") ?
                argParser.getDoubleOption("max-pct-change") : 5.0;

        List<Date> dates = new ArrayList<Date>();
        List<Integer> prices = loadPrices(args[0], dates);

        Range longest;
        if (argParser.hasOption("naive")) {
            longest = findLongestSidewaysTrendNaive(maxPctChange, prices);
        } else {
            longest = findLongestSidewaysTrend(
                    maxPctChange, prices, 0, prices.size() - 1);
        }

        System.out.printf(
                "Longest sideways trend is from %s to %s (%d trading days)\n",
                DATE_FORMAT.format(dates.get(longest.firstIndex)),
                DATE_FORMAT.format(dates.get(longest.lastIndex)),
                longest.length());
        System.out.printf("Price range is %.2f to %.2f, a %.1f%% change\n",
                longest.lowPrice / 100., longest.highPrice / 100.,
                100. * (longest.highPrice - longest.lowPrice) / longest.lowPrice);
    }

    /**
     * Returns the prices in the file. Prices are returned in units of cents
     * ($0.01) to avoid roundoff issues elsewhere in the code.
     *
     * @param fileName Name of the CSV file containing price data
     * @param dates    If non-null, dates will be stored in this list. In this case,
     *                 we will also check that the prices are in order of increasing date.
     */
    private static List<Integer> loadPrices(String fileName, List<Date> dates)
            throws IOException, ParseException {
        assert (dates == null) || (dates.size() == 0);

        // Stores the relevant information from one row of data.
        class Row {
            public final Date date;
            public final int price;

            public Row(Date date, int price) {
                this.date = date;
                this.price = price;
            }
        }
        List<Row> rows = new ArrayList<Row>();

        CsvParser parser = new CsvParser(fileName, true, new Object[]{
                DATE_FORMAT, Float.class, Float.class, Float.class, Float.class,
                String.class, String.class
        });
        while (parser.hasNext()) {
            String[] parts = parser.next();
            double close = Double.parseDouble(parts[1]);
            rows.add(new Row(DATE_FORMAT.parse(parts[0]), (int) (100 * close)));
        }

        // Put the rows in increasing order of date.
        Collections.sort(rows, (r1, r2) -> r1.date.compareTo(r2.date));

        // If requested, otput the dates from the file.
        if (dates != null) {
            for (Row row : rows)
                dates.add(row.date);
        }

        // Return the prices from the file.
        List<Integer> prices = new ArrayList<Integer>();
        for (Row row : rows)
            prices.add(row.price);
        return prices;
    }

    /**
     * Returns the range with the longest sideways trend in the price data.
     */
    private static Range findLongestSidewaysTrendNaive(
            double maxPctChange, List<Integer> prices) {

        Range longest = Range.fromOneIndex(0, prices); // initialize longest Range
        for (int i = 0; i < prices.size(); i++) {
            Range r = Range.fromOneIndex(i, prices);
            for (int j = i + 1; j < prices.size(); j++) {
                r = r.concat(Range.fromOneIndex(j, prices));
                // stop incrementing j once i,j is not a sideways trend
                if (!r.percentChangeAtMost(maxPctChange)) {
                    break;
                } else if (r.length() > longest.length()) {
                    longest = r;
                }
            }
        }

        return longest;
    }

    /**
     * Returns the range with the longest sideways trend in the price data from
     * {@code firstIndex} to {@code lastIndex} (inclusive).
     */
    private static Range findLongestSidewaysTrend(double maxPctChange,
                                                  List<Integer> prices, int firstIndex, int lastIndex) {
        assert firstIndex <= lastIndex;

        // base case
        if (firstIndex == lastIndex) {
            return Range.fromOneIndex(firstIndex, prices);
        } else if (lastIndex - firstIndex == 1) {
            Range first = Range.fromOneIndex(firstIndex, prices);
            Range r = first.concat(Range.fromOneIndex(lastIndex, prices));
            if (r.percentChangeAtMost(maxPctChange)) {
                return r;
            } else {
                return first;
            }
        }

        Range left = findLongestSidewaysTrend(maxPctChange, prices, firstIndex, (firstIndex + lastIndex) / 2);
        Range right = findLongestSidewaysTrend(maxPctChange, prices, (firstIndex + lastIndex) / 2 + 1, lastIndex);
        Range middle = findLongestSidewaysTrendCrossingMidpoint(maxPctChange, prices, firstIndex,
                (firstIndex + lastIndex) / 2, lastIndex);

        // pick the larger range between left and right
        Range maxRange;
        if (left.length() < right.length()) {
            maxRange = right;
        } else {
            maxRange = left;
        }

        // pick the larger range between range-max(left, right) and middle
        if (middle != null && middle.length() > maxRange.length()) {
            maxRange = middle;
        }

        return maxRange;
    }

    /**
     * Returns the range with the longest sideways trend in the price data from
     * {@code firstIndex} to {@code lastIndex} (inclusive) that either starts
     * at or before {@code midIndex} or ends at or after {@code midIndex+1}. (If
     * no such range defines a sideways trend, then it returns null.)
     */
    private static Range findLongestSidewaysTrendCrossingMidpoint(
            double maxPctChange, List<Integer> prices, int firstIndex, int midIndex,
            int lastIndex) {
        List<Range> purple = new ArrayList<>();
        List<Range> gold = new ArrayList<>();

        //create gold ranges
        Range goldRange = Range.fromOneIndex(midIndex, prices);
        gold.add(goldRange); // add [n/2..n/2]
        for (int i = midIndex - 1; i >= firstIndex; i--) {
            goldRange = Range.fromOneIndex(i, prices).concat(goldRange);
            gold.add(goldRange);
        }
        Collections.reverse(gold);

        //crate purple ranges
        Range purpleRange = Range.fromOneIndex(midIndex + 1, prices);
        purple.add(purpleRange); // add [n/2 + 1 .. n/2 + 1]
        for (int i = midIndex + 2; i <= lastIndex; i++) {
            purpleRange = purpleRange.concat(Range.fromOneIndex(i, prices));
            purple.add(purpleRange);
        }

        int rightFinger = 0;
        Range longestRange = null; // return null if no sideways trend found crossing midpoint
        int longestRangeLength = 0;
        for (int leftFinger = 0; leftFinger < gold.size(); leftFinger++) { // left finger
            for (int j = rightFinger; j < purple.size(); j++) { // right finger: it always starts from where it was last at
                Range combined = gold.get(leftFinger).concat(purple.get(j));
                if (!combined.percentChangeAtMost(maxPctChange)) {
                    break;
                } else {
                    rightFinger = j; // after moving leftFinger, j starts from here
                    if (combined.length() > longestRangeLength) {
                        longestRange = combined;
                        longestRangeLength = combined.length();
                    }
                }
            }
        }

        return longestRange;
    }
}


