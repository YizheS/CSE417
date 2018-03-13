/**
 * Ken Lo
 * CSE 417 Winter 2018
 * HW 8
 */
package cse417;


import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleFunction;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import static cse417.GraphUtils.EPSILON;


/**
 * Program that reads a table of numbers stored in a CSV and writes a new CSV
 * with the entries in the table rounded in such a way that the colum and row
 * sums are equal to the correct sums rounded.
 */
public class TableRounder {

    /**
     * Entry point for a program to round table entries.
     */
    public static void main(String[] args) throws IOException {
        ArgParser argParser = new ArgParser("Table");
        argParser.addOption("header", Boolean.class);
        argParser.addOption("digits", Integer.class);
        argParser.addOption("out-file", String.class);
        args = argParser.parseArgs(args, 1, 1);

        // If the user asks us to round to a digit after the decimal place, we
        // multiply by a power of 10 so that rounding integers after scaling is the
        // same as rounding at the desired decimal place. (We scale back below.)
        int digits = argParser.hasOption("digits") ?
                argParser.getIntegerOption("digits") : 0;
        final double scale = Math.pow(10, digits);

        CsvParser csvParser = new CsvParser(args[0]);
        String[] header = null;
        if (argParser.hasOption("header")) {
            assert csvParser.hasNext();
            header = csvParser.next();
        }

        // Read the table from the CSV.
        List<double[]> table = new ArrayList<double[]>();
        while (csvParser.hasNext()) {
            table.add(Arrays.asList(csvParser.next()).stream()
                    .mapToDouble(s -> scale * Double.parseDouble(s)).toArray());
            if (table.size() > 2) {
                assert table.get(table.size() - 2).length ==
                        table.get(table.size() - 1).length;
            }
        }

        roundTable(table);

        // Output the rounded tables.
        PrintStream output = !argParser.hasOption("out-file") ? System.out :
                new PrintStream(new FileOutputStream(
                        argParser.getStringOption("out-file")));
        if (header != null)
            writeRow(output, header);  // echo the header to the output
        for (double[] vals : table) {
            writeRow(output,
                    DoubleStream.of(vals).map(v -> v / scale).toArray(), digits);
        }
    }

    /**
     * Modifies the given table so that each entry is rounded to an integer.
     */
    static void roundTable(final List<double[]> table) {
        if (table.size() == 0) return;

        List<Integer> nodes = new ArrayList<>();
        int sink = -2;
        int source = -1;
        nodes.add(sink);
        nodes.add(source);

        // add col and row nodes, where columns are the first table.size() numbers
        for (int n = 1; n <= table.size() + table.get(0).length; n++) {
            nodes.add(n);
        }

        ToDoubleBiFunction<Integer, Integer> minEdgeFlow = (a, b) ->
                (a == -1 && b == -2) ? 0.0 :
                        (a == -2 || b == -1) ? 0.0 :
                                (a == -1 && b <= table.size()) ? Math.floor(getColSum(b - 1, table)) :
                                        (a > table.size() && b == -2) ?
                                                Math.floor(getRowSum((a - 1) % table.size(), table)) :
                                                (a != -1 && a <= table.size() && b > table.size()) ?
                                                        Math.floor(table.get((b - 1) % table.size())[a - 1]) : 0.0;


        ToDoubleBiFunction<Integer, Integer> maxEdgeFlow = (a, b) ->
                (a == -1 && b == -2) ? 0.0 :
                        (a == -2 || b == -1) ? 0.0 :
                                (a == -1 && b <= table.size()) ? Math.ceil(getColSum(b - 1, table)) :
                                        (a > table.size() && b == -2) ?
                                                Math.ceil(getRowSum((a - 1) % table.size(), table)) :
                                                (a != -1 && a <= table.size() && b > table.size()) ?
                                                        Math.ceil(table.get((b - 1) % table.size())[a - 1]) : 0.0;

        ToDoubleBiFunction<Integer, Integer> rounded = findFeasibleBoundedFlow(source, sink, nodes, minEdgeFlow,
                maxEdgeFlow);

        List<double[]> tempTable = new ArrayList<>(table); // storage for new table
        for (int r = table.size() + 1; r <= table.size() + table.get(0).length; r++) {
            double[] row = new double[table.get(0).length];
            for (int c = 1; c <= table.size(); c++) {
                row[c - 1] = rounded.applyAsDouble(c, r);
            }
            tempTable.set(r - 1 - table.size(), row);
        }

        // modify original table
        for (int i = 0; i < table.size(); i++) {
            table.set(i, tempTable.get(i));
        }
    }

