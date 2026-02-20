package org.opentripplanner.graph_builder.module.ned;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.geotools.api.coverage.CannotEvaluateException;
import org.geotools.api.coverage.PointOutsideCoverageException;
import org.geotools.api.coverage.SampleDimension;
import org.geotools.api.coverage.grid.GridCoverage;
import org.geotools.api.coverage.grid.GridGeometry;
import org.geotools.api.geometry.Bounds;
import org.geotools.api.geometry.Position;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.api.util.Record;
import org.geotools.api.util.RecordType;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.util.CoverageUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link GridCoverage} implementation that performs bilinear interpolation directly on raster
 * elevation data, bypassing GeoTools' synchronized {@code Interpolator2D} and per-point CRS
 * transformation machinery.
 *
 * <p>This is suitable for WGS84 (lon-first) rasters with a simple affine grid-to-world transform,
 * covering the common case (SRTM, NED, most GeoTIFFs). NoData values are handled inline,
 * subsuming the functionality of {@link NoDataGridCoverage}.
 */
class DirectBilinearGridCoverage implements GridCoverage {

  private static final Logger LOG = LoggerFactory.getLogger(DirectBilinearGridCoverage.class);

  @Nullable
  private final GridCoverage2D gridCoverage;

  private final double[] data;
  private final int width;
  private final int height;
  private final AffineTransform worldToGrid;
  private final double noDataValue;
  private final boolean hasNoData;

  private DirectBilinearGridCoverage(
    @Nullable GridCoverage2D gridCoverage,
    double[] data,
    int width,
    int height,
    AffineTransform worldToGrid,
    double noDataValue,
    boolean hasNoData
  ) {
    this.gridCoverage = gridCoverage;
    this.data = data;
    this.width = width;
    this.height = height;
    this.worldToGrid = worldToGrid;
    this.noDataValue = noDataValue;
    this.hasNoData = hasNoData;
  }

  /**
   * Create from test parameters: a simple grid defined by bounds and resolution.
   */
  static DirectBilinearGridCoverage create(
    double[] data,
    int width,
    int height,
    double originX,
    double originY,
    double pixelSizeX,
    double pixelSizeY
  ) {
    AffineTransform gridToWorld = new AffineTransform(
      pixelSizeX,
      0,
      0,
      pixelSizeY,
      originX,
      originY
    );
    try {
      AffineTransform worldToGrid = gridToWorld.createInverse();
      return new DirectBilinearGridCoverage(
        null,
        data,
        width,
        height,
        worldToGrid,
        Double.NaN,
        false
      );
    } catch (NoninvertibleTransformException e) {
      throw new IllegalArgumentException("Non-invertible grid-to-world transform", e);
    }
  }

  /**
   * Try to create from a GeoTools GridCoverage2D. Returns null if the coverage uses a non-affine
   * transform or if data extraction fails.
   */
  @Nullable
  static DirectBilinearGridCoverage fromGridCoverage2D(GridCoverage2D gridCoverage) {
    try {
      var gridGeometry = gridCoverage.getGridGeometry();
      var mathTransform = gridGeometry.getGridToCRS();

      if (!(mathTransform instanceof AffineTransform gridToWorld)) {
        LOG.debug("Grid-to-CRS is not an AffineTransform, falling back to standard path");
        return null;
      }

      AffineTransform worldToGrid = gridToWorld.createInverse();

      var renderedImage = gridCoverage.getRenderedImage();
      Raster raster = renderedImage.getData();
      int w = raster.getWidth();
      int h = raster.getHeight();
      double[] data = raster.getPixels(raster.getMinX(), raster.getMinY(), w, h, (double[]) null);

      double noDataValue = Double.NaN;
      boolean hasNoData = false;
      var noDataProp = CoverageUtilities.getNoDataProperty(gridCoverage);
      if (noDataProp != null) {
        noDataValue = noDataProp.getAsSingleValue();
        hasNoData = true;
      }

      LOG.info(
        "Created direct bilinear grid coverage: {}x{} pixels, noData={}",
        w,
        h,
        hasNoData ? noDataValue : "none"
      );
      return new DirectBilinearGridCoverage(
        gridCoverage,
        data,
        w,
        h,
        worldToGrid,
        noDataValue,
        hasNoData
      );
    } catch (NoninvertibleTransformException e) {
      LOG.debug("Non-invertible grid transform, falling back to standard path", e);
      return null;
    } catch (Exception e) {
      LOG.warn(
        "Failed to create direct bilinear grid coverage, falling back to standard path",
        e
      );
      return null;
    }
  }

  // === GridCoverage delegation ===

  @Override
  public boolean isDataEditable() {
    return requireDelegate().isDataEditable();
  }

  @Override
  public GridGeometry getGridGeometry() {
    return requireDelegate().getGridGeometry();
  }

