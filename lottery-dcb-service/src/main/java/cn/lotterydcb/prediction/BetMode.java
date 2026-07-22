package cn.lotterydcb.prediction;

public enum BetMode {

    STANDARD,
    COMPOUND,
    COMPOUND_7_2;

    public boolean isCompound() {
        return this == COMPOUND || this == COMPOUND_7_2;
    }

}
