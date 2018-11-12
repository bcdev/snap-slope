package org.esa.snap.slope;

import com.bc.ceres.core.ProgressMonitor;
import org.apache.commons.math3.stat.StatUtils;
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
 * Computes slope and aspect for an arbitrary product which must contain an elevation band and a geocoding.
 *
 * @author TonioF, olafd
 */
@OperatorMetadata(alias = "SlopeCalculation",
        version = "0.8",
//        internal = true,
        authors = "Tonio Fincke, Olaf Danne",
        copyright = "(c) 2018 by Brockmann Consult",
        description = "Computes slope and aspect for an arbitrary product which must contain an elevation " +
                "band and a geocoding.")
public class SlopeCalculationOp extends Operator {

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
    private Band varianceBand;
    private final static String TARGET_PRODUCT_NAME = "Slope-Calculation";
    private final static String TARGET_PRODUCT_TYPE = "slope-calculation";
    final static String SLOPE_BAND_NAME = "slope";
    final static String ASPECT_BAND_NAME = "aspect";
    final static String VARIANCE_BAND_NAME = "elevation_variance";
    private final static String SLOPE_BAND_DESCRIPTION = "Slope of each pixel as angle";
    private final static String ASPECT_BAND_DESCRIPTION =
            "Aspect of each pixel as angle between North direction and steepest slope, clockwise";
    private final static String VARIANCE_BAND_DESCRIPTION = "Variance of elevation over a 3x3 pixel window";
    private final static String SLOPE_BAND_UNIT = "deg [0..90]";
    private final static String ASPECT_BAND_UNIT = "deg [0..360]";
    private final static String VARIANCE_BAND_UNIT = "m^2";

