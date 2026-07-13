package cn.lotterydcb.prediction;

import java.util.List;

public record ConstraintEvaluation(boolean accepted, List<String> rejectionReasons) {

}
