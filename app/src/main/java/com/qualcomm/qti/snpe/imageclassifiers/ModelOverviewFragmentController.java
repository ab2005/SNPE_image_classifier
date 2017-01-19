/*
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */
package com.qualcomm.qti.snpe.imageclassifiers;

import android.graphics.Bitmap;
import android.os.AsyncTask;

import com.qualcomm.qti.snpe.INeuralNetwork;
import com.qualcomm.qti.snpe.imageclassifiers.tasks.ClassifyImageTask;
import com.qualcomm.qti.snpe.imageclassifiers.tasks.LoadImageTask;
import com.qualcomm.qti.snpe.imageclassifiers.tasks.LoadNetworkTask;

import java.io.File;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

public class ModelOverviewFragmentController extends AbstractViewController<ModelOverviewFragment> {

    private final Map<String, SoftReference<Bitmap>> mBitmapCache;

    private final Model mModel;

    private INeuralNetwork mNeuralNetwork;

    public ModelOverviewFragmentController(Model model) {
        mBitmapCache = new HashMap<>();
        mModel = model;
    }

    @Override
    protected void onViewAttached(ModelOverviewFragment view) {
        view.setLoadingVisible(true);
        view.setModelName(mModel.name);
        loadImageSamples(view);
        final LoadNetworkTask task = new LoadNetworkTask(this, mModel);
        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void loadImageSamples(ModelOverviewFragment view) {
        for (int i = 0; i < mModel.jpgImages.length; i++) {
            final File jpeg = mModel.jpgImages[i];
            final Bitmap cached = getCachedBitmap(jpeg);
            if (cached != null) {
                view.addSampleBitmap(cached);
            } else {
                final LoadImageTask task = new LoadImageTask(this, jpeg);
                task.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
            }
        }
    }

    private Bitmap getCachedBitmap(File jpeg) {
        final SoftReference<Bitmap> reference = mBitmapCache.get(jpeg.getAbsolutePath());
        if (reference != null) {
            final Bitmap bitmap = reference.get();
            if (bitmap != null) {
                return bitmap;
            }
        }
        return null;
    }

    @Override
    protected void onViewDetached(ModelOverviewFragment view) {
        if (mNeuralNetwork != null) {
            mNeuralNetwork.release();
            mNeuralNetwork = null;
        }
    }

    public void onBitmapLoaded(File imageFile, Bitmap bitmap) {
        mBitmapCache.put(imageFile.getAbsolutePath(), new SoftReference<>(bitmap));
        if (isAttached()) {
            getView().addSampleBitmap(bitmap);
        }
    }

    public void onNetworkLoaded(INeuralNetwork neuralNetwork) {
        if (isAttached()) {
            mNeuralNetwork = neuralNetwork;
            ModelOverviewFragment view = getView();
            view.setNetworkDimensions(neuralNetwork.getInputDimensions());
            view.setOutputLayersNames(neuralNetwork.getOutputLayers());
            view.setModelVersion(neuralNetwork.getModelVersion());
            view.setLoadingVisible(false);
        } else {
            neuralNetwork.release();
        }
    }

    public void onNetworkLoadFailed() {
        if (isAttached()) {
            ModelOverviewFragment view = getView();
            view.displayModelLoadFailed();
        }
    }

    public void classify(final Bitmap bitmap) {
        final INeuralNetwork neuralNetwork = mNeuralNetwork;
        if (neuralNetwork != null) {
            final ClassifyImageTask task = new ClassifyImageTask(this,
                mNeuralNetwork, bitmap, mModel);
            task.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
        } else {
            getView().displayModelNotLoaded();
        }
    }

    public void onClassificationResult(String[] labels) {
        if (isAttached()) {
            ModelOverviewFragment view = getView();
            view.setClassificationResult(labels);
        }
    }

    public void onClassificationFailed() {
        if (isAttached()) {
            getView().displayClassificationFailed();
        }
    }
}
