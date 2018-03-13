package cse417;

public class test {
    public static void main(String[] args) {
        try {
            String[] arg1 = {"data/wk1-2017.csv"};
            String[] arg2 = {"data/wk2-2017.csv"};
            String[] arg3 = {"data/wk3-2017.csv"};
            OptimalLineup.main(arg1);
            OptimalLineup.main(arg2);
            OptimalLineup.main(arg3);
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}
