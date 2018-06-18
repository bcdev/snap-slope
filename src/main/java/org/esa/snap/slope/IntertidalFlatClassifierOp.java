package org.esa.snap.fuzzydectree;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.*;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.OperatorSpi;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.OperatorMetadata;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.esa.snap.core.gpf.annotations.TargetProduct;
import org.esa.snap.core.util.ProductUtils;
import org.esa.snap.fuzzydectree.generated.IntertidalFlatClassifierFuz;

import java.awt.*;
import java.util.Map;

/**
 * Performs intertidal flat classification based on fuzzy decision tree.
 *
 * @author olafd
 */
@OperatorMetadata(alias = "IntertidalFlatClassifier", version = "0.9-SNAPSHOT",
        authors = "Olaf Danne, Norman Fomferra (Brockmann Consult)",
        category = "Classification",
        copyright = "Copyright (C) 2018 by Brockmann Consult",
        description = "Performs intertidal flat classification based on fuzzy decision tree.")
public class IntertidalFlatClassifierOp extends Operator {


    @SourceProduct(description = "Source product",
            label = "Classification input product")
    private Product sourceProduct;

    @TargetProduct(description = "The target product.")
    private Product targetProduct;

    private IntertidalFlatClassifierFuz intertidalFlatClassifier;

    @Override
    public void initialize() throws OperatorException {
        intertidalFlatClassifier = new IntertidalFlatClassifierFuz();
        createTargetProduct();
    }

    @Override
    public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {
        final int numSrcBands = intertidalFlatClassifier.getInputSize();
        Tile[] srcTile = new Tile[numSrcBands];
        for (int i = 0; i < numSrcBands; i++) {
            final String srcBandName = IntertidalFlatClassifierConstants.INPUT_NAMES[i][1];
            srcTile[i] = getSourceTile(sourceProduct.getBand(srcBandName), targetRectangle);
        }

        final Tile finalClassTargetTile =
                targetTiles.get(targetProduct.getBand(GenericClassifierConstants.FINAL_CLASS_BAND_NAME));
        final Tile fuzzyMaxValTargetTile =
                targetTiles.get(targetProduct.getBand(GenericClassifierConstants.FUZZY_MAX_VAL_BAND_NAME));
        final String bsumTargetBandName = intertidalFlatClassifier.getOutputNames()[13];
        final Tile bsumTargetTile = targetTiles.get(targetProduct.getBand(bsumTargetBandName));

        double[] inputs = new double[numSrcBands];
        double[] classificationOutputs = new double[intertidalFlatClassifier.getOutputSize()];  // 13 classes + bsum
        for (int y = targetRectangle.y; y < targetRectangle.y + targetRectangle.height; y++) {
            checkForCancellation();
            for (int x = targetRectangle.x; x < targetRectangle.x + targetRectangle.width; x++) {
                for (int i = 0; i < numSrcBands; i++) {
                    inputs[i] = srcTile[i].getSampleFloat(x, y);
                }
                intertidalFlatClassifier.apply(inputs, classificationOutputs);
                double outputMax = Double.MIN_VALUE;
                int maxOutputIndex = -1;
                for (int i = 0; i < classificationOutputs.length-1; i++) {    // 13 classes
                    if (classificationOutputs[i] > outputMax) {
                        outputMax = classificationOutputs[i];
                        maxOutputIndex = IntertidalFlatClassifierConstants.CLASSIF_CLASS[i];
                    }
                    final String targetBandName = intertidalFlatClassifier.getOutputNames()[i];
                    targetTiles.get(targetProduct.getBand(targetBandName)).setSample(x, y, classificationOutputs[i]);
                }

                bsumTargetTile.setSample(x, y, classificationOutputs[13]);
                finalClassTargetTile.setSample(x, y, maxOutputIndex);
                fuzzyMaxValTargetTile.setSample(x, y, outputMax);
            }
        }
    }

    private void createTargetProduct() {
        final int w = sourceProduct.getSceneRasterWidth();
        final int h = sourceProduct.getSceneRasterHeight();
        targetProduct = new Product(sourceProduct.getName(), sourceProduct.getProductType(), w, h);

        ProductUtils.copyMetadata(sourceProduct, targetProduct);
        ProductUtils.copyGeoCoding(sourceProduct, targetProduct);
        targetProduct.setStartTime(sourceProduct.getStartTime());
        targetProduct.setEndTime(sourceProduct.getEndTime());

        for (int i = 0; i < intertidalFlatClassifier.getOutputSize(); i++) {
            targetProduct.addBand(intertidalFlatClassifier.getOutputNames()[i], ProductData.TYPE_FLOAT32);
        }
        targetProduct.addBand(GenericClassifierConstants.FUZZY_MAX_VAL_BAND_NAME, ProductData.TYPE_FLOAT32);
        addFinalClassBand();
    }

    private void addFinalClassBand() {
        Band finalClassBand = targetProduct.addBand(GenericClassifierConstants.FINAL_CLASS_BAND_NAME,
                                                    ProductData.TYPE_INT8);

        final IndexCoding finalClassIndexCoding =
                new IndexCoding(GenericClassifierConstants.FINAL_CLASS_BAND_NAME);

        ColorPaletteDef.Point[] points =
                new ColorPaletteDef.Point[IntertidalFlatClassifierConstants.CLASSIF_CLASS.length];
        for (int i = 0; i < IntertidalFlatClassifierConstants.CLASSIF_CLASS.length; i++) {
            final int r = IntertidalFlatClassifierConstants.CLASSIF_CLASS_RGB[i][0];
            final int g = IntertidalFlatClassifierConstants.CLASSIF_CLASS_RGB[i][1];
            final int b = IntertidalFlatClassifierConstants.CLASSIF_CLASS_RGB[i][2];
            final Color color = new Color(r, g, b);
            final String descr = IntertidalFlatClassifierConstants.CLASSIF_CLASS_DESCR[i];
            points[i] = new ColorPaletteDef.Point(i+1, color, descr);
            finalClassIndexCoding.addIndex(descr, i+1, descr);
        }
        final ColorPaletteDef cpd = new ColorPaletteDef(points);
        final ImageInfo imageInfo = new ImageInfo(cpd);
        finalClassBand.setImageInfo(imageInfo);
        finalClassBand.setSampleCoding(finalClassIndexCoding);

        targetProduct.getIndexCodingGroup().add(finalClassIndexCoding);
    }

    public static class Spi extends OperatorSpi {

        public Spi() {
            super(IntertidalFlatClassifierOp.class);
        }
    }
}
