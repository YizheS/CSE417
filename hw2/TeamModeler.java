/*
Ken Lo
CSE 417 HW2
NFL Modeling
 */

package cse417;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.function.DoubleFunction;

import static cse417.TeamModel.TEAMS;


/**
 * Program that finds {@code TeamModel}s that best fit provided data. The
 * option {@code --train} shows the results of using different penalties when
 * fitting historical data. Without that option, this just finds the best model
 * using the last {@code WEEKS} weeks of data. The latter supports using either
 * a fixed penalty or finding the model with a fixed number of non-zero params.
 */
public class TeamModeler {

    /**
     * Number of weeks to use for building models.
     */
    private static int WEEKS = 6;

    /**
     * Tolerance parameter used by coordinate descent. The method will stop
     * iterating when the L0 norm of the change is less than this amount.
     */
    private static double TOLERANCE = 5e-3;

    /**
     * Entry point for a program to build a model of NFL teams.
     */
    public static void main(String[] args) throws IOException {
        ArgParser argParser = new ArgParser("TeamModeler");
        argParser.addOption("train", Boolean.class);
        argParser.addOption("penalty", Double.class);
        argParser.addOption("num-nonzero", Integer.class);
        argParser.addOption("verbose", Boolean.class);
        args = argParser.parseArgs(args, 1, 1);

        if (argParser.hasOption("train")) {
            train(args[0]);
        } else {
            // Find the drives in the last WEEKS worth of weeks.
            List<Drive> drives = loadDrives(args[0], 1, 16);
            int maxWeek = drives.stream().map((d) -> d.week)
                    .max((a, b) -> Double.compare(a, b)).get();
            drives = drives.stream().filter((d) -> maxWeek - WEEKS + 1 <= d.week)
                    .collect(Collectors.toList());

            TeamModel model;
            if (argParser.hasOption("num-nonzero")) {
                model = findBestSparseModel(drives,
                        argParser.getIntegerOption("num-nonzero"), TOLERANCE,
                        argParser.hasOption("verbose"));
            } else {
                double penalty = argParser.hasOption("penalty") ?
                        argParser.getDoubleOption("penalty") : 0.0;
                model = findBestModel(drives, penalty, TOLERANCE,
                        argParser.hasOption("verbose"));
            }
            model.printTo(System.out);

        }
    }

    /**
     * Returns the drives described in the given file from games in weeks of the
     * NFL season in the given range ({@code minWeek} to {@code maxWeek}).
     */
    private static List<Drive> loadDrives(
            String fileName, int minWeek, int maxWeek) throws IOException {
        List<Drive> drives = new ArrayList<Drive>();
        CsvParser parser = new CsvParser(fileName, true, new Object[]{
                String.class, String.class, Integer.class, Float.class, Float.class
        });
        while (parser.hasNext()) {
            String[] parts = parser.next();
            int week = Integer.parseInt(parts[2]);
            if (minWeek <= week && week <= maxWeek) {
                double expStartPts = Double.parseDouble(parts[3]);
                double expEndPts = Double.parseDouble(parts[4]);
                drives.add(new Drive(week, parts[0], parts[1], expStartPts, expEndPts));
            }
        }
        return drives;
    }

