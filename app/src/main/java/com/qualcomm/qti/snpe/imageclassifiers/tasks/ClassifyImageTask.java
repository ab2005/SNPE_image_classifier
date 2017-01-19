/*
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */
package com.qualcomm.qti.snpe.imageclassifiers.tasks;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Pair;

import com.qualcomm.qti.snpe.IFloatTensor;
import com.qualcomm.qti.snpe.INeuralNetwork;
import com.qualcomm.qti.snpe.ITensor;
import com.qualcomm.qti.snpe.imageclassifiers.Model;
import com.qualcomm.qti.snpe.imageclassifiers.ModelOverviewFragmentController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ClassifyImageTask extends AsyncTask<Bitmap, Void, String[]> {

    private static final String LOG_TAG = ClassifyImageTask.class.getSimpleName();

    public static final String OUTPUT_LAYER = "prob";

    private static final int FLOAT_SIZE = 4;

    private final INeuralNetwork mNeuralNetwork;

    private final Model mModel;

    private final Bitmap mImage;

    private final ModelOverviewFragmentController mController;

    public ClassifyImageTask(ModelOverviewFragmentController controller,
                             INeuralNetwork network, Bitmap image, Model model) {
        mController = controller;
        mNeuralNetwork = network;
        mImage = image;
        mModel = model;
    }

    @Override
    protected String[] doInBackground(Bitmap... params) {
        final List<String> result = new LinkedList<>();

        final IFloatTensor tensor = new ITensor.Builder(mNeuralNetwork.getInputDimensions())
            .buildFloatTensor();
        final int[] dimensions = tensor.getDimensions();
        final FloatBuffer meanImage = loadMeanImageIfAvailable(mModel.meanImage, tensor.getSize());
        if (meanImage.remaining() != tensor.getSize()) {
            return new String[0];
        }

        final boolean isGrayScale = (dimensions[dimensions.length -1] == 1);
        final float[] floats = isGrayScale ? rgbGrayScalePixelsAsFloat(mImage, meanImage) :
                                             rgbPixelsAsFloat(mImage, meanImage);
        tensor.write(floats, 0, floats.length);

        final Map<String, IFloatTensor> outputs = mNeuralNetwork.execute(tensor);
        for (Map.Entry<String, IFloatTensor> output : outputs.entrySet()) {
            if (output.getKey().equals(OUTPUT_LAYER)) {
                for (Pair<Integer, Float> pair : topK(1, output.getValue())) {
                    result.add(mModel.labels[pair.first]);
                    result.add(String.valueOf(pair.second));
                }
            }
        }
        return result.toArray(new String[result.size()]);
    }

    @Override
    protected void onPostExecute(String[] labels) {
        super.onPostExecute(labels);
        if (labels.length > 0) {
            mController.onClassificationResult(labels);
        } else {
            mController.onClassificationFailed();
        }
    }

    private float[] rgbPixelsAsFloat(Bitmap image, FloatBuffer meanImage) {
        final float[] floats = new float[image.getWidth() * image.getHeight() * 3];
        final int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0,
            image.getWidth(), image.getHeight());
        for (int i = 0, j = 0; i < pixels.length; i++) {
            final int rgb = pixels[i];
            floats[j++] =  ((rgb)       & 0xFF) - meanImage.get();
            floats[j++] =  ((rgb >>  8) & 0xFF) - meanImage.get();
            floats[j++] =  ((rgb >> 16) & 0xFF) - meanImage.get();
        }
        return floats;
    }

    private FloatBuffer loadMeanImageIfAvailable(File meanImage, final int imageSize) {
        ByteBuffer buffer = ByteBuffer.allocate(imageSize * FLOAT_SIZE)
                .order(ByteOrder.nativeOrder());
        if (!meanImage.exists()) {
            return buffer.asFloatBuffer();
        }
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(meanImage);
            final byte[] chunk = new byte[1024];
            int read;
            while ((read = fileInputStream.read(chunk)) != -1) {
                buffer.put(chunk, 0, read);
            }
            buffer.flip();
        } catch (IOException e) {
            buffer = ByteBuffer.allocate(imageSize * FLOAT_SIZE);
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    // Do thing
                }
            }
        }
        return buffer.asFloatBuffer();
    }

    private float[] rgbGrayScalePixelsAsFloat(Bitmap image, FloatBuffer meanImage) {
        final float[] floats = new float[image.getWidth() * image.getHeight()];
        final int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0,
            image.getWidth(), image.getHeight());
        for (int i = 0; i < pixels.length; i++) {
            final int rgb = pixels[i];
            final float b = ((rgb)       & 0xFF);
            final float g = ((rgb >>  8) & 0xFF);
            final float r = ((rgb >> 16) & 0xFF);
            floats[i] = (float) (r * 0.3 + g * 0.59 + b * 0.11);
            floats[i] -= meanImage.get();
        }
        return floats;
    }

    private Pair<Integer, Float>[] topK(int k, IFloatTensor tensor) {
        final float[] array = new float[tensor.getSize()];
        tensor.read(array, 0, array.length);

        final boolean[] selected = new boolean[tensor.getSize()];
        final Pair<Integer, Float>[] topK = new Pair[k];
        int count = 0;
        while (count < k) {
            final int index = top(array, selected);
            selected[index] = true;
            topK[count] = new Pair<>(index, array[index]);
            count++;
        }
        return topK;
    }

    private int top(float[] array, boolean[] selected) {
        int index = 0;
        float max = -1.f;
        for (int i = 0; i < array.length; i++) {
            if (selected[i]) {
                continue;
            }
            if (array[i] > max) {
                max = array[i];
                index = i;
            }
        }
        return index;
    }
}
