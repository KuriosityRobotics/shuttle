package com.kuriosityrobotics.shuttle.hardware;

public interface MetricPositionSensor {
	double getPositionMeters() throws InterruptedException;
	void updateOffsetToMatch(double currentPosition) throws InterruptedException;
}
