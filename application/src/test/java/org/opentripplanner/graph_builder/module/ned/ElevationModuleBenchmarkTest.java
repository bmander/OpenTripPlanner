package org.opentripplanner.graph_builder.module.ned;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import org.geotools.api.coverage.Coverage;
import org.geotools.geometry.Position2D;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentripplanner.graph_builder.services.ned.ElevationGridCoverageFactory;
import org.opentripplanner.routing.graph.Graph;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.street.geometry.SphericalDistanceLibrary;
import org.opentripplanner.street.model.StreetModelForTest;
import org.opentripplanner.street.model.StreetTraversalPermission;
import org.opentripplanner.street.model.edge.StreetEdge;
import org.opentripplanner.street.model.edge.StreetEdgeBuilder;
import org.opentripplanner.street.model.vertex.IntersectionVertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Benchmark test for ElevationModule to measure edges/second throughput.
 *
 * <p>Run with: {@code mvn test -Dtest=ElevationModuleBenchmarkTest -pl application -Dps
 * -Dgroups=performance}
 */
@Tag("performance")
class ElevationModuleBenchmarkTest {

  private static final Logger LOG = LoggerFactory.getLogger(ElevationModuleBenchmarkTest.class);

  private static final double BASE_LAT = 45.5;
  private static final double BASE_LON = -122.6;
  // ~111m at this latitude
  private static final double GRID_SPACING = 0.001;
  // 101x101 = 10201 vertices, ~20000 edges
  private static final int GRID_SIZE = 101;
  private static final int WARMUP_ITERATIONS = 3;
  private static final int MEASURED_ITERATIONS = 5;

  @Test
  void benchmarkWithMockCoverage() {
    Coverage coverage = mock(Coverage.class);
    doAnswer(invocation -> {
      Position2D pos = invocation.getArgument(0);
      double[] result = invocation.getArgument(1);
      result[0] = pos.getY() * 100;
      return result;
    })
      .when(coverage)
      .evaluate(any(Position2D.class), any(double[].class));

    ElevationGridCoverageFactory factory = mock(ElevationGridCoverageFactory.class);
    when(factory.getGridCoverage()).thenReturn(coverage);
    when(factory.elevationUnitMultiplier()).thenReturn(1.0);

    GraphAndEdgeCount graphData = buildTestGraph();
    double median = runBenchmark("Mock Coverage", graphData, factory, null);

    assertTrue(median > 1000, "Expected at least 1000 edges/sec, got " + median);
  }

  @Test
  void benchmarkWithDirectInterpolator() {
    Coverage coverage = mock(Coverage.class);
    doAnswer(invocation -> {
      double[] result = invocation.getArgument(1);
      result[0] = 0;
      return result;
    })
      .when(coverage)
      .evaluate(any(Position2D.class), any(double[].class));

    ElevationGridCoverageFactory factory = mock(ElevationGridCoverageFactory.class);
    when(factory.getGridCoverage()).thenReturn(coverage);
    when(factory.elevationUnitMultiplier()).thenReturn(1.0);

    DirectElevationInterpolator inMemory = buildSyntheticDem();
    GraphAndEdgeCount graphData = buildTestGraph();
    double median = runBenchmark("Direct Interpolator", graphData, factory, inMemory);

    assertTrue(median > 1000, "Expected at least 1000 edges/sec, got " + median);
  }