    /**
     * Fills in the given maps with the best model of how the offenses increase
     * the expected points in each drive and how the defenses decrease it.
     *
     * @param drives  List of the drives that the model should describe.
     * @param penalty Constant factor on the penalty term of the loss function.
     * @param tol     Stop when the L0 change per iteration less than this amount.
     * @param verbose If true, prints progress of the model fitting process.
     */
    private static TeamModel findBestModel(
            final List<Drive> drives, double penalty, double tol, boolean verbose) {
        TeamModel best = new TeamModel();  // start with all zeros
        double change = 10000;
        while (change > tol) {
            TeamModel prev = best.copy();

            // tune offense
            for (String team : TEAMS) {

                double newOffense = Optimizer.findMinimumOfUnimodal(
                        t -> best.copy().setOffense(team, t).evalLoss(drives, penalty), -8, 8);
                if (verbose) {
                    System.out.println("Offense for " + team + " is now " + newOffense);
                }
                best.setOffense(team, newOffense);
            }

            // tune defense
            for (String team : TEAMS) {

                double newDefense = Optimizer.findMinimumOfUnimodal(
                        t -> best.copy().setDefense(team, t).evalLoss(drives, penalty), -8, 8);
                if (verbose) {
                    System.out.println("Defense for " + team + " is now " + newDefense);
                }
                best.setDefense(team, newDefense);
            }

            // tune constant
            double newConstant = Optimizer.findMinimumOfUnimodal(
                    t -> best.copy().setConstant(t).evalLoss(drives, penalty), -8, 8);
            if (verbose) {
                System.out.println("Constant is now " + newConstant);
            }
            best.setConstant(newConstant);


            // check change
            change = best.copy().addScaledBy(-1, prev).norm0();
            if (verbose) {
                System.out.println("Difference between new model and old model: " + change);
            }
        }
        return best;
    }

    /**
     * Like {@code findBestModel} above but taking the desired number of non-zero
     * parameters in the model instead of the penalty.
     * <p>
     * If increasing the penalty by less than 0.0001 changes the number of
     * non-zero parameters from less than the desired amount to more than the
     * desired amount, then this will just return the model with fewer parameters
     */
    private static TeamModel findBestSparseModel(
            final List<Drive> drives, int numNonZeros, double tol, boolean verbose) {

        int thisNumNonZeros = 0;
        double i = 0;
        double j = 0.05;
        double prevM = 0;
        TeamModel model = new TeamModel();
        while (i < j) {
            double m = (i + j) / 2;
            if (verbose) {
                System.out.printf("Differences between new and old m: %.6f\n", Math.abs(m - prevM));
            }
            // return model if the change is less than 0.0001
            if (Math.abs(m - prevM) < 0.0001) {
                return model;
            } else {
                prevM = m;
            }
            model = findBestModel(drives, m, tol, verbose);
            thisNumNonZeros = model.countNonZeroParameters(0.05);
            if (verbose) {
                System.out.printf("Current penalty: %.5f, Current Number of Non Zero Params: %2d\n", m, thisNumNonZeros);
            }
            if (thisNumNonZeros > numNonZeros) {
                i = m + 0.0001;
            } else if (thisNumNonZeros == numNonZeros) {
                return model;
            } else {
                j = m;
            }
        }
        return null;
    }

    /**
     * Prints the results of using different penalties .001 to 0.050 on each
     * period of {@code WEEKS} weeks in the provided data. That is, for each
     * period of {@code WEEKS+1} consecutive weeks in the data, it trains a model
     * using the first {@code WEEKS} of data (for each penalty) and then shows
     * the results for predicting the next week.
     * <p>
     * <b>Important</b>: Errors are normalized by subtracting off the minimum
     * error for any of the penalties tried for those weeks. This means, for each
     * period of {@code WEEKS} weeks, at least one penalty gives error zero.
     * (This normalization is necessary because some weeks are harder to predict
     * than others, and we want the model that works best on an average week not
     * just the worst week.)
     */
    private static void train(String fileName) throws IOException {
        for (int i = 1; i + WEEKS < 17; i++) {
            List<Drive> drives = loadDrives(fileName, i, i + WEEKS - 1);
            List<Drive> testDrives = loadDrives(fileName, i + WEEKS, i + WEEKS);
            List<TeamModel> models = new ArrayList<>();

            double minError = 99999;
            for (double penalty = 0.050; penalty >= 0.000; penalty -= 0.001) {
                TeamModel best = findBestModel(drives, penalty, TOLERANCE, false);
                models.add(best);

                double thisError = best.evalLoss(testDrives, 0.0);
                if (thisError < minError) {
                    minError = thisError;
                }
            }

            // print
            for (TeamModel model : models) {
                System.out.printf("%02d %g\n", model.countNonZeroParameters(0.005), model.evalLoss(testDrives,
                        0.0) - minError);
            }
        }
    }
}




