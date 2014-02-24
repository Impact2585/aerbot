package com.bellaire.aerbot.systems;

import com.bellaire.aerbot.Environment;
import edu.wpi.first.wpilibj.AnalogChannel;

// multiply voltage by 512 * 5 to get it in inches
public class SonarSystem implements RobotSystem {

  private AnalogChannel sonar;

  public void init(Environment e) {
    sonar = new AnalogChannel(1);
  }

  public void destroy() {
      sonar.free();
  }

  public double getDistance() {
    return Math.round(sonar.getVoltage() * (512d / 5d));//rounded to reduce noise
  }

}