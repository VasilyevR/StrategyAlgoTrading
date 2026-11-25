package rv.trading.strategy.contracts;

import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import rv.trading.strategy.contracts.dto.internal.OrderView;
import rv.trading.strategy.contracts.dto.internal.PageResponse;
import rv.trading.strategy.contracts.dto.internal.PositionView;
import rv.trading.strategy.contracts.dto.internal.SignalView;
import rv.trading.strategy.contracts.enums.ExitMode;
import rv.trading.strategy.contracts.enums.IndicatorRole;
import rv.trading.strategy.contracts.enums.IndicatorCode;
import rv.trading.strategy.contracts.enums.IndicatorConditionType;
import rv.trading.strategy.contracts.enums.MacdLine;
import rv.trading.strategy.contracts.enums.OrderSide;
import rv.trading.strategy.contracts.enums.OrderStatus;
import rv.trading.strategy.contracts.enums.OrderType;
import rv.trading.strategy.contracts.enums.PositionSide;
import rv.trading.strategy.contracts.enums.PositionStatus;
import rv.trading.strategy.contracts.enums.SignalStatus;
import rv.trading.strategy.contracts.enums.SignalType;
import rv.trading.strategy.contracts.enums.SarMode;
import rv.trading.strategy.contracts.enums.TimeInForce;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SharedContractTypesTest {

    @Test
    void sharedEnumsExposeStableWireValues() {
        assertThat(OrderSide.values()).containsExactly(OrderSide.BUY, OrderSide.SELL);
        assertThat(OrderType.values()).containsExactly(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP, OrderType.STOP_LIMIT);
        assertThat(OrderStatus.values()).containsExactly(
                OrderStatus.PENDING_SUBMIT,
                OrderStatus.PRE_SUBMITTED,
                OrderStatus.OPEN,
                OrderStatus.PARTIALLY_FILLED,
                OrderStatus.FILLED,
                OrderStatus.CANCELED,
                OrderStatus.REJECTED
        );
        assertThat(TimeInForce.values()).containsExactly(TimeInForce.GTC, TimeInForce.IOC, TimeInForce.FOK, TimeInForce.DAY);
        assertThat(PositionSide.values()).containsExactly(PositionSide.LONG, PositionSide.SHORT);
        assertThat(PositionStatus.values()).containsExactly(PositionStatus.OPEN, PositionStatus.CLOSED);
        assertThat(ExitMode.values()).containsExactly(
                ExitMode.SIGNAL_MARKET,
                ExitMode.TRAILING_STOP,
                ExitMode.HYBRID
        );
        assertThat(IndicatorRole.values()).containsExactly(
                IndicatorRole.TRIGGER,
                IndicatorRole.FILTER,
                IndicatorRole.TRAILING_STOP
        );
        assertThat(IndicatorCode.values()).containsExactly(
                IndicatorCode.SMA_CROSS, IndicatorCode.MACD, IndicatorCode.CMF, IndicatorCode.SAR,
                IndicatorCode.AO, IndicatorCode.ULTOSC, IndicatorCode.CCI, IndicatorCode.ROC,
                IndicatorCode.KAMA, IndicatorCode.STOCH, IndicatorCode.STOCH_RVI
        );
        assertThat(IndicatorConditionType.values()).containsExactly(
                IndicatorConditionType.CROSS, IndicatorConditionType.THRESHOLD, IndicatorConditionType.RANGE
        );
        assertThat(MacdLine.values()).containsExactly(MacdLine.SIGNAL, MacdLine.MACD, MacdLine.HISTOGRAM);
        assertThat(SarMode.values()).containsExactly(SarMode.TREND, SarMode.CONTRARIAN_EXTREME);
        assertThat(SignalType.values()).containsExactly(
                SignalType.BUY_OPEN,
                SignalType.BUY_CLOSE,
                SignalType.SELL_OPEN,
                SignalType.SELL_CLOSE
        );
        assertThat(SignalStatus.values()).containsExactly(
                SignalStatus.DETECTED,
                SignalStatus.EXECUTED,
                SignalStatus.IGNORED,
                SignalStatus.FAILED
        );
    }

    @Test
    void internalDtosAreInstantiableRecords() {
        assertThat(Instancio.create(OrderView.class)).isInstanceOf(OrderView.class);
        assertThat(Instancio.create(PositionView.class)).isInstanceOf(PositionView.class);
        assertThat(Instancio.create(SignalView.class)).isInstanceOf(SignalView.class);
    }

    @Test
    void pageResponseRetainsContentAndPaginationMetadata() {
        List<String> content = List.of("first", "second");

        PageResponse<String> page = new PageResponse<>(content, 2, 2, 5, 3);

        assertThat(page.content()).containsExactly("first", "second");
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.size()).isEqualTo(2);
        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }
}
