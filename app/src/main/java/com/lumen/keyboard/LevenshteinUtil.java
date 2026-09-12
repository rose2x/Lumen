package com.lumen.keyboard;

/**
 * Plain Java utility for edit-distance calculations.
 * <p>
 * Kept in Java (rather than Kotlin, like the rest of the project) simply to
 * demonstrate/keep a small Java presence in the codebase -- calling it from
 * Kotlin needs no wrapper code at all, Kotlin/Java interop is seamless.
 */
public final class LevenshteinUtil {

    private LevenshteinUtil() {
        // static utility, no instances
    }

    /** Standard dynamic-programming edit distance between two strings. */
    public static int distance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                int deletion = dp[i - 1][j] + 1;
                int insertion = dp[i][j - 1] + 1;
                int substitution = dp[i - 1][j - 1] + cost;
                dp[i][j] = Math.min(Math.min(deletion, insertion), substitution);
            }
        }
        return dp[a.length()][b.length()];
    }
}
