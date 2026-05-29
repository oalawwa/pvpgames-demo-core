package gg.pvpgames.demo.stats;

/**
 * Standard Elo rating math, isolated so it's trivially unit-testable and tunable.
 *
 * <p>Expected score for A vs B:  Ea = 1 / (1 + 10^((Rb - Ra)/400)).
 * New rating: Ra' = Ra + K * (Sa - Ea), where Sa is 1 win / 0.5 draw / 0 loss.
 */
public final class Elo {

    private Elo() {
    }

    /** Probability that player A beats player B given their current ratings. */
    public static double expected(int ratingA, int ratingB) {
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
    }

    /**
     * Compute the rating delta to apply to A.
     *
     * @param score 1.0 = A won, 0.5 = draw, 0.0 = A lost
     * @param kFactor volatility constant (commonly 16–40)
     * @return signed change to add to A's rating (already rounded)
     */
    public static int delta(int ratingA, int ratingB, double score, int kFactor) {
        double expected = expected(ratingA, ratingB);
        return (int) Math.round(kFactor * (score - expected));
    }

    /** Apply a delta with a configurable floor so ratings never go below {@code minimum}. */
    public static int apply(int rating, int delta, int minimum) {
        return Math.max(minimum, rating + delta);
    }
}
