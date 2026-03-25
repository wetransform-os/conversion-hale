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

  @Test
  public void testHaleVersion() throws InterruptedException {
    try (GenericContainer<?> container = new GenericContainer<>(
      DockerImageName.parse("wetransform/conversion-hale:test"))
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
    runGpkgConversion("test.gpkg", "test-gpkg.xsd", file -> {
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

  private void runGpkgConversion(String gpkgClasspathResource, String xsdClasspathResource,
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
        DockerImageName.parse("wetransform/conversion-hale:test"))
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
