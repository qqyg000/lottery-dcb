package cn.lotterydcb.prediction;

import cn.lotterydcb.config.LotteryHistoryProperties;
import cn.lotterydcb.history.HistoryRepository;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredictionServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void requestDefaultsShouldRemainBackwardCompatible() {
        PredictionRequest request = new PredictionRequest();

        assertEquals(BetMode.STANDARD, request.getBetMode());
        assertEquals(8, request.getTicketCount());
    }

    @Test
    void sameSeedShouldProduceSameTickets() {
        PredictionService service = createService();
        PredictionRequest request = new PredictionRequest();
        request.setSeed(20260713L);
        request.setTicketCount(5);
        request.setCandidatePoolSize(1000);
        request.setRotationMode(RotationMode.PAIR_COVERAGE);
        request.setBetMode(BetMode.STANDARD);

        PredictionResponse first = service.generate(request);
        PredictionResponse second = service.generate(request);

        assertEquals(first.tickets(), second.tickets());
        assertEquals(5, first.tickets().size());
        first.tickets().forEach(ticket -> {
            assertEquals(6, ticket.redBalls().size());
            assertEquals(6, ticket.redBalls().stream().distinct().count());
            assertTrue(ticket.blueBall() >= 1 && ticket.blueBall() <= 16);
            assertTrue(ConstraintEvaluator.evaluate(ticket.metrics(), request.getStrategy()).accepted());
        });
    }

    @Test
    void firstSixteenBlueBallsShouldBeDistinct() {
        PredictionService service = createService();
        PredictionRequest request = new PredictionRequest();
        request.setSeed(7L);
        request.setTicketCount(16);
        request.setCandidatePoolSize(2500);
        request.setRedPoolSize(11);
        request.setRotationMode(RotationMode.PAIR_COVERAGE);
        request.setBetMode(BetMode.STANDARD);

        PredictionResponse response = service.generate(request);
        List<Integer> blueBalls = response.tickets().stream().map(PredictionTicket::blueBall).toList();

        assertEquals(16, blueBalls.stream().distinct().count());
        assertNotEquals(
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16),
                blueBalls
        );
    }

    @Test
    void compoundSevenPlusTwoShouldExpandToFourteenValidTickets() {
        PredictionService service = createService();
        PredictionRequest request = new PredictionRequest();
        request.setSeed(20260713L);
        request.setTicketCount(1);
        request.setCandidatePoolSize(1000);
        request.setBetMode(BetMode.COMPOUND_7_2);

        PredictionResponse first = service.generate(request);
        PredictionResponse second = service.generate(request);

        assertEquals(first.compoundGroups(), second.compoundGroups());
        assertEquals(BetMode.COMPOUND_7_2, first.betMode());
        assertEquals(1, first.compoundGroups().size());
        assertEquals(14, first.tickets().size());
        assertEquals(14, first.expandedTicketCount());
        assertEquals(28, first.totalStakeAmountYuan());

        CompoundPredictionGroup group = first.compoundGroups().get(0);
        assertEquals(group.expandedTickets(), first.tickets());
        assertEquals(7, group.redBalls().size());
        assertEquals(7, group.redBalls().stream().distinct().count());
        assertEquals(2, group.blueBalls().size());
        assertEquals(2, group.blueBalls().stream().distinct().count());
        assertEquals(14, group.expandedTickets().size());
        assertEquals(14, group.expandedTickets().stream()
                .map(ticket -> ticket.redBalls() + ":" + ticket.blueBall())
                .distinct()
                .count());
        assertEquals(7, group.expandedTickets().stream()
                .map(PredictionTicket::redBalls)
                .distinct()
                .count());
        group.expandedTickets().stream()
                .map(PredictionTicket::redBalls)
                .distinct()
                .forEach(redBalls -> assertEquals(
                        2,
                        group.expandedTickets().stream()
                                .filter(ticket -> ticket.redBalls().equals(redBalls))
                                .count()
                ));
        group.blueBalls().forEach(blueBall -> assertEquals(
                7,
                group.expandedTickets().stream()
                        .filter(ticket -> ticket.blueBall() == blueBall)
                        .count()
        ));
        group.expandedTickets().forEach(ticket -> {
            assertEquals(6, ticket.redBalls().size());
            assertTrue(group.redBalls().containsAll(ticket.redBalls()));
            assertTrue(group.blueBalls().contains(ticket.blueBall()));
            assertTrue(ConstraintEvaluator.evaluate(ticket.metrics(), request.getStrategy()).accepted());
        });
        assertEquals(7, first.coverage().redPool().size());
        assertEquals(21, first.coverage().coveredPairCount());
        assertEquals(21, first.coverage().possiblePairCount());
        assertEquals(1.0, first.coverage().pairCoverageRatio());
    }

    @Test
    void twoCompoundGroupsShouldReturnTwentyEightTopLevelTickets() {
        PredictionService service = createService();
        PredictionRequest request = new PredictionRequest();
        request.setSeed(88L);
        request.setTicketCount(2);
        request.setCandidatePoolSize(1000);
        request.setBetMode(BetMode.COMPOUND_7_2);

        PredictionResponse response = service.generate(request);

        assertEquals(2, response.compoundGroups().size());
        assertEquals(28, response.tickets().size());
        assertEquals(28, response.expandedTicketCount());
        assertEquals(56, response.totalStakeAmountYuan());
        assertEquals(
                response.compoundGroups().stream()
                        .flatMap(group -> group.expandedTickets().stream())
                        .toList(),
                response.tickets()
        );
        for (int index = 0; index < response.tickets().size(); index++) {
            assertEquals(index + 1, response.tickets().get(index).sequence());
        }
    }

    @Test
    void blueSelectionModeShouldNotChangeCompoundRedPool() {
        PredictionService service = createService();
        PredictionRequest request = new PredictionRequest();
        request.setSeed(99L);
        request.setTicketCount(1);
        request.setCandidatePoolSize(1000);
        request.setBetMode(BetMode.COMPOUND_7_2);

        request.setBlueSelectionMode(BlueSelectionMode.RANDOM);
        PredictionResponse randomBlue = service.generate(request);
        request.setBlueSelectionMode(BlueSelectionMode.COLD_HOT_MIX);
        PredictionResponse balancedBlue = service.generate(request);

        assertEquals(
                randomBlue.compoundGroups().get(0).redBalls(),
                balancedBlue.compoundGroups().get(0).redBalls()
        );
    }

    @Test
    void compoundModeShouldRejectTooManyGroups() {
        PredictionService service = createService();
        PredictionRequest request = new PredictionRequest();
        request.setBetMode(BetMode.COMPOUND_7_2);
        request.setTicketCount(11);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.generate(request)
        );

        assertTrue(exception.getMessage().contains("最多生成 10 组"));
    }

    private PredictionService createService() {
        LotteryHistoryProperties properties = new LotteryHistoryProperties();
        properties.setFilePath(tempDirectory.resolve("ssq-history.json"));
        HistoryRepository repository = new HistoryRepository(
                properties,
                new GsonBuilder().setPrettyPrinting().create()
        );
        repository.initialize();
        return new PredictionService(repository);
    }

}
