package org.esa.snap.slope;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SlopeCalculationOpTest {

    @Test
    public void testComputeSlopeAspectVariance() {
        double[] altitude = new double[]{
                10.0f, 10.0f, 15.0f,
                10.0f, 10.0f, 15.0f,
                12.0f, 12.0f, 14.0f};
        final float[] slopeAndAspect_7 = SlopeCalculationOp.computeSlopeAspectVariance(altitude, 10);
        double[] altitude2 = new double[]{
                14.0f, 12.0f, 14.0f,
                14.0f, 12.0f, 14.0f,
                14.0f, 12.0f, 14.0f};
        final float[] slopeAndAspect_27 = SlopeCalculationOp.computeSlopeAspectVariance(altitude2, 10);

        assertEquals(slopeAndAspect_7[0], 0.21798114, 1e-7);
        assertEquals(slopeAndAspect_27[0], 0.0, 1e-7);

        assertEquals(slopeAndAspect_7[1], 4.9984403, 1e-7);
        assertTrue(Double.isNaN(slopeAndAspect_27[1]));

        assertEquals(slopeAndAspect_7[2], 4.75, 1e-6);
    }


    @Test
    public void testComputeOrientation() {
        float[] latitudes = new float[]{50.0f, 50.01f, 50.02f, 50.03f,
                50.1f, 50.11f, 50.12f, 50.13f,
                50.2f, 50.21f, 50.22f, 50.23f,
                50.3f, 50.31f, 50.32f, 50.33f};
        float[] longitudes = new float[]{10.0f, 10.2f, 10.4f, 10.6f,
                10.01f, 10.21f, 10.41f, 10.61f,
                10.02f, 10.22f, 10.42f, 10.62f,
                10.03f, 10.23f, 10.43f, 10.63f};
        final float orientation_1 = SlopeCalculationOp.computeOrientation(latitudes, longitudes, 1);
        final float orientation_2 = SlopeCalculationOp.computeOrientation(latitudes, longitudes, 2);
        final float orientation_5 = SlopeCalculationOp.computeOrientation(latitudes, longitudes, 5);
        final float orientation_6 = SlopeCalculationOp.computeOrientation(latitudes, longitudes, 6);
        final float orientation_9 = SlopeCalculationOp.computeOrientation(latitudes, longitudes, 9);
        final float orientation_10 = SlopeCalculationOp.computeOrientation(latitudes, longitudes, 10);
        final float orientation_13 = SlopeCalculationOp.computeOrientation(latitudes, longitudes, 13);
        final float orientation_14 = SlopeCalculationOp.computeOrientation(latitudes, longitudes, 14);
        assertEquals(-0.07763171, orientation_1, 1e-8);
        assertEquals(-0.07764761, orientation_2, 1e-8);
        assertEquals(-0.07779299, orientation_5, 1e-8);
        assertEquals(-0.07780917, orientation_6, 1e-8);
        assertEquals(-0.07795518, orientation_9, 1e-8);
        assertEquals(-0.07797144, orientation_10, 1e-8);
        assertEquals(-0.07811809, orientation_13, 1e-8);
        assertEquals(-0.07813445, orientation_14, 1e-8);
    }

    @Test
    public void testComputeDistance() {
        // numbers from https://www.movable-type.co.uk/scripts/latlong.html
        double lat1 = 50.0 + 3.0/60.0 + 59.0/3600.0;
        double lon1 = -5.0 - 42.0/60.0 - 53.0/3600.0;
        double lat2 = 58.0 + 38.0/60.0 + 38.0/3600.0;
        double lon2 = -3.0 - 4.0/60.0 - 12.0/3600.0;

        double distance = SlopeCalculationOp.computeDistance(lat1, lon1, lat2, lon2);
        assertEquals(968.9, distance, 0.1);
    }
}
