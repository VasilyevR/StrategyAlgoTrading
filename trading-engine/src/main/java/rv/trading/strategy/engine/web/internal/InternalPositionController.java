package rv.trading.strategy.engine.web.internal;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rv.trading.strategy.contracts.dto.internal.PositionView;
import rv.trading.strategy.contracts.enums.PositionStatus;
import rv.trading.strategy.engine.service.PositionService;

import java.util.List;

@Validated
@RestController
public class InternalPositionController {

    private final PositionService positionService;

    public InternalPositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping("/internal/positions/{id}")
    public PositionView getPosition(@PathVariable("id") @Positive long id) {
        return positionService.getPosition(id);
    }

    @GetMapping("/internal/positions")
    public List<PositionView> getPositions(
            @RequestParam(name = "brokerClientIds") @NotEmpty List<@Positive Long> brokerClientIds,
            @RequestParam(name = "status", required = false) PositionStatus status
    ) {
        return positionService.getPositions(brokerClientIds, status);
    }
}
