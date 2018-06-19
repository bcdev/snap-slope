package org.esa.snap.slope;

import org.esa.snap.core.dataio.ProductIO;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.CrsGeoCoding;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.GPF;
import org.esa.snap.core.util.io.FileUtils;
import org.geotools.referencing.CRS;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;

import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.fail;

public class SlopeCalculationIntegrationTest {

    private File targetDirectory;

    @Before
    public void setUp() {
        targetDirectory = new File("sao_test_out");
        if (!targetDirectory.mkdirs()) {
            fail("Unable to create test target directory");
        }
        GPF.getDefaultInstance().getOperatorSpiRegistry().addOperatorSpi(new SlopeCalculationOp.Spi());
    }

    @After
    public void tearDown() {
        GPF.getDefaultInstance().getOperatorSpiRegistry().removeOperatorSpi(new SlopeCalculationOp.Spi());
        if (targetDirectory.isDirectory()) {
            if (!FileUtils.deleteTree(targetDirectory)) {
                fail("Unable to delete test directory");
            }
        }
    }

    @Test
    public void testSlopeCalculationOp_withFloatInputs() throws FactoryException, TransformException, IOException {
        final int width = 4;
        final int height = 4;
        final Product product = new Product("SAO_Test", "sao_test", width, height);
        final CrsGeoCoding crsGeoCoding =
                new CrsGeoCoding(CRS.decode("EPSG:32650"), width, height, 699960.0, 4000020.0, 10.0, 10.0, 0.0, 0.0);
        product.setSceneGeoCoding(crsGeoCoding);
        final Band elevationBand = new Band("elevation", ProductData.TYPE_FLOAT32, width, height);
        float[] elevationData = new float[]{
                10.0f, 15.0f, 17.5f, 12.5f,
                12.0f, 14.0f, 16.0f, 13.0f,
                13.0f, 11.0f, 13.0f, 14.0f,
                14.0f, 12.0f, 14.0f, 11.0f};
        elevationBand.setDataElems(elevationData);
        product.addBand(elevationBand);

        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("elevationBandName", "elevation");
        final Product targetProduct = GPF.createProduct("SlopeCalculation", parameters, product);
        final String targetFilePath = targetDirectory.getPath() + File.separator + "sao_test.dim";
        ProductIO.writeProduct(targetProduct, targetFilePath, "BEAM-DIMAP");

        assertEquals(true, targetProduct.containsBand(SlopeCalculationOp.SLOPE_BAND_NAME));
        assertEquals(true, targetProduct.containsBand(SlopeCalculationOp.ASPECT_BAND_NAME));

        final Band slopeBand = targetProduct.getBand(SlopeCalculationOp.SLOPE_BAND_NAME);
        final Band aspectBand = targetProduct.getBand(SlopeCalculationOp.ASPECT_BAND_NAME);

        float[][] expectedSlope = new float[][]{
                {12.4894f, 18.354824f, 6.554816f, 12.680384f},
                {6.1373796f, 12.802796f, 8.248572f, 7.125016f},
                {4.044691f, 4.044691f, 6.37937f, 6.37937f},
                {6.37937f, 2.8624053f, 0.0f, 8.049467f}};

        float[][] expectedAspect = {
                {286.38953f, 266.7603f, 112.380135f, 90.0f},
                {305.53766f, 238.49573f, 172.56859f, 90.0f},
                {45.0f, 225.0f, 206.56505f, 153.43495f},
                {63.43495f, -0.f, Float.NaN, 135.0f}};
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                assertEquals(slopeBand.getSampleFloat(x, y), expectedSlope[y][x], 1e-5);
                assertEquals(aspectBand.getSampleFloat(x, y), expectedAspect[y][x], 1e-5);
            }
        }
    }

    @Test
    public void testSlopeCalculationOp_withShortInputs() throws FactoryException, TransformException, IOException {
        final int width = 4;
        final int height = 4;
        final Product product = new Product("SAO_Test", "sao_test", width, height);
        final CrsGeoCoding crsGeoCoding =
                new CrsGeoCoding(CRS.decode("EPSG:32650"), width, height, 699960.0, 4000020.0, 10.0, 10.0, 0.0, 0.0);
        product.setSceneGeoCoding(crsGeoCoding);
        final Band elevationBand = new Band("elevation", ProductData.TYPE_INT16, width, height);
        short[] elevationData = new short[]{
                10, 15, 17, 12,
                12, 14, 16, 13,
                13, 11, 13, 14,
                14, 12, 14, 11};
        elevationBand.setDataElems(elevationData);
        product.addBand(elevationBand);

        final Map<String, Object> parameters = new HashMap<>();
        final Product targetProduct = GPF.createProduct("SlopeCalculation", parameters, product);
        final String targetFilePath = targetDirectory.getPath() + File.separator + "sao_test.dim";
        ProductIO.writeProduct(targetProduct, targetFilePath, "BEAM-DIMAP");

        assertEquals(true, targetProduct.containsBand(SlopeCalculationOp.SLOPE_BAND_NAME));
        assertEquals(true, targetProduct.containsBand(SlopeCalculationOp.ASPECT_BAND_NAME));

        final Band slopeBand = targetProduct.getBand(SlopeCalculationOp.SLOPE_BAND_NAME);
        final Band aspectBand = targetProduct.getBand(SlopeCalculationOp.ASPECT_BAND_NAME);

        float[][] expectedSlope = new float[][]{
                {12.4894f, 17.36706f, 7.264626f, 12.75587f},
                {6.1373796f, 12.3342f, 7.2646269f, 7.2646269f},
                {4.044691f, 4.044691f, 6.37937f, 6.37937f},
                {6.37937f, 2.8624053f, 0.0f, 8.049467f}};

        float[][] expectedAspect = {
                {286.38953f, 267.70941f, 101.30993f, 83.65981f},
                {305.53766f, 239.036239f, 168.690078f, 78.6900711f},
                {45.0f, 225.0f, 206.56505f, 153.43495f},
                {63.43495f, -0.f, Float.NaN, 135.0f}};
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                assertEquals(slopeBand.getSampleFloat(x, y), expectedSlope[y][x], 1e-5);
                assertEquals(aspectBand.getSampleFloat(x, y), expectedAspect[y][x], 1e-5);
            }
        }
    }

    @Test
    public void testSpatialResolution() throws FactoryException, TransformException {
        final int width = 4;
        final int height = 4;
        final Product product = new Product("SAO_Test", "sao_test", width, height);
        final CrsGeoCoding crsGeoCoding =
                new CrsGeoCoding(CRS.decode("EPSG:32650"), width, height, 699960.0, 4000020.0, 10.0, 10.0, 0.0, 0.0);
        product.setSceneGeoCoding(crsGeoCoding);

        // if we have a CRS geocoding
        final MathTransform i2m = crsGeoCoding.getImageToMapTransform();
        final double spatialResolution1 = ((AffineTransform) i2m).getScaleX();

        // fallback
        double spatialResolution2 = SlopeCalculationOp.computeSpatialResolution(product, crsGeoCoding);

        assertEquals(spatialResolution1, spatialResolution2, 0.1);
    }
}
