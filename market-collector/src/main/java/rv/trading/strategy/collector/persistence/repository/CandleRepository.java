package rv.trading.strategy.collector.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import rv.trading.strategy.collector.persistence.entity.CandleEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CandleRepository extends JpaRepository<CandleEntity, Long> {

    @Query(value = """
            SELECT c.*
            FROM (
                SELECT c.*
                FROM candle c
                JOIN timeframe tf ON tf.id = c.timeframe_id
                WHERE c.instrument_pair_id = :instrumentPairId
                  AND tf.code = :timeframeCode
                ORDER BY c.open_time DESC
                LIMIT :limit
            ) c
            ORDER BY c.open_time ASC
            """, nativeQuery = true)
    List<CandleEntity> findLatestByInstrumentPairIdAndTimeframeCode(
            @Param("instrumentPairId") long instrumentPairId,
            @Param("timeframeCode") String timeframeCode,
            @Param("limit") int limit
    );

    @Query(value = """
            SELECT c.*
            FROM candle c
            JOIN timeframe tf ON tf.id = c.timeframe_id
            WHERE c.instrument_pair_id = :instrumentPairId
              AND tf.code = :timeframeCode
              AND c.open_time = :openTime
            """, nativeQuery = true)
    Optional<CandleEntity> findByInstrumentPairIdAndTimeframeCodeAndOpenTime(
            @Param("instrumentPairId") long instrumentPairId,
            @Param("timeframeCode") String timeframeCode,
            @Param("openTime") Instant openTime
    );

    @Query(value = "SELECT id FROM timeframe WHERE code = :timeframeCode", nativeQuery = true)
    Optional<Long> findTimeframeIdByCode(@Param("timeframeCode") String timeframeCode);

}
