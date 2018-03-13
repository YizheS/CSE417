package cse417;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Program to find the lineup that has the highest projected points, subject to
 * constraints on the total budget and number at each position.
 */
public class OptimalLineup {

    /**
     * Maximum that can be spent on players in a lineup.
     */
    private static final int BUDGET = 60000;

    // Number of players that must be played at each position:
    private static final int NUM_QB = 1;
    private static final int NUM_RB = 2;
    private static final int NUM_WR = 3;
    private static final int NUM_TE = 1;
    private static final int NUM_K = 1;
    private static final int NUM_DEF = 1;

    /**
     * Entry point for a program to compute optimal lineups.
     */
    public static void main(String[] args) throws Exception {
        ArgParser argParser = new ArgParser("OptimalLineup");
        argParser.addOption("no-high-correlations", Boolean.class);
        args = argParser.parseArgs(args, 1, 1);

        // Parse the list of players from the file given in args[0]
        List<Player> players = new ArrayList<Player>();
        CsvParser parser = new CsvParser(args[0], false, new Object[]{
                // name, position, team, opponent
                String.class, String.class, String.class, String.class,
                // points, price, floor, ceiling, stddev
                Float.class, Integer.class, Float.class, Float.class, Float.class
        });
        while (parser.hasNext()) {
            String[] row = parser.next();
            players.add(new Player(row[0], Position.valueOf(row[1]), row[2], row[3],
                    Integer.parseInt(row[5]), Float.parseFloat(row[4]),
                    Float.parseFloat(row[8])));
        }

        List<Player> roster;
        if (argParser.hasOption("no-high-correlations")) {
            roster = findOptimalLineupWithoutHighCorrelations(players, "");
        } else {
            roster = findOptimalLineup(players);
        }

        displayLineup(roster);
    }

    private static Set<ArrayList<Position>> subproblemSet = new HashSet<>(); //store a set of subproblems

    /**
     * initialize the problem we want to solve in an arraylist
     */
    private static ArrayList<Position> initiazlieProblem() {
        ArrayList<Position> al = new ArrayList<>();
        for (int i = 0; i < NUM_QB; i++) {
            al.add(Position.QB);
        }
        for (int i = 0; i < NUM_RB; i++) {
            al.add(Position.RB);
        }
        for (int i = 0; i < NUM_WR; i++) {
            al.add(Position.WR);
        }
        for (int i = 0; i < NUM_TE; i++) {
            al.add(Position.TE);
        }
        for (int i = 0; i < NUM_K; i++) {
            al.add(Position.K);
        }
        for (int i = 0; i < NUM_DEF; i++) {
            al.add(Position.DEF);
        }
        return al;
    }

    /**
     * populate a set of subproblems
     */
    private static void populateSubproblemSet(ArrayList<Position> positions) {
        subproblemSet.add(positions);
        if (positions.size() != 0) {
            for (Position pos : positions) {
                ArrayList<Position> copyOfPositions = new ArrayList<>(positions);
                copyOfPositions.remove(pos);
                populateSubproblemSet(copyOfPositions);
            }
        }
    }

    /**
     * Initialize the dictionary that maps subproblems to their unique row indices
     */
    private static Map<ArrayList<Position>, Integer> initializeMap(ArrayList<ArrayList<Position>> subproblemList) {
        Map<ArrayList<Position>, Integer> subproblemMap = new HashMap<>();
        int idx = 0;
        for (ArrayList<Position> subp : subproblemList) {
            subproblemMap.put(subp, idx);
            idx++;
        }
        return subproblemMap;
    }

