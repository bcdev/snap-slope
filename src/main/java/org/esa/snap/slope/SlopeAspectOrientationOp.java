package org.esa.snap.slope;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.Parameter;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.core.util.math.MathUtils;
import org.opengis.referencing.operation.MathTransform;

import javax.media.jai.BorderExtender;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.Map;

/**
 * Computes Slope and aspect for an arbitrary product with elevation data and a CRS geocoding.
 *
 * @author TonioF, olafd
 */
@OperatorMetadata(alias = "SlopeAspectOrientation",
        version = "1.0",
        internal = true,
        authors = "Tonio Fincke, Olaf Danne",
        copyright = "(c) 2018 by Brockmann Consult",
        description = "Computes Slope and aspect for an arbitrary product with elevation data and a CRS geocoding.")
public class SlopeAspectOrientationOp extends Operator {

    @Parameter(defaultValue = "elevation",
            description = "Name of elevation band in source product.")
    private String elevationBandName;

    @Parameter(defaultValue = "false",
            description = "If selected, elevation source band will be written to target product.")
    private boolean copyElevationBand;

    @SourceProduct(description = "Source product containing elevation band.",
            label = "Elevation product")
    private Product sourceProduct;

    @TargetProduct
    private Product targetProduct;

    private double spatialResolution;

    private Band elevationBand;
    private Band slopeBand;
    private Band aspectBand;
    private final static String TARGET_PRODUCT_NAME = "Slope-Aspect-Orientation";
    private final static String TARGET_PRODUCT_TYPE = "slope-aspect-orientation";
    final static String SLOPE_BAND_NAME = "slope";
    final static String ASPECT_BAND_NAME = "aspect";
    private final static String SLOPE_BAND_DESCRIPTION = "Slope of each pixel as angle";
    private final static String ASPECT_BAND_DESCRIPTION =
            "Aspect of each pixel as angle between raster -Y direction and steepest slope, clockwise";
    private final static String SLOPE_BAND_UNIT = "deg [0..90]";
    private final static String ASPECT_BAND_UNIT = "deg [0..360]";

