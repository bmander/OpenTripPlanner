package org.opentripplanner.graph_builder.module.ned;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.Raster;
import javax.annotation.Nullable;
import org.geotools.api.coverage.PointOutsideCoverageException;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.util.CoverageUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Performs bilinear interpolation directly on raster elevation data, bypassing GeoTools'
 * synchronized Interpolator2D and per-point CRS transformation machinery.
 *
 * <p>This is suitable for WGS84 (lon-first) rasters with a simple affine grid-to-world transform,
 * covering the common case (SRTM, NED, most GeoTIFFs).
 */
class DirectElevationInterpolator {

  private static final Logger LOG = LoggerFactory.getLogger(DirectElevationInterpolator.class);

  private final double[] data;
  private final int width;
  private final int height;
  private final AffineTransform worldToGrid;
  private final double noDataValue;
  private final boolean hasNoData;

  DirectElevationInterpolator(
    double[] data,
    int width,
    int height,
    AffineTransform worldToGrid,
    double noDataValue,
    boolean hasNoData
  ) {
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
  static DirectElevationInterpolator create(
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
      return new DirectElevationInterpolator(data, width, height, worldToGrid, Double.NaN, false);
    } catch (NoninvertibleTransformException e) {
      throw new IllegalArgumentException("Non-invertible grid-to-world transform", e);
    }
  }

  /**
   * Try to create from a GeoTools GridCoverage2D. Returns null if the coverage uses a non-affine
   * transform or if data extraction fails.
   */
  @Nullable
  static DirectElevationInterpolator fromGridCoverage2D(GridCoverage2D gridCoverage) {
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
        "Created direct elevation interpolator: {}x{} pixels, noData={}",
        w,
        h,
        hasNoData ? noDataValue : "none"
      );
      return new DirectElevationInterpolator(data, w, h, worldToGrid, noDataValue, hasNoData);
    } catch (NoninvertibleTransformException e) {
      LOG.debug("Non-invertible grid transform, falling back to standard path", e);
      return null;
    } catch (Exception e) {
      LOG.warn("Failed to create in-memory elevation grid, falling back to standard path", e);
      return null;
    }
  }

  /**
   * Evaluate elevation at the given world coordinate using bilinear interpolation.
   *
   * @param x longitude
   * @param y latitude
   * @return interpolated elevation value
   * @throws PointOutsideCoverageException if the point is outside the raster bounds
   */
  double evaluate(double x, double y) throws PointOutsideCoverageException {
    // Inline affine transform to avoid Point2D allocations
    double gx =
      worldToGrid.getScaleX() * x + worldToGrid.getShearX() * y + worldToGrid.getTranslateX() - 0.5;
    double gy =
      worldToGrid.getShearY() * x + worldToGrid.getScaleY() * y + worldToGrid.getTranslateY() - 0.5;

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

    return v00 * (1 - fx) * (1 - fy) + v10 * fx * (1 - fy) + v01 * (1 - fx) * fy + v11 * fx * fy;
  }
}