    /**
     * @param rown  row number to sum
     * @param table table of numbers
     * @return sum
     */
    private static double getRowSum(int rown, List<double[]> table) {
        double sum = 0.0;
        double[] row = table.get(rown);
        for (int i = 0; i < row.length; i++) {
            sum += row[i];
        }
        return sum;
    }

    /**
     * @param coln  row number to sum
     * @param table table of numbers
     * @return sum
     */
    private static double getColSum(int coln, List<double[]> table) {
        double sum = 0.0;
        for (double[] row : table) {
            sum += row[coln];
        }
        return sum;
    }

    /**
     * Returns a flow that satisfies the given constraints or null if none
     * exists.
     */
    static ToDoubleBiFunction<Integer, Integer> findFeasibleBoundedFlow(
            final Integer source, final Integer sink, Collection<Integer> nodes,
            ToDoubleBiFunction<Integer, Integer> minEdgeFlow,
            ToDoubleBiFunction<Integer, Integer> maxEdgeFlow) {
        ToDoubleBiFunction<Integer, Integer> capacity = (a, b) -> (a == sink && b == source) ?
                Double.POSITIVE_INFINITY : maxEdgeFlow.applyAsDouble(a, b) - minEdgeFlow.applyAsDouble(a, b);
        ToDoubleFunction<Integer> demand = n -> GraphUtils.imbalanceAt(n, nodes, minEdgeFlow) * -1;
        return (a, b) -> findFeasibleDemandFlow(nodes, capacity, demand).applyAsDouble(a, b)
                + minEdgeFlow.applyAsDouble(a, b);
    }

    /**
     * Returns a circulation that satisfies the given capacity constraints (upper
     * bounds) and demands or null if none exists.
     */
    static ToDoubleBiFunction<Integer, Integer> findFeasibleDemandFlow(
            Collection<Integer> nodes,
            final ToDoubleBiFunction<Integer, Integer> capacity,
            final ToDoubleFunction<Integer> demand) {

        // Make sure that the demands could even possibly be met.
        double surplus = 0, deficit = 0;
        for (Integer n : nodes) {
            if (demand.applyAsDouble(n) >= EPSILON)
                surplus += demand.applyAsDouble(n);
            if (demand.applyAsDouble(n) <= -EPSILON)
                deficit += -demand.applyAsDouble(n);
        }
        assert Math.abs(surplus - deficit) <= 1e-5;

        int s = -1;
        int t = -2;
        List<Integer> newNodes = new ArrayList<>(nodes);
        newNodes.add(s);
        newNodes.add(t);
        ToDoubleBiFunction<Integer, Integer> newCapacity = (a, b) ->
                (a == s && demand.applyAsDouble(b) < 0) ? demand.applyAsDouble(b) * -1 :
                        (b == t && demand.applyAsDouble(a) > 0) ? demand.applyAsDouble(a) :
                                (a != s && b != t) ? capacity.applyAsDouble(a, b) : 0;

        return GraphUtils.maxFlow(-1, -2, newNodes, newCapacity);
    }

    /**
     * Outputs a CSV row of the given values with the specified number of digits
     * after the decimal.
     */
    private static void writeRow(PrintStream out, double[] vals, int digits) {
        final String fmt = String.format("%%.%df", digits);
        DoubleFunction<String> fmtVal = v -> String.format(fmt, v);
        writeRow(out, DoubleStream.of(vals).mapToObj(fmtVal)
                .toArray(n -> new String[n]));
    }

    /**
     * Outputs a CSV row containing the given values. Note that the current
     * implementation assumes that there are no commas in the column values.
     */
    private static void writeRow(PrintStream out, String[] row) {
        for (int i = 0; i < row.length; i++)
            assert row[i].indexOf(',') < 0;  // quoting not supported here

        out.println(Stream.of(row).collect(Collectors.joining(",")).toString());
    }
}
