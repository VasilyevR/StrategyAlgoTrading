package rv.trading.strategy.engine.web.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rv.trading.strategy.contracts.dto.internal.PageResponse;
import rv.trading.strategy.contracts.dto.internal.SignalView;
import rv.trading.strategy.engine.service.SignalService;

@Validated
@RestController
public class InternalSignalController {

    private final SignalService signalService;

    public InternalSignalController(SignalService signalService) {
        this.signalService = signalService;
    }

    @GetMapping("/internal/signals/{id}")
    public SignalView getSignal(@PathVariable("id") @Positive long id) {
        return signalService.getSignal(id);
    }

    @GetMapping("/internal/signals")
    public PageResponse<SignalView> getSignals(
            @RequestParam(name = "algotradingId") @Positive long algotradingId,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(200) int size
    ) {
        return signalService.getSignals(algotradingId, page, size);
    }
}
