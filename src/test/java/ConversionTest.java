/*
 * Copyright (c) 2026 wetransform GmbH
 * All rights reserved.
 */
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Consumer;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.shaded.org.apache.commons.io.FileUtils;
import org.testcontainers.utility.DockerImageName;
import org.w3c.dom.NodeList;

public class ConversionTest {

  private static final Logger log = LoggerFactory.getLogger(ConversionTest.class);

  /** Default image, based on hale-cli, including OrientDB. */
  private static final String IMAGE = "wetransform/conversion-hale:test";

  /** Alternative image variant with the OrientDB dependency removed. */
  private static final String IMAGE_NO_ORIENTDB = "wetransform/conversion-hale:test-no-orientdb";

  @Test
  public void testHaleVersion() throws InterruptedException {
    try (GenericContainer<?> container = new GenericContainer<>(
      DockerImageName.parse(IMAGE))
      .withCommand("/hale/bin/hale", "version")
      .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("hale"))) {

      container.start();

      // wait for container to finish
      while (container.isRunning()) {
        Thread.sleep(1000);
      }

      // check exit code
      var state = container.getContainerInfo().getState();
      assertEquals(0, state.getExitCodeLong());
    }
  }

  @Test
  public void testConvertGpkgToGml() throws UnsupportedOperationException, IOException, InterruptedException {
    runGpkgConversion(IMAGE, "test.gpkg", "test-gpkg.xsd", file -> {
      verifyGmlFeatureCount(file, 3);
    });
  }

  @Test
  public void testNoOrientDbVariantExcludesOrientDb() throws InterruptedException {
    // The vulnerable OrientDB dependency and its graph stack (orientdb-core, orientdb-nativeos,
    // orient-commons, and the *-orientDB* gremlin/pipes/blueprints jars) must not be present
    // ANYWHERE in the image filesystem - vulnerability scanners (grype/trivy) inventory files on
    // disk regardless of the classpath, and the base image ships these jars in more than one
    // directory (/hale/lib and /app/libs). The hale wrapper bundles
    // (eu.esdihumboldt.hale.common.*.orient-*.jar) are deliberately not matched here - they remain.
    var checkCmd = "matches=$(find / -type f -name '*.jar' 2>/dev/null "
      + "| grep -iE 'orientdb-|orient-commons|orientDB'); echo \"$matches\"; [ -z \"$matches\" ]";

    try (GenericContainer<?> container = new GenericContainer<>(
      DockerImageName.parse(IMAGE_NO_ORIENTDB))
      .withCommand("sh", "-c", checkCmd)
      .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("hale"))) {

      container.start();

      // wait for container to finish
      while (container.isRunning()) {
        Thread.sleep(1000);
      }

      // exit code 0 means grep found no matching OrientDB jars
      var state = container.getContainerInfo().getState();
      assertEquals(0, state.getExitCodeLong(), "OrientDB jars should be absent from the no-orientdb image");
    }
  }

  @Test
  public void testConvertGpkgToGmlNoOrientDb()
    throws UnsupportedOperationException, IOException, InterruptedException {
    // The GML rewrite use case must still work without OrientDB as temporary storage.
    runGpkgConversion(IMAGE_NO_ORIENTDB, "test.gpkg", "test-gpkg.xsd", file -> {
      verifyGmlFeatureCount(file, 3);
    });
  }

  private void verifyGmlFeatureCount(File gmlFile, int expectedFeatures) {
    assertTrue(gmlFile.exists());
    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      var doc = factory.newDocumentBuilder().parse(gmlFile);
      NodeList members = doc.getElementsByTagNameNS("http://www.opengis.net/gml/3.2", "featureMember");
      assertEquals(expectedFeatures, members.getLength());
    } catch (Exception e) {
      throw new AssertionError("Failed to parse GML output: " + e.getMessage(), e);
    }
  }

  private void runGpkgConversion(String image, String gpkgClasspathResource, String xsdClasspathResource,
    Consumer<File> verify) throws UnsupportedOperationException, IOException, InterruptedException {
    var targetDir = "/opt/data";
    var dlSource = targetDir + "/source.gpkg";
    var targetFile = "target.gml";

    try (Network network = Network.newNetwork();
      GenericContainer<?> nginx = new GenericContainer<>(DockerImageName.parse("nginx:latest"))
        .withClasspathResourceMapping(gpkgClasspathResource, "/usr/share/nginx/html/" + gpkgClasspathResource,
          BindMode.READ_ONLY)
        .withClasspathResourceMapping(xsdClasspathResource, "/usr/share/nginx/html/" + xsdClasspathResource,
          BindMode.READ_ONLY)
        .withNetwork(network)
        .withNetworkAliases("nginx")) {

      nginx.start();

      var gpkgUrl = "http://nginx/" + gpkgClasspathResource;
      var schemaUrl = "http://nginx/" + xsdClasspathResource;

      // build sh -c command: curl download then hale data rewrite
      var haleArgs = String.join(" ",
        "\"" + dlSource + "\"",
        "--data-reader", "eu.esdihumboldt.hale.io.geopackage.instance.reader",
        "--schema", "\"" + schemaUrl + "\"",
        "--schema-reader", "eu.esdihumboldt.hale.io.xsd.reader",
        "--target", "\"" + targetDir + "/" + targetFile + "\"",
        "--target-writer", "eu.esdihumboldt.hale.io.gml.writer",
        "--target-setting", "xml.pretty=true");
      var cmd = "curl -L -o \"" + dlSource + "\" \"" + gpkgUrl + "\" && "
        + "/hale/bin/hale data rewrite --data " + haleArgs;

      try (GenericContainer<?> conversionContainer = new GenericContainer<>(
        DockerImageName.parse(image))
        .withNetwork(network)
        .withCommand("sh", "-c", cmd)
        .withLogConsumer(new Slf4jLogConsumer(log).withPrefix("hale"))) {

        conversionContainer.start();

        // wait for container to finish
        while (conversionContainer.isRunning()) {
          Thread.sleep(1000);
        }

        // check exit code
        var state = conversionContainer.getContainerInfo().getState();
        assertEquals(0, state.getExitCodeLong());

        // copy output file and verify
        var tmpDir = Files.createTempDirectory("conversion-test");
        try {
          var outFile = new File(tmpDir.toFile(), targetFile);
          conversionContainer.copyFileFromContainer(targetDir + "/" + targetFile, outFile.getAbsolutePath());

          if (verify != null) {
            verify.accept(outFile);
          } else {
            assertTrue(outFile.exists());
          }
        } finally {
          FileUtils.deleteDirectory(tmpDir.toFile());
        }
      }
    }
  }

}
