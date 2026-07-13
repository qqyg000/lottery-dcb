package cn.lotterydcb.controller;

import cn.lotterydcb.prediction.PredictionRequest;
import cn.lotterydcb.prediction.PredictionResponse;
import cn.lotterydcb.prediction.PredictionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping("/defaults")
    public PredictionRequest defaults() {
        return predictionService.defaults();
    }

    @PostMapping("/generate")
    public PredictionResponse generate(@Valid @RequestBody PredictionRequest request) {
        return predictionService.generate(request);
    }

}
