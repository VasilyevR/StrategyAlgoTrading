package rv.trading.strategy.engine.core.signal;

import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import rv.trading.strategy.contracts.dto.internal.AlgotradingRuntimeConfig;
import rv.trading.strategy.contracts.dto.internal.StrategyIndicatorConfig;
import rv.trading.strategy.contracts.enums.DirectionMode;
import rv.trading.strategy.contracts.enums.ExitMode;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.contracts.enums.OrderSide;
import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.contracts.enums.SignalType;
import rv.trading.strategy.engine.core.indicator.DirectionBitMask;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluation;
import rv.trading.strategy.engine.core.indicator.IndicatorEvaluator;
import rv.trading.strategy.engine.core.indicator.IndicatorRegistry;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public final class SignalBitMaskEvaluator {

    public static final String SNAPSHOT_KEY_SEPARATOR = ".";

    private final IndicatorRegistry indicatorRegistry;

    public SignalBitMaskEvaluator(IndicatorRegistry indicatorRegistry) {
        this.indicatorRegistry = indicatorRegistry;
    }

    public SignalEvaluationResult evaluate(BarSeries series, AlgotradingRuntimeConfig config,
                                           SignalEvaluationContext context) {
        Map<Integer, List<StrategyIndicatorConfig>> groups = config.indicators().stream()
                .filter(indicator -> isDirectional(indicator.role()))
                .collect(Collectors.groupingBy(StrategyIndicatorConfig::conditionGroup));

        BitSet resolvedDirectionMask = DirectionBitMask.empty();
        Map<String, Object> indicatorSnapshot = new HashMap<>();
        for (List<StrategyIndicatorConfig> group : groups.values()) {
            BitSet groupMask = DirectionBitMask.of(true, true);
            for (StrategyIndicatorConfig indicator : group) {
                IndicatorEvaluator evaluator = indicatorRegistry.getEvaluator(indicator.indicatorCode().name());
                IndicatorEvaluation evaluation = evaluator.evaluate(series, indicator.role(), indicator.params());
                groupMask.and(evaluation.directionMask());
                evaluation.snapshot().forEach((key, value) ->
                        indicatorSnapshot.put(indicator.alias() + SNAPSHOT_KEY_SEPARATOR + key, value));
            }
            resolvedDirectionMask.or(groupMask);
        }

        if (DirectionBitMask.hasUp(resolvedDirectionMask) && DirectionBitMask.hasDown(resolvedDirectionMask)) {
            resolvedDirectionMask.clear();
        }

        return new SignalEvaluationResult(
                resolveActions(resolvedDirectionMask, config.directionMode(), config.exitMode(), context),
                resolvedDirectionMask,
                indicatorSnapshot
        );
    }

    private static List<SignalType> resolveActions(BitSet mask, DirectionMode directionMode, ExitMode exitMode,
                                                   SignalEvaluationContext context) {
        List<SignalType> actions = new ArrayList<>(2);
        boolean up = DirectionBitMask.hasUp(mask);
        boolean down = DirectionBitMask.hasDown(mask);
        boolean enforceDirectionMemory = exitMode == ExitMode.SIGNAL_MARKET;

        if (context.openPositionSide().isEmpty()) {
            if (up && directionMode != DirectionMode.SHORT_ONLY
                    && (!enforceDirectionMemory
                    || context.lastSignalDirection().filter(OrderSide.BUY::equals).isEmpty())) {
                actions.add(SignalType.BUY_OPEN);
            }
            if (down && directionMode != DirectionMode.LONG_ONLY
                    && (!enforceDirectionMemory
                    || context.lastSignalDirection().filter(OrderSide.SELL::equals).isEmpty())) {
                actions.add(SignalType.SELL_OPEN);
            }
            return actions;
        }

        PositionSide positionSide = context.openPositionSide().orElseThrow();
        if (positionSide == PositionSide.LONG && down) {
            if (exitMode != ExitMode.TRAILING_STOP) {
                actions.add(SignalType.BUY_CLOSE);
                if (directionMode == DirectionMode.BOTH) {
                    actions.add(SignalType.SELL_OPEN);
                }
            }
        } else if (positionSide == PositionSide.SHORT && up) {
            if (exitMode != ExitMode.TRAILING_STOP) {
                actions.add(SignalType.SELL_CLOSE);
                if (directionMode == DirectionMode.BOTH) {
                    actions.add(SignalType.BUY_OPEN);
                }
            }
        }
        return actions;
    }

    private static boolean isDirectional(IndicatorRole role) {
        return role == IndicatorRole.TRIGGER || role == IndicatorRole.FILTER;
    }

}
