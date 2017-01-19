/*
 * Copyright (c) 2016 Qualcomm Technologies, Inc.
 * All Rights Reserved.
 * Confidential and Proprietary - Qualcomm Technologies, Inc.
 */
package com.qualcomm.qti.snpe.imageclassifiers.tasks;

import android.os.AsyncTask;
import android.util.Log;

import com.qualcomm.qti.snpe.INeuralNetwork;
import com.qualcomm.qti.snpe.imageclassifiers.Model;
import com.qualcomm.qti.snpe.imageclassifiers.ModelOverviewFragmentController;

import java.io.File;
import java.io.IOException;

import static com.qualcomm.qti.snpe.NeuralProcessingEngine.Runtime.CPU;
import static com.qualcomm.qti.snpe.NeuralProcessingEngine.Runtime.GPU;

public class LoadNetworkTask extends AsyncTask<File, Void, INeuralNetwork> {

    private static final String LOG_TAG = LoadNetworkTask.class.getSimpleName();

    private final ModelOverviewFragmentController mController;

    private final Model mModel;

    public LoadNetworkTask(ModelOverviewFragmentController controller, Model model) {
        mController = controller;
        mModel = model;
    }

    @Override
    protected INeuralNetwork doInBackground(File... params) {
        INeuralNetwork network = null;
        try {
            final INeuralNetwork.Builder builder = new INeuralNetwork.Builder()
                .setDebugEnabled(false)
                .setRuntimeOrder(GPU, CPU)
                .setModel(mModel.file);
            network = builder.build();
        } catch (IOException e) {
            Log.e(LOG_TAG, e.getMessage(), e);
        }
        return network;
    }

    @Override
    protected void onPostExecute(INeuralNetwork neuralNetwork) {
        super.onPostExecute(neuralNetwork);
        if (neuralNetwork != null) {
            mController.onNetworkLoaded(neuralNetwork);
        } else {
            mController.onNetworkLoadFailed();
        }
    }
}
