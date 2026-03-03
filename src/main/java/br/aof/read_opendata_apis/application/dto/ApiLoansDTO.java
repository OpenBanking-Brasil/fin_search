package br.aof.read_opendata_apis.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiLoansDTO {
    @JsonProperty("data")
    public List<Data> data;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Data {
        @Id
        private String id;

        @JsonProperty("participant")
        public Participant participant;

        @JsonProperty("type")
        public String type;

        @JsonProperty("fees")
        public Fees fees;

        @JsonProperty("interestRates")
        public List<InterestRates> interestRates;

        @JsonProperty("requiredWarranties")
        public List<String> requiredWarranties;

        @JsonProperty("termsConditions")
        public String termsConditions;
    }


    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Participant {
        @JsonProperty("brand")
        public String brand;

        @JsonProperty("name")
        public String name;

        @JsonProperty("cnpjNumber")
        public String cnpjNumber;

        @JsonProperty("urlComplementaryList")
        public String urlComplementaryList;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Fees {
        @JsonProperty("services")
        public List<Services> service;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Services {
        @JsonProperty("name")
        public String name;

        @JsonProperty("code")
        public String code;

        @JsonProperty("chargingTriggerInfo")
        public String chargingTriggerInfo;

        @JsonProperty("prices")
        public List<Prices> prices;

        @JsonProperty("minimum")
        public Minimum minimum;

        @JsonProperty("maximum")
        public Minimum maximum;

    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Prices {
        @JsonProperty("interval")
        public String interval;

        @JsonProperty("value")
        public String value;

        @JsonProperty("currency")
        public String currency;

        @JsonProperty("customers")
        public Customers customers;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Customers {
        @JsonProperty("rate")
        public String rate;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Minimum {
        @JsonProperty("value")
        public String value;

        @JsonProperty("currency")
        public String currency;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Maximum {
        @JsonProperty("value")
        public String value;

        @JsonProperty("currency")
        public String currency;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterestRates {
        @JsonProperty("referentialRateIndexer")
        public String referentialRateIndexer;

        @JsonProperty("rate")
        public String rate;

        @JsonProperty("applications")
        public List<Applications> applications;

        @JsonProperty("minimumRate")
        public String minimumRate;

        @JsonProperty("maximumRate")
        public String maximumRate;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Applications {
        @JsonProperty("interval")
        public String interval;

        @JsonProperty("indexer")
        public Customers indexer;

        @JsonProperty("customers")
        public Customers customers;
    }

/*    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 80)
    private String brand;

    @Column(length = 80)
    private String name;

    @Column(length = 14)
    private String cnpjNumber;

    @Column(length = 1024)
    private String urlComplementaryList;

    @Column(length = 120)
    private String type;

    @Column(length = 250)
    private String fees_services_name;

    @Column(length = 100)
    private String fees_services_code;

    @Column(length = 2000)
    private String fees_services_chargingTriggerInfo;

    @Column(length = 20)
    private String fees_services_prices_interval;

    @Column(length = 12)
    private String fees_services_prices_value;

    @Column(length = 3)
    private String fees_services_prices_currency;

    @Column(length = 8)
    private String fees_services_prices_customers_rate;

    @Column(length = 12)
    private String fees_services_minimum_value;

    @Column(length = 3)
    private String fees_services_minimum_currency;

    @Column(length = 12)
    private String fees_services_maximum_value;

    @Column(length = 3)
    private String fees_services_maximum_currency;

    @Column(length = 50)
    private String interestRates_referentialRateIndexer;

    @Column(length = 8)
    private String interestRates_rate;

    @Column(length = 20)
    private String interestRates_applications_interval;

    @Column(length = 8)
    private String interestRates_applications_indexer_rate;

    @Column(length = 8)
    private String interestRates_applications_customers_rate;

    @Column(length = 8)
    private String interestRates_minimumRate;

    @Column(length = 8)
    private String interestRates_maximumRate;

    @Column(length = 50)
    private String requiredWarranties;

    @Column(length = 2000)
    private String termsConditions;

    @Column(name = "payload")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode payload;

    @Column(length = 1200)
    private String url;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();*/

}
