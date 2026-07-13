package cn.lotterydcb.history;

import java.util.List;

public record DrawRecord(
        String issue,
        String drawDate,
        List<Integer> redBalls,
        int blueBall
) {

    public DrawRecord {
        redBalls = redBalls == null ? List.of() : redBalls.stream().sorted().toList();
    }

    public boolean isValid() {
        return issue != null
                && !issue.isBlank()
                && drawDate != null
                && redBalls.size() == 6
                && redBalls.stream().distinct().count() == 6
                && redBalls.stream().allMatch(number -> number >= 1 && number <= 33)
                && blueBall >= 1
                && blueBall <= 16;
    }

}
