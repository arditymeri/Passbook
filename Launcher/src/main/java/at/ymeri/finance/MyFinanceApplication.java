package at.ymeri.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"at.ymeri", "at.ymeri.finance"})
public class MyFinanceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MyFinanceApplication.class, args);
	}

}
