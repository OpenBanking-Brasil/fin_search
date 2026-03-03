package br.aof.read_opendata_apis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReadOpendataApisApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReadOpendataApisApplication.class, args);
	}

}