    /**
     * Returns the players in the optimal lineup (in any order).
     */
    private static List<Player> findOptimalLineup(List<Player> allPlayers) {
        // TODO: implement this using dynamic programming
        // INITIALIZE DATA STRUCTURES
        populateSubproblemSet(initiazlieProblem());
        ArrayList<ArrayList<Position>> subproblemList = new ArrayList<>(subproblemSet);
        // sort by arraylist size
        Collections.sort(subproblemList, new Comparator<ArrayList<Position>>() {
            @Override
            public int compare(ArrayList<Position> o1, ArrayList<Position> o2) {
                return o1.size() - o2.size();
            }
        });
        Map<ArrayList<Position>, Integer> subpMap = initializeMap(subproblemList);
        float[][][] table = new float[subproblemSet.size()][allPlayers.size()][BUDGET / 100 + 1];


        // DYNAMIC PROGRAMMING
        for (int n = 0; n < table[0].length; n++) {
            Player player = allPlayers.get(n);
            assert player.getPrice() % 100 == 0;
            for (ArrayList<Position> subPositionList : subproblemList ) {
                int p = subpMap.get(subPositionList);
                if (n + 1 >= subPositionList.size()) {
                    for (int w = 1; w < table[0][0].length; w++) {
                        if (subPositionList.contains(player.getPosition())) {
                            ArrayList<Position> subSubPositionList = new ArrayList<>(subPositionList);
                            subSubPositionList.remove(player.getPosition());
                            int subp = subpMap.get(subSubPositionList);
                            if (player.getPrice() / 100 <= w) {
                                if (n == 0) {
                                    table[p][n][w] = player.getPointsExpected();
                                } else {
                                    if (subSubPositionList.size() > 0 && table[subp][n - 1][w - player.getPrice() / 100] == 0) {
                                        // if the subproblem did not get a complete solution (value is 0), do not add to it
                                        table[p][n][w] = table[p][n - 1][w];
                                    } else {
                                        table[p][n][w] = Math.max(table[p][n - 1][w], table[subp][n - 1][w - player.getPrice() / 100]
                                                + player.getPointsExpected());
                                    }
                                }
                            } else {
                                // if player's price is more expensive than the current w
                                if (n != 0) {
                                    table[p][n][w] = table[p][n - 1][w];
                                } // else 0
                            }
                        } else {
                            // if player's position is not in the current subproblem
                            if (n != 0) {
                                table[p][n][w] = table[p][n - 1][w];
                            } // else 0
                        }
                    }
                }
            }
        }

        // RECOVER SOLUTION
        int p = table.length - 1;
        int n = table[0].length - 1;
        int w = table[0][0].length - 1;

        List<Player> optSolution = new ArrayList<>();
        while (p != 0) {
            ArrayList<Position> sublist = new ArrayList<>(subproblemList.get(p));
            sublist.remove(allPlayers.get(n).getPosition());
            int subp = subpMap.get(sublist);
            if (sublist.size() == 0 && n == 0) {
                // special case when player n_0 is included
                optSolution.add(allPlayers.get(n));
                break;
            } else {
                float left = table[p][n - 1][w];
                if (left == table[p][n][w]) {
                    n--;
                } else {
                    optSolution.add(allPlayers.get(n));
                    p = subp;
                    w -= allPlayers.get(n).getPrice() / 100;
                    n--;
                }
            }
        }
        return optSolution;
    }

    /**
     * compare roster1 and roster2 and return the one with the highest total score
     */
    private static List<Player> compareRoster(List<Player> roster1, List<Player> roster2) {
        float roster1Score = 0;
        float roster2Score = 0;
        for (Player p1 : roster1) {
            roster1Score += p1.getPointsExpected();
        }
        for (Player p2 : roster2) {
            roster2Score += p2.getPointsExpected();
        }
        if (roster1Score > roster2Score) {
            return roster1;
        } else {
            return roster2;
        }

    }


    /**
     * Returns the players in the optimal lineup subject to the constraint that
     * there are no players with high correlations, i.e., no QB-WR, QB-K, or
     * K-DEF from the same team.
     */
    private static List<Player> findOptimalLineupWithoutHighCorrelations(
            List<Player> allPlayers, String label) {
        List<Player> bestRoster = findOptimalLineup(allPlayers);
        Player[] highCorrelations = getHighCorrelations(bestRoster);
        if (getHighCorrelations(bestRoster) != null) {
            List<Player> allPlayersWithoutFirst = new ArrayList<>(allPlayers);
            allPlayersWithoutFirst.remove(highCorrelations[0]);
            List<Player> allPlayersWithoutSecond = new ArrayList<>(allPlayers);
            allPlayersWithoutSecond.remove(highCorrelations[1]);
            bestRoster = compareRoster(findOptimalLineupWithoutHighCorrelations(allPlayersWithoutFirst, null),
                    findOptimalLineupWithoutHighCorrelations(allPlayersWithoutSecond, null));
        }
        return bestRoster;
    }


