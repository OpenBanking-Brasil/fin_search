package br.aof.read_opendata_apis;

import br.aof.read_opendata_apis.infrastructure.config.OpenFinanceProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(OpenFinanceProperties.class)
public class ReadOpendataApisApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReadOpendataApisApplication.class, args);
	}

}
