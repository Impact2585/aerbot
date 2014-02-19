package com.bellaire.aerbot.systems;

import com.bellaire.aerbot.Environment;
import com.bellaire.aerbot.custom.RobotDrive3;
import com.bellaire.aerbot.input.InputMethod;
import edu.wpi.first.wpilibj.Relay;
import edu.wpi.first.wpilibj.command.PIDSubsystem;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class WheelSystem implements RobotSystem {

  private final GyroPID gyroPID = new GyroPID();

  private GyroSystem gyro;
  private SonarSystem sonar;
  private AccelerometerSystem accelerometer;
  private RobotDrive3 wheels;
  private Relay gearbox;
  private int gear = 0; // off
  private boolean gearPress = false;

  private double currentLeftY = 0, currentRightX = 0;
  private double currentRampY = 0, currentRampX = 0;

  public void init(Environment e) {
    wheels = new RobotDrive3(1, 2);

    wheels.setSafetyEnabled(false);

    this.gyro = e.getGyroSystem();
    sonar = e.getSonarSystem();

    accelerometer = e.getAccelerometerSystem();

    this.sonar = e.getSonarSystem();

    gearbox = new Relay(2);
    this.gearsOff();
    accelerometer = new AccelerometerSystem();
    accelerometer.init(e);
  }

  public void destroy() {
    wheels.free();
    gearbox.free();
  }

  public void setMotors(double left, double right) {
    wheels.setLeftRightMotorOutputs(left, right);
  }

  public void drive(double outputMaginitude, double curve) {
    wheels.drive(outputMaginitude, curve);
    automaticGearShift();
  }

  public void move(InputMethod input) {
    currentLeftY = -input.getLeftY();
    currentRightX = input.getRightX();

    currentRampY += (currentLeftY - currentRampY) * (20d / 100d);
    currentRampX += (currentRightX - currentRampX) * (20d / 100d);

    /*if(currentLeftY == 0) {
     currentRampY = 0;
     }
     if(currentRightX == 0) {
     currentRampX = 0;
     }*/
    if (input.getLeftY() != 0 && input.getRightX() != 0) {
      wheels.arcadeDrive(currentRampY, currentRampX);
    }

    //SmartDashboard.putNumber("Sonar Distance", sonar.getDistance());
    //SmartDashboard.putNumber("Robot Heading", motion.getHeading());
    //SmartDashboard.putNumber("Robot Speed", motion.getSpeed());
    if (!input.gearSwitch()) {
      gearPress = false;
    }

    if (gearPress == false) {
      if (input.gearSwitch()) {
        gearPress = true;

        if (gear == 0) {
          this.gearsForward();
        } else if (gear == 1) {
          this.gearsOff();
        }
      }
    }

    // make left and right turns
    if (Math.abs(input.getRightX()) > 0.12 && gyroPID.getPIDController().isEnable()) {
      gyroPID.disable();
    } else if (input.getLeftTurn() && !gyroPID.getPIDController().isEnable()) {
      if (gyro.getHeading() < 90) {
        gyroPID.setSetpoint(270 + gyro.getHeading());
      } else {
        gyroPID.setSetpoint(gyro.getHeading() - 90);
      }
      gyroPID.enable();
    } else if (input.getRightTurn() && !gyroPID.getPIDController().isEnable()) {
      if (gyro.getHeading() > 269) {
        gyroPID.setSetpoint(270 - gyro.getHeading());
      } else {
        gyroPID.setSetpoint(gyro.getHeading() + 90);
      }
      gyroPID.enable();
    }
    /*if (input.gearSwitch() && gyro.getHeading() > 2) {
     faceForward();
     }*/

    SmartDashboard.putBoolean("Low gear: ", gearPress);
    SmartDashboard.putNumber("Angle: ", gyro.getHeading());
    try {
      SmartDashboard.putNumber("AccelerationX: ", accelerometer.getAccelerationX());
      SmartDashboard.putNumber("AccelerationY: ", accelerometer.getAccelerationY());
      SmartDashboard.putNumber("AccelerationZ: ", accelerometer.getAccelerationZ());
    } catch (NullPointerException ex) {

    }
    try {
      SmartDashboard.putNumber("Speed: ", accelerometer.getSpeed());
    } catch (NullPointerException ex) {

    }
    try {
      SmartDashboard.putNumber("Range: ", sonar.getDistance());
    } catch (NullPointerException ex) {

    }
  }

  public void automaticGearShift() {
    if (accelerometer.getAccelerationX() > 3) { // if encoder rate is greater than gear shift speed
      gearsForward();
    } else {
      gearsOff();
    }
  }

  public void gearsOff() {
    gear = 0;
    gearbox.set(Relay.Value.kOff);
  }

  public void gearsForward() {
    gear = 1;
    gearbox.set(Relay.Value.kReverse);
  }

  public void faceForward() {
    /*if (gyro.getHeading() < 90 || (gyro.getHeading() < 270 && gyro.getHeading() > 180)) {
     setMotors(.2, -.2);
     } else {
     setMotors(-.2, .2);
     }*/
    if (gyro.getHeading() < 90 || (gyro.getHeading() < 270 && gyro.getHeading() > 180)) {
      turn(0);
    } else {
      turn(180);
    }
  }

  public void turn(double angle) {
    if (!gyroPID.getPIDController().isEnable()) {
      gyroPID.setSetpoint(angle);
      gyroPID.enable();
    } else if (gyroPID.getPosition() == angle) {
      gyroPID.disable();
    }
  }

  public void driveDistance(double distance) {
    if (!gyroPID.getPIDController().isEnable()) {
      gyroPID.setSetpointRelative(distance);
      gyroPID.enable();
    } else if (gyroPID.getPosition() == distance) {
      gyroPID.disable();
    }
    // PID should use another sensor
  }

  public void selfCatch() {
    if (gyro.getHeading() > 1 && gyro.getHeading() < 358) {
      faceForward();
    } else if (gyroPID.getPosition() == 0) {
      // if getPosition equals the point in front of the truss
      //shoot
      driveDistance(0);//driveToDistance point behind truss
    } else if (gyroPID.getSetpoint() == 0) {
      driveDistance(0);//drive to point behind the truss
    } else if (gyroPID.getSetpoint() != 0 || gyroPID.getSetpoint() == 0) {
      //if setpoint is not the point in front of the truss OR the setpoint is the point in front of the truss
      driveDistance(0);//drive to point in front of truss
    }
    // should use another innner PID class
  }

  private class GyroPID extends PIDSubsystem {

    private static final double Kp = .02;
    private static final double Ki = .02;
    private static final double Kd = 0.0;

    public GyroPID() {
      super(Kp, Ki, Kd);
    }

    protected double returnPIDInput() {
      return gyro.getHeading();
    }

    protected void usePIDOutput(double d) {
      SmartDashboard.putNumber("PID: ", d);
      setMotors(-d, d);// positive d will result in right turn and vise versa
    }

    protected void initDefaultCommand() {

    }
  }
}