    /**
     * Returns a pair that are highly correlated or null if none.
     */
    private static Player[] getHighCorrelations(List<Player> roster) {
        Player qb = roster.stream()
                .filter(p -> p.getPosition() == Position.QB).findFirst().get();

        List<Player> wrs = roster.stream()
                .filter(p -> p.getPosition() == Position.WR)
                .sorted((p, q) -> q.getPrice() - p.getPrice())
                .collect(Collectors.toList());
        for (Player wr : wrs) {
            if (qb.getTeam().equals(wr.getTeam()))
                return new Player[]{qb, wr};
        }

        Player k = roster.stream()
                .filter(p -> p.getPosition() == Position.K).findFirst().get();
        if (qb.getTeam().equals(k.getTeam()))
            return new Player[]{qb, k};

        Player def = roster.stream()
                .filter(p -> p.getPosition() == Position.DEF).findFirst().get();
        if (k.getTeam().equals(def.getTeam()))
            return new Player[]{k, def};

        return null;
    }

    /**
     * Displays a lineup, which is assumed to meet the position constraints.
     */
    private static void displayLineup(List<Player> roster) {
        if (roster == null) {
            System.out.println("*** No solution");
            return;
        }

        List<Player> qbs = roster.stream()
                .filter(p -> p.getPosition() == Position.QB)
                .collect(Collectors.toList());
        List<Player> rbs = roster.stream()
                .filter(p -> p.getPosition() == Position.RB)
                .sorted((p, q) -> q.getPrice() - p.getPrice())
                .collect(Collectors.toList());
        List<Player> wrs = roster.stream()
                .filter(p -> p.getPosition() == Position.WR)
                .sorted((p, q) -> q.getPrice() - p.getPrice())
                .collect(Collectors.toList());
        List<Player> tes = roster.stream()
                .filter(p -> p.getPosition() == Position.TE)
                .collect(Collectors.toList());
        List<Player> ks = roster.stream()
                .filter(p -> p.getPosition() == Position.K)
                .collect(Collectors.toList());
        List<Player> defs = roster.stream()
                .filter(p -> p.getPosition() == Position.DEF)
                .collect(Collectors.toList());

        assert qbs.size() == NUM_QB;
        assert rbs.size() == NUM_RB;
        assert wrs.size() == NUM_WR;
        assert tes.size() == NUM_TE;
        assert ks.size() == NUM_K;
        assert defs.size() == NUM_DEF;

        assert roster.stream().mapToInt(p -> p.getPrice()).sum() <= BUDGET;

        System.out.printf(" QB  %s\n", describePlayer(qbs.get(0)));
        System.out.printf("RB1  %s\n", describePlayer(rbs.get(0)));
        System.out.printf("RB2  %s\n", describePlayer(rbs.get(1)));
        System.out.printf("WR1  %s\n", describePlayer(wrs.get(0)));
        System.out.printf("WR2  %s\n", describePlayer(wrs.get(1)));
        System.out.printf("WR3  %s\n", describePlayer(wrs.get(2)));
        System.out.printf(" TE  %s\n", describePlayer(tes.get(0)));
        System.out.printf("  K  %s\n", describePlayer(ks.get(0)));
        System.out.printf("DEF  %s\n", describePlayer(defs.get(0)));
        System.out.printf("*** Totals: price $%d, points %.1f +/- %.1f\n",
                roster.stream().mapToInt(p -> p.getPrice()).sum(),
                roster.stream().mapToDouble(p -> p.getPointsExpected()).sum(),
                Math.sqrt(roster.stream().mapToDouble(
                        p -> p.getPointsVariance()).sum()));
    }

    /**
     * Returns a short description of a player with price and opponent.
     */
    private static String describePlayer(Player p) {
        return String.format("%-20s $%-5d %3s %2s %3s", p.getName(), p.getPrice(),
                p.getTeam(), p.isAtHome() ? "vs" : "at", p.getOpponent());
    }
}
