package cn.lotterydcb.prediction;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class PredictionRequest {

    @Min(1)
    @Max(50)
    private int ticketCount = 8;

    @Min(100)
    @Max(50000)
    private int candidatePoolSize = 5000;

    @Min(7)
    @Max(12)
    private int redPoolSize = 9;

    @Min(10)
    @Max(1000)
    private int lookback = 100;

    @NotNull
    private RotationMode rotationMode = RotationMode.PAIR_COVERAGE;

    @NotNull
    private BetMode betMode = BetMode.STANDARD;

    @Min(6)
    @Max(33)
    private int compoundRedCount = 7;

    @Min(1)
    @Max(16)
    private int compoundBlueCount = 2;

    @NotNull
    private BlueSelectionMode blueSelectionMode = BlueSelectionMode.FREQUENCY_BALANCED;

    @Min(-9007199254740991L)
    @Max(9007199254740991L)
    private Long seed;

    @Valid
    @NotNull
    private StrategyParameters strategy = new StrategyParameters();

    public int getTicketCount() {
        return ticketCount;
    }

    public void setTicketCount(int ticketCount) {
        this.ticketCount = ticketCount;
    }

    public int getCandidatePoolSize() {
        return candidatePoolSize;
    }

    public void setCandidatePoolSize(int candidatePoolSize) {
        this.candidatePoolSize = candidatePoolSize;
    }

    public int getRedPoolSize() {
        return redPoolSize;
    }

    public void setRedPoolSize(int redPoolSize) {
        this.redPoolSize = redPoolSize;
    }

    public int getLookback() {
        return lookback;
    }

    public void setLookback(int lookback) {
        this.lookback = lookback;
    }

    public RotationMode getRotationMode() {
        return rotationMode;
    }

    public void setRotationMode(RotationMode rotationMode) {
        this.rotationMode = rotationMode;
    }

    public BetMode getBetMode() {
        return betMode;
    }

    public void setBetMode(BetMode betMode) {
        this.betMode = betMode;
    }

    public int getCompoundRedCount() {
        return compoundRedCount;
    }

    public void setCompoundRedCount(int compoundRedCount) {
        this.compoundRedCount = compoundRedCount;
    }

    public int getCompoundBlueCount() {
        return compoundBlueCount;
    }

    public void setCompoundBlueCount(int compoundBlueCount) {
        this.compoundBlueCount = compoundBlueCount;
    }

    public BlueSelectionMode getBlueSelectionMode() {
        return blueSelectionMode;
    }

    public void setBlueSelectionMode(BlueSelectionMode blueSelectionMode) {
        this.blueSelectionMode = blueSelectionMode;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public StrategyParameters getStrategy() {
        return strategy;
    }

    public void setStrategy(StrategyParameters strategy) {
        this.strategy = strategy;
    }

}
