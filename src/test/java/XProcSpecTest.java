import javax.inject.Inject;

import java.io.File;

import org.daisy.maven.xproc.xprocspec.XProcSpecRunner;

import com.google.common.collect.ImmutableMap;

import static org.daisy.pipeline.pax.exam.Options.brailleModule;
import static org.daisy.pipeline.pax.exam.Options.calabashConfigFile;
import static org.daisy.pipeline.pax.exam.Options.domTraversalPackage;
import static org.daisy.pipeline.pax.exam.Options.felixDeclarativeServices;
import static org.daisy.pipeline.pax.exam.Options.logbackClassic;
import static org.daisy.pipeline.pax.exam.Options.logbackConfigFile;
import static org.daisy.pipeline.pax.exam.Options.mavenBundle;
import static org.daisy.pipeline.pax.exam.Options.mavenBundlesWithDependencies;
import static org.daisy.pipeline.pax.exam.Options.pipelineModule;
import static org.daisy.pipeline.pax.exam.Options.thisBundle;
import static org.daisy.pipeline.pax.exam.Options.xprocspec;

import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.ops4j.pax.exam.util.PathUtils;

import static org.ops4j.pax.exam.CoreOptions.junitBundles;
import static org.ops4j.pax.exam.CoreOptions.options;
import static org.ops4j.pax.exam.CoreOptions.systemPackage;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class XProcSpecTest {
	
	@Inject
	private XProcSpecRunner xprocspecRunner;
	
	@Test
	public void runXProcSpec() throws Exception {
		File baseDir = new File(PathUtils.getBaseDir());
		boolean success = xprocspecRunner.run(ImmutableMap.of(
			                                      "test_dtbook-to-pef",
			                                      new File(baseDir, "src/test/xprocspec/test_dtbook-to-pef.xprocspec"),
			                                      "test_dtbook-to-pef_tables",
			                                      new File(baseDir, "src/test/xprocspec/test_dtbook-to-pef_tables.xprocspec"),
			                                      "test_css_translator",
			                                      new File(baseDir, "src/test/xprocspec/test_css_translator.xprocspec"),
			                                      "test_translator",
			                                      new File(baseDir, "src/test/xprocspec/test_translator.xprocspec")
			                                      ),
		                                      new File(baseDir, "target/xprocspec-reports"),
		                                      new File(baseDir, "target/surefire-reports"),
		                                      new File(baseDir, "target/xprocspec"),
		                                      null,
		                                      new XProcSpecRunner.Reporter.DefaultReporter());
		assertTrue("XProcSpec tests should run with success", success);
	}
	
	@Configuration
	public Option[] config() {
		return options(
			logbackConfigFile(),
			domTraversalPackage(),
			calabashConfigFile(),
			felixDeclarativeServices(),
			systemPackage("javax.xml.stream;version=\"1.0.1\""),
			thisBundle(),
			junitBundles(),
			mavenBundlesWithDependencies(
				brailleModule("common-utils"),
				brailleModule("css-utils"),
				brailleModule("liblouis-utils"),
				brailleModule("liblouis-tables"),
				brailleModule("liblouis-native").forThisPlatform(),
				brailleModule("libhyphen-core"),
				brailleModule("libhyphen-libreoffice-tables"),
				brailleModule("libhyphen-native").forThisPlatform(),
				brailleModule("dotify-formatter"),
				brailleModule("pef-utils"),
				brailleModule("dtbook-to-pef"),
				brailleModule("html-to-pef"),
				// logging
				logbackClassic(),
				// xprocspec
				xprocspec(),
				mavenBundle("org.daisy.maven:xproc-engine-daisy-pipeline:?"))
		);
	}
}
