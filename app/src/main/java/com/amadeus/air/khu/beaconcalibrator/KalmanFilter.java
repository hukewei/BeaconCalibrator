package com.amadeus.air.khu.beaconcalibrator;

import java.util.Map;

/**
 * Created by khu on 13/07/2015.
 */
public class KalmanFilter {
    private double currentMeasurement;
    private double currentPredictedMean;
    private double currentPredictedVar;
    private double  predictionVar;
    private boolean enableDeltaDistance;

    public KalmanFilter(boolean enableDeltaDistance) {
        this.currentPredictedMean = 0;
        this.currentPredictedVar = 1000;
        this.enableDeltaDistance = enableDeltaDistance;
        if(this.enableDeltaDistance) {
            predictionVar = 1.0;
        } else {
            predictionVar = 1.0;
        }
    }


    Pair<Double, Double> update(double mean1, double var1, double mean2, double var2) {
        double new_mean = (var2 * mean1 + var1 * mean2) / (var1 + var2);
        double new_var = 1./(1./var1 + 1./var2);
        return new Pair<>(new_mean,new_var);
    }

    Pair<Double, Double> predict(double mean1, double var1, double mean2, double var2) {
        double new_mean = mean1 + mean2;
        double new_var = var1 + var2;
        return new Pair<>(new_mean,new_var);
    }

    Pair updatePrediction(double mean, double var) {
        Pair<Double, Double> updatedValue = update(currentPredictedMean, currentPredictedVar, mean, var);
        double deltaDistance = updatedValue.getFirst() - currentPredictedMean;
        currentPredictedMean = updatedValue.getFirst();
        currentPredictedVar = updatedValue.getSecond();
        if(!enableDeltaDistance) {
            deltaDistance = 0;
        }
        Pair<Double, Double> predictedValue = predict(currentPredictedMean, currentPredictedVar, deltaDistance, predictionVar);
        currentPredictedMean = predictedValue.getFirst();
        currentPredictedVar = predictedValue.getSecond();
        return predictedValue;
    }
}
