package at.ymeri.my.finance;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The {@code Application} module has no {@code @SpringBootApplication} of its own (that lives in
 * {@code Launcher}, a separate module not on this module's test classpath) — {@code @WebMvcTest}
 * needs one somewhere in the test source tree to anchor its auto-configuration. Test-only, never
 * packaged.
 */
@SpringBootApplication
class TestApplication {
}
