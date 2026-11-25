package rv.trading.strategy.engine.web.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rv.trading.strategy.contracts.dto.internal.OrderView;
import rv.trading.strategy.contracts.dto.internal.PageResponse;
import rv.trading.strategy.engine.service.OrderService;

import java.util.List;

@Validated
@RestController
public class InternalOrderController {

    private final OrderService orderService;

    public InternalOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/internal/orders/{id}")
    public OrderView getOrder(@PathVariable("id") @Positive long id) {
        return orderService.getOrder(id);
    }

    @GetMapping("/internal/orders")
    public PageResponse<OrderView> getOrders(
            @RequestParam(name = "brokerClientIds") @NotEmpty List<@Positive Long> brokerClientIds,
            @RequestParam(name = "page", defaultValue = "0") @Min(0) int page,
            @RequestParam(name = "size", defaultValue = "50") @Min(1) @Max(200) int size
    ) {
        return orderService.getOrders(brokerClientIds, page, size);
    }
}
