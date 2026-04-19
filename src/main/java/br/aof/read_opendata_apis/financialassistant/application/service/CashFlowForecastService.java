package br.aof.read_opendata_apis.financialassistant.application.service;

import br.aof.read_opendata_apis.financialassistant.application.dto.CashFlowForecast;
import br.aof.read_opendata_apis.financialassistant.application.repository.FinanceEventRepository;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceEvent;
import br.aof.read_opendata_apis.financialassistant.domain.model.FinanceEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CashFlowForecastService {

    private final FinanceEventRepository financeEventRepository;

    public CashFlowForecast forecastNext30Days() {
        LocalDateTime now = LocalDateTime.now();
        List<FinanceEvent> last90d = financeEventRepository.findByOccurredAtBetween(now.minusDays(90), now);
        BigDecimal inflow = sumByType(last90d, FinanceEventType.INCOME).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
        BigDecimal outflow = sumByType(last90d, FinanceEventType.EXPENSE).divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP).abs();
        BigDecimal net = inflow.subtract(outflow);
        String riskLevel = net.compareTo(BigDecimal.ZERO) < 0 ? "HIGH" : net.compareTo(outflow.multiply(new BigDecimal("0.20"))) < 0 ? "MEDIUM" : "LOW";
        return new CashFlowForecast(inflow, outflow, net, riskLevel);
    }

    private BigDecimal sumByType(List<FinanceEvent> events, FinanceEventType eventType) {
        return events.stream()
                .filter(event -> event.getEventType() == eventType)
                .map(FinanceEvent::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