  private double runBenchmark(
    String label,
    GraphAndEdgeCount graphData,
    ElevationGridCoverageFactory factory,
    DirectElevationInterpolator inMemory
  ) {
    Graph graph = graphData.graph;
    int edgeCount = graphData.edgeCount;
    double[] rates = new double[MEASURED_ITERATIONS];

    LOG.info(
      "=== {} Benchmark: {} vertices, {} edges ===",
      label,
      graph.getVertices().size(),
      edgeCount
    );

    for (int i = 0; i < WARMUP_ITERATIONS + MEASURED_ITERATIONS; i++) {
      for (StreetEdge edge : graph.getStreetEdges()) {
        edge.setElevationExtension(null);
      }

      ElevationModule module = new ElevationModule(factory, graph);
      if (inMemory != null) {
        module.setDirectInterpolator(inMemory);
      }

      long start = System.nanoTime();
      module.buildGraph();
      long elapsed = System.nanoTime() - start;

      double seconds = elapsed / 1_000_000_000.0;
      double edgesPerSecond = edgeCount / seconds;

      if (i < WARMUP_ITERATIONS) {
        LOG.info("  Warmup {}: {} edges/sec ({} s)", i + 1, (int) edgesPerSecond, seconds);
      } else {
        int mi = i - WARMUP_ITERATIONS;
        rates[mi] = edgesPerSecond;
        LOG.info("  Measured {}: {} edges/sec ({} s)", mi + 1, (int) edgesPerSecond, seconds);
      }
    }

    Arrays.sort(rates);
    double median = rates[MEASURED_ITERATIONS / 2];
    LOG.info("BENCHMARK RESULT [{}] - Median: {} edges/sec", label, (int) median);
    return median;
  }

  private DirectElevationInterpolator buildSyntheticDem() {
    // Create a DEM raster covering the test area with elevation = lat * 100
    // Grid extent: slightly larger than the vertex grid to avoid boundary issues
    double margin = GRID_SPACING * 2;
    double minLon = BASE_LON - margin;
    double minLat = BASE_LAT - margin;
    double maxLon = BASE_LON + (GRID_SIZE - 1) * GRID_SPACING + margin;
    double maxLat = BASE_LAT + (GRID_SIZE - 1) * GRID_SPACING + margin;

    // Use a resolution finer than the grid spacing
    double pixelSize = GRID_SPACING / 2;
    int demWidth = (int) Math.ceil((maxLon - minLon) / pixelSize) + 1;
    int demHeight = (int) Math.ceil((maxLat - minLat) / pixelSize) + 1;

    double[] data = new double[demWidth * demHeight];
    for (int row = 0; row < demHeight; row++) {
      double lat = minLat + row * pixelSize;
      for (int col = 0; col < demWidth; col++) {
        data[row * demWidth + col] = lat * 100;
      }
    }

    return DirectElevationInterpolator.create(
      data,
      demWidth,
      demHeight,
      minLon,
      minLat,
      pixelSize,
      pixelSize
    );
  }

  private GraphAndEdgeCount buildTestGraph() {
    Graph graph = new Graph();
    IntersectionVertex[][] vertices = new IntersectionVertex[GRID_SIZE][GRID_SIZE];

    for (int row = 0; row < GRID_SIZE; row++) {
      for (int col = 0; col < GRID_SIZE; col++) {
        double lat = BASE_LAT + row * GRID_SPACING;
        double lon = BASE_LON + col * GRID_SPACING;
        String label = "v_" + row + "_" + col;
        IntersectionVertex v = StreetModelForTest.intersectionVertex(label, lat, lon);
        vertices[row][col] = v;
        graph.addVertex(v);
      }
    }

    int edgeCount = 0;
    for (int row = 0; row < GRID_SIZE; row++) {
      for (int col = 0; col < GRID_SIZE; col++) {
        if (col < GRID_SIZE - 1) {
          createEdge(vertices[row][col], vertices[row][col + 1]);
          edgeCount++;
        }
        if (row < GRID_SIZE - 1) {
          createEdge(vertices[row][col], vertices[row + 1][col]);
          edgeCount++;
        }
      }
    }

    return new GraphAndEdgeCount(graph, edgeCount);
  }

  private StreetEdge createEdge(IntersectionVertex from, IntersectionVertex to) {
    var coords = new org.locationtech.jts.geom.Coordinate[] {
      from.getCoordinate(),
      to.getCoordinate(),
    };
    var geom = GeometryUtils.getGeometryFactory().createLineString(coords);
    double length = SphericalDistanceLibrary.distance(from.getCoordinate(), to.getCoordinate());
    return new StreetEdgeBuilder<>()
      .withFromVertex(from)
      .withToVertex(to)
      .withGeometry(geom)
      .withName("edge")
      .withMeterLength(length)
      .withPermission(StreetTraversalPermission.ALL)
      .withBack(false)
      .buildAndConnect();
  }

  private record GraphAndEdgeCount(Graph graph, int edgeCount) {}
}
