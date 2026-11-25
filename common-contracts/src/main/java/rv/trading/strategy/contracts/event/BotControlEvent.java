package rv.trading.strategy.contracts.event;

import rv.trading.strategy.contracts.enums.BotControlCommand;

import java.time.Instant;

public record BotControlEvent(long algotradingId, BotControlCommand command, Instant emittedAt) {
}
