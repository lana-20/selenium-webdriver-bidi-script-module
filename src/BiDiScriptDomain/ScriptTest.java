package BiDiScriptDomain;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.bidi.log.ConsoleLogEntry;
import org.openqa.selenium.bidi.log.LogLevel;
import org.openqa.selenium.bidi.module.LogInspector;
import org.openqa.selenium.bidi.module.Script;
import org.openqa.selenium.bidi.script.EvaluateResult;
import org.openqa.selenium.bidi.script.EvaluateResultSuccess;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import org.testng.Assert;

public class ScriptTest {

	static protected WebDriver driver;
	static protected LogInspector logInspector;
	static protected Script script;

	private String webPage = "https://selenium.dev/selenium/web/blank";

	@BeforeTest
	public void setup() {
		var options = new FirefoxOptions();
		options.enableBiDi();
		driver = new FirefoxDriver(options);
		logInspector = new LogInspector(driver);
		script = new Script(driver);
	}

	@AfterTest
	public void teardown() {
		logInspector.close();
		script.close();
		driver.quit();
	}

	@Test
	public void preloadScriptTest() throws InterruptedException, TimeoutException, ExecutionException {

		String id = script.addPreloadScript("() => {{ console.log('{welcome_to_the_blank_page}') }}");

		Assert.assertTrue(id != null && !id.isEmpty());

		try (LogInspector logInspector = new LogInspector(driver)) {
			CompletableFuture<ConsoleLogEntry> future = new CompletableFuture<>();
			logInspector.onConsoleEntry(future::complete);

			driver.get(webPage);

			ConsoleLogEntry logEntry = future.get(5, TimeUnit.SECONDS);

			Assert.assertEquals(logEntry.getText(), "{welcome_to_the_blank_page}");
			Assert.assertEquals(logEntry.getLevel(), LogLevel.INFO);
		}

		script.removePreloadScript(id);

		try (LogInspector logInspector = new LogInspector(driver)) {
			CompletableFuture<ConsoleLogEntry> future = new CompletableFuture<>();
			logInspector.onConsoleEntry(future::complete);

			driver.get(webPage);

			try {
				ConsoleLogEntry logEntry = future.get(5, TimeUnit.SECONDS);
			} catch (TimeoutException e) {
				Assert.assertTrue(e instanceof TimeoutException);

			}
		}

	}

	@Test
	public void canCallFunctionInASandbox() {
		String id = driver.getWindowHandle();

		// Make changes without sandbox
		script.callFunctionInBrowsingContext(id, "() => { window.foo = 1; }", true, Optional.empty(), Optional.empty(),
				Optional.empty());

		// Check changes are not present in sandbox
		EvaluateResult resultNotInSandbox = script.callFunctionInBrowsingContext(id, "sandbox", "() => window.foo",
				true, Optional.empty(), Optional.empty(), Optional.empty());

		Assert.assertEquals(resultNotInSandbox.getResultType(), EvaluateResult.Type.SUCCESS);

		EvaluateResultSuccess result = (EvaluateResultSuccess) resultNotInSandbox;
		Assert.assertEquals(result.getResult().getType(), "undefined");

		// Make changes in sandbox
		script.callFunctionInBrowsingContext(id, "sandbox", "() => { window.foo = 2; }", true, Optional.empty(),
				Optional.empty(), Optional.empty());

		// check if changes are present in sandbox
		EvaluateResult resultInSandbox = script.callFunctionInBrowsingContext(id, "sandbox", "() => window.foo", true,
				Optional.empty(), Optional.empty(), Optional.empty());

		Assert.assertEquals(resultInSandbox.getResultType(), EvaluateResult.Type.SUCCESS);
		Assert.assertTrue(resultInSandbox.getRealmId() != null);

		EvaluateResultSuccess resultInSandboxSuccess = (EvaluateResultSuccess) resultInSandbox;

		Assert.assertEquals((Long) resultInSandboxSuccess.getResult().getValue().get(), 2L);

	}

}