    @Override
    public void initialize() throws OperatorException {
        sourceProduct = getSourceProduct();
        ensureSingleRasterSize(sourceProduct);
        GeoCoding sourceGeoCoding = sourceProduct.getSceneGeoCoding();
        if (sourceGeoCoding == null) {
            throw new OperatorException("Source product has no geo-coding");
        }
        if (sourceGeoCoding instanceof CrsGeoCoding) {
            final MathTransform i2m = sourceGeoCoding.getImageToMapTransform();
            if (i2m instanceof AffineTransform) {
                spatialResolution = ((AffineTransform) i2m).getScaleX();
            } else {
                throw new OperatorException("Could not retrieve spatial resolution from Geo-coding");
            }
        } else {
            throw new OperatorException("Could not retrieve spatial resolution from Geo-coding");
        }
        elevationBand = sourceProduct.getBand(elevationBandName);
        if (elevationBand == null) {
            throw new OperatorException("Elevation band required to compute slope or aspect");
        }
        targetProduct = createTargetProduct();
        if (copyElevationBand) {
            ProductUtils.copyBand(elevationBandName, sourceProduct, targetProduct, true);
        }
        slopeBand = createBand(SLOPE_BAND_NAME, SLOPE_BAND_DESCRIPTION, SLOPE_BAND_UNIT);
        aspectBand = createBand(ASPECT_BAND_NAME, ASPECT_BAND_DESCRIPTION, ASPECT_BAND_UNIT);
        setTargetProduct(targetProduct);
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm)
            throws OperatorException {
        final Rectangle sourceRectangle = getSourceRectangle(targetRectangle);
        final BorderExtender borderExtender = BorderExtender.createInstance(BorderExtender.BORDER_COPY);
        final Tile elevationTile = getSourceTile(elevationBand, sourceRectangle, borderExtender);
        final float[] elevationData = getAsFloatArray(elevationTile);
        float[] sourceLatitudes = new float[(int) (sourceRectangle.getWidth() * sourceRectangle.getHeight())];
        float[] sourceLongitudes = new float[(int) (sourceRectangle.getWidth() * sourceRectangle.getHeight())];
        ((CrsGeoCoding) getSourceProduct().getSceneGeoCoding()).getPixels((int) sourceRectangle.getMinX(),
                                                                          (int) sourceRectangle.getMinY(),
                                                                          (int) sourceRectangle.getWidth(),
                                                                          (int) sourceRectangle.getHeight(),
                                                                          sourceLatitudes,
                                                                          sourceLongitudes);
        int sourceIndex = sourceRectangle.width;
        final Tile slopeTile = targetTiles.get(slopeBand);
        final Tile aspectTile = targetTiles.get(aspectBand);
        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            sourceIndex++;
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                final float[] slopeAndAspect = computeSlopeAndAspect(elevationData, sourceIndex,
                                                                     spatialResolution, sourceRectangle.width);
                slopeTile.setSample(x, y, slopeAndAspect[0] * MathUtils.RTOD);
                aspectTile.setSample(x, y, slopeAndAspect[1] * MathUtils.RTOD);
                sourceIndex++;
            }
            sourceIndex++;
        }
    }

    /* package local for testing */
    static float[] computeSlopeAndAspect(float[] elevationData, int sourceIndex, double spatialResolution,
                                         int sourceWidth) {

        float elevA1 = elevationData[sourceIndex - sourceWidth - 1];
        float elevA2 = elevationData[sourceIndex - sourceWidth];
        float elevA3 = elevationData[sourceIndex - sourceWidth + 1];
        float elevA4 = elevationData[sourceIndex - 1];
        float elevA6 = elevationData[sourceIndex + 1];
        float elevA7 = elevationData[sourceIndex + sourceWidth - 1];
        float elevA8 = elevationData[sourceIndex + sourceWidth];
        float elevA9 = elevationData[sourceIndex + sourceWidth + 1];

        float b = (elevA3 + 2 * elevA6 + elevA9 - elevA1 - 2 * elevA4 - elevA7) / 8f;
        float c = (elevA1 + 2 * elevA2 + elevA3 - elevA7 - 2 * elevA8 - elevA9) / 8f;
        float slope = (float) Math.atan(Math.sqrt(Math.pow(b / spatialResolution, 2) +
                                                          Math.pow(c / spatialResolution, 2)));
        float aspect = (float) Math.atan2(-b, -c);
        if (aspect < 0.0f) {
//             map from [-180, 180] into [0, 360], see e.g. https://www.e-education.psu.edu/geog480/node/490
            aspect += 2.0*Math.PI;
        }
        return new float[]{slope, aspect};
    }

    /* package local for testing */
    static float computeOrientation(float[] latData, float[] lonData, int sourceIndex) {
        float lat1 = latData[sourceIndex - 1];
        float lat2 = latData[sourceIndex + 1];
        float lon1 = lonData[sourceIndex - 1];
        float lon2 = lonData[sourceIndex + 1];
        return (float) Math.atan2(-(lat2 - lat1), (lon2 - lon1) * Math.cos(Math.toRadians(lat1)));
    }

    private static Rectangle getSourceRectangle(Rectangle targetRectangle) {
        return new Rectangle(targetRectangle.x - 1, targetRectangle.y - 1,
                             targetRectangle.width + 2, targetRectangle.height + 2);
    }

    private Product createTargetProduct() {
        final int sceneWidth = sourceProduct.getSceneRasterWidth();
        final int sceneHeight = sourceProduct.getSceneRasterHeight();
        Product targetProduct = new Product(TARGET_PRODUCT_NAME, TARGET_PRODUCT_TYPE, sceneWidth, sceneHeight);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        return targetProduct;
    }

    private static float[] getAsFloatArray(Tile tile) {
        ProductData dataBuffer = tile.getDataBuffer();
        float[] dataArrFloat = new float[dataBuffer.getNumElems()];
        switch (dataBuffer.getType()) {
            case ProductData.TYPE_INT16:
                for (int i = 0; i < dataBuffer.getNumElems(); i++) {
                    dataArrFloat[i] = (float) tile.getDataBufferShort()[i];
                }
                break;
            case ProductData.TYPE_INT32:
                for (int i = 0; i < dataBuffer.getNumElems(); i++) {
                    dataArrFloat[i] = (float) tile.getDataBufferInt()[i];
                }
                break;
            case ProductData.TYPE_FLOAT32:
                for (int i = 0; i < dataBuffer.getNumElems(); i++) {
                    dataArrFloat[i] = tile.getDataBufferFloat()[i];
                }
                break;
            case ProductData.TYPE_FLOAT64:
                for (int i = 0; i < dataBuffer.getNumElems(); i++) {
                    dataArrFloat[i] = (float) tile.getDataBufferDouble()[i];
                }
                break;
            default:
                throw new OperatorException("Source product data type '" + dataBuffer.getTypeString() +
                                                    "' not supported.");
        }
        return dataArrFloat;
    }

    private Band createBand(String bandName, String description, String unit) {
        Band band = targetProduct.addBand(bandName, ProductData.TYPE_FLOAT32);
        band.setDescription(description);
        band.setUnit(unit);
        band.setNoDataValue(-9999.);
        band.setNoDataValueUsed(true);
        return band;
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(org.esa.snap.slope.SlopeAspectOrientationOp.class);
        }
    }
}