    @Override
    public void initialize() throws OperatorException {
        sourceProduct = getSourceProduct();

        // validation
        ensureSingleRasterSize(sourceProduct);
        elevationBand = sourceProduct.getBand(elevationBandName);
        if (elevationBand == null) {
            throw new OperatorException("Elevation band required to compute slope or aspect");
        }

        GeoCoding sourceGeoCoding = sourceProduct.getSceneGeoCoding();
        if (sourceGeoCoding == null) {
            throw new OperatorException("Source product has no geo-coding");
        }

        // get spatial resolution
        if (sourceGeoCoding instanceof CrsGeoCoding) {
            final MathTransform i2m = sourceGeoCoding.getImageToMapTransform();
            if (i2m instanceof AffineTransform) {
                spatialResolution = ((AffineTransform) i2m).getScaleX();
            } else {
                spatialResolution = computeSpatialResolution(sourceProduct, sourceGeoCoding);
            }
        } else {
            spatialResolution = computeSpatialResolution(sourceProduct, sourceGeoCoding);
        }

        // set up target product
        targetProduct = createTargetProduct();
        if (copyElevationBand) {
            ProductUtils.copyBand(elevationBandName, sourceProduct, targetProduct, true);
        }
        slopeBand = createBand(SLOPE_BAND_NAME, SLOPE_BAND_DESCRIPTION, SLOPE_BAND_UNIT);
        aspectBand = createBand(ASPECT_BAND_NAME, ASPECT_BAND_DESCRIPTION, ASPECT_BAND_UNIT);
        varianceBand = createBand(VARIANCE_BAND_NAME, VARIANCE_BAND_DESCRIPTION, VARIANCE_BAND_UNIT);
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
        final Tile varianceTile = targetTiles.get(varianceBand);
        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            sourceIndex++;
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                if (x == 10750 && y == 650) {
                    System.out.println("x = " + x);
                }
                final float[] slopeAspectVariance = computeSlopeAspectVariance(elevationData, sourceIndex,
                                                                          spatialResolution, sourceRectangle.width);
                slopeTile.setSample(x, y, slopeAspectVariance[0] * MathUtils.RTOD);
                aspectTile.setSample(x, y, slopeAspectVariance[1] * MathUtils.RTOD);
                varianceTile.setSample(x, y, slopeAspectVariance[2]);
                sourceIndex++;
            }
            sourceIndex++;
        }
    }

    /**
     * Computes product spatial resolution from great circle distances at the product edges.
     * To be used as fallback if we have no CRS geocoding.
     *
     * @param sourceProduct   - the source product
     * @param sourceGeoCoding - the source scene geocoding
     * @return spatial resolution in metres
     */
    static double computeSpatialResolution(Product sourceProduct, GeoCoding sourceGeoCoding) {
        final int width = sourceProduct.getSceneRasterWidth();
        final int height = sourceProduct.getSceneRasterHeight();

        final GeoPos leftPos = sourceGeoCoding.getGeoPos(new PixelPos(0, height / 2), null);
        final GeoPos rightPos = sourceGeoCoding.getGeoPos(new PixelPos(width - 1, height / 2), null);
        final double distance1 =
                computeDistance(leftPos.getLat(), leftPos.getLon(), rightPos.getLat(), rightPos.getLon());

        final GeoPos upperPos = sourceGeoCoding.getGeoPos(new PixelPos(width / 2, 0), null);
        final GeoPos lowerPos = sourceGeoCoding.getGeoPos(new PixelPos(width / 2, height - 1), null);
        final double distance2 =
                computeDistance(upperPos.getLat(), upperPos.getLon(), lowerPos.getLat(), lowerPos.getLon());

        final double distance = 0.5 * (distance1 + distance2);

        return 1000.0 * distance / (width - 1);
    }

    /**
     * Calculate the great-circle distance between two points on Earth using Haversine formula.
     * See e.g. https://www.movable-type.co.uk/scripts/latlong.html
     *
     * @param lat1 - first point latitude
     * @param lon1 - first point longitude
     * @param lat2 - second point latitude
     * @param lon2 - second point longitude
     * @return distance in km
     */
    static double computeDistance(double lat1, double lon1, double lat2, double lon2) {
        final double deltaLatR = (lat1 - lat2) * MathUtils.DTOR;
        final double deltaLonR = (lon1 - lon2) * MathUtils.DTOR;

        final double a = Math.sin(deltaLatR / 2.0) * Math.sin(deltaLatR / 2.0) +
                Math.cos(lat1 * MathUtils.DTOR) * Math.cos(lat2 * MathUtils.DTOR) *
                        Math.sin(deltaLonR / 2.0) * Math.sin(deltaLonR / 2.0);

        final double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        final double R = 6371.0;  // Earth radius in km

        return R * c;
    }

    /* package local for testing */
    static float[] computeSlopeAspectVariance(float[] elevationData, int sourceIndex, double spatialResolution,
                                              int sourceWidth) {

        double[] elev = new double[9];
        elev[0] = elevationData[sourceIndex - sourceWidth - 1];
        elev[1] = elevationData[sourceIndex - sourceWidth];
        elev[2] = elevationData[sourceIndex - sourceWidth + 1];
        elev[3] = elevationData[sourceIndex - 1];
        elev[4] = elevationData[sourceIndex];
        elev[5] = elevationData[sourceIndex + 1];
        elev[6] = elevationData[sourceIndex + sourceWidth - 1];
        elev[7] = elevationData[sourceIndex + sourceWidth];
        elev[8] = elevationData[sourceIndex + sourceWidth + 1];

        double b = (elev[2] + 2 * elev[5] + elev[8] - elev[0] - 2 * elev[3] - elev[6]) / 8f;
        double c = (elev[0] + 2 * elev[1] + elev[2] - elev[6] - 2 * elev[7] - elev[8]) / 8f;
        float slope = (float) Math.atan(Math.sqrt(Math.pow(b / spatialResolution, 2) +
                                                          Math.pow(c / spatialResolution, 2)));
        float aspect = (float) Math.atan2(-b, -c);
        if (aspect < 0.0f) {
//             map from [-180, 180] into [0, 360], see e.g. https://www.e-education.psu.edu/geog480/node/490
            aspect += 2.0 * Math.PI;
        }
        if (slope <= 0.0) {
            aspect = Float.NaN;
        }

        final float variance = (float) StatUtils.variance(elev);

        return new float[]{slope, aspect, variance};
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
            super(SlopeCalculationOp.class);
        }
    }
}