  @Override
  public int[] getOptimalDataBlockSizes() {
    return requireDelegate().getOptimalDataBlockSizes();
  }

  @Override
  public int getNumOverviews() {
    return requireDelegate().getNumOverviews();
  }

  @Override
  public GridGeometry getOverviewGridGeometry(int index) throws IndexOutOfBoundsException {
    return requireDelegate().getOverviewGridGeometry(index);
  }

  @Override
  public GridCoverage getOverview(int index) throws IndexOutOfBoundsException {
    return requireDelegate().getOverview(index);
  }

  @Override
  public List<GridCoverage> getSources() {
    return requireDelegate().getSources();
  }

  @Override
  public RenderedImage getRenderedImage() {
    return requireDelegate().getRenderedImage();
  }

  @Override
  public CoordinateReferenceSystem getCoordinateReferenceSystem() {
    return requireDelegate().getCoordinateReferenceSystem();
  }

  @Override
  public Bounds getEnvelope() {
    return requireDelegate().getEnvelope();
  }

  @Override
  public RecordType getRangeType() {
    return requireDelegate().getRangeType();
  }

  @Override
  public Set<Record> evaluate(Position directPosition, Collection<String> collection)
    throws CannotEvaluateException {
    throw new UnsupportedOperationException("This method is unsupported");
  }

  @Override
  public Object evaluate(Position directPosition) throws CannotEvaluateException {
    throw new UnsupportedOperationException("This method is unsupported");
  }

  @Override
  public boolean[] evaluate(Position directPosition, boolean[] booleans)
    throws CannotEvaluateException, ArrayIndexOutOfBoundsException {
    throw new UnsupportedOperationException("This method is unsupported");
  }

  @Override
  public byte[] evaluate(Position directPosition, byte[] bytes)
    throws CannotEvaluateException, ArrayIndexOutOfBoundsException {
    throw new UnsupportedOperationException("This method is unsupported");
  }

  @Override
  public int[] evaluate(Position directPosition, int[] ints)
    throws CannotEvaluateException, ArrayIndexOutOfBoundsException {
    throw new UnsupportedOperationException("This method is unsupported");
  }

  @Override
  public float[] evaluate(Position directPosition, float[] floats)
    throws CannotEvaluateException, ArrayIndexOutOfBoundsException {
    throw new UnsupportedOperationException("This method is unsupported");
  }

  @Override
  public double[] evaluate(Position directPosition, double[] dest)
    throws CannotEvaluateException, ArrayIndexOutOfBoundsException {
    double x = directPosition.getOrdinate(0);
    double y = directPosition.getOrdinate(1);

    // Inline affine transform to avoid Point2D allocations
    double gx =
      worldToGrid.getScaleX() * x +
      worldToGrid.getShearX() * y +
      worldToGrid.getTranslateX() -
      0.5;
    double gy =
      worldToGrid.getShearY() * x +
      worldToGrid.getScaleY() * y +
      worldToGrid.getTranslateY() -
      0.5;

    int x0 = (int) Math.floor(gx);
    int y0 = (int) Math.floor(gy);
    int x1 = x0 + 1;
    int y1 = y0 + 1;

    if (x0 < 0 || y0 < 0 || x1 >= width || y1 >= height) {
      throw new PointOutsideCoverageException(
        "Point (" + x + ", " + y + ") outside coverage bounds"
      );
    }

    double fx = gx - x0;
    double fy = gy - y0;

    double v00 = data[y0 * width + x0];
    double v10 = data[y0 * width + x1];
    double v01 = data[y1 * width + x0];
    double v11 = data[y1 * width + x1];

    if (
      hasNoData &&
      (v00 == noDataValue || v10 == noDataValue || v01 == noDataValue || v11 == noDataValue)
    ) {
      throw new PointOutsideCoverageException("Value is NO_DATA at (" + x + ", " + y + ")");
    }

    dest[0] =
      v00 * (1 - fx) * (1 - fy) + v10 * fx * (1 - fy) + v01 * (1 - fx) * fy + v11 * fx * fy;
    return dest;
  }

  @Override
  public int getNumSampleDimensions() {
    return requireDelegate().getNumSampleDimensions();
  }

  @Override
  public SampleDimension getSampleDimension(int index) throws IndexOutOfBoundsException {
    return requireDelegate().getSampleDimension(index);
  }

  @Override
  public RenderableImage getRenderableImage(int xAxis, int yAxis)
    throws UnsupportedOperationException, IndexOutOfBoundsException {
    return requireDelegate().getRenderableImage(xAxis, yAxis);
  }

  private GridCoverage2D requireDelegate() {
    if (gridCoverage == null) {
      throw new UnsupportedOperationException(
        "No backing GridCoverage2D available (test-only instance)"
      );
    }
    return gridCoverage;
  }
}
