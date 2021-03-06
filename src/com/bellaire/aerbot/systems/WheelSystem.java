package com.bellaire.aerbot.systems;

import com.bellaire.aerbot.Environment;
import com.bellaire.aerbot.custom.RobotDrive3;
import com.bellaire.aerbot.input.InputMethod;

import edu.wpi.first.wpilibj.Relay;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.command.PIDSubsystem;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;

public class WheelSystem implements RobotSystem {

	public static final double SHIFTING_SPEED = 2;
	
  private final GyroPID gyroPID = new GyroPID();
  private final StraightDrivePID straightDrivePID = new StraightDrivePID();

  private GyroSystem gyro;
  private AccelerometerSystem accelerometer;
  private RobotDrive3 wheels;
  private Relay gearbox;
  private int gear = 0; // off
  private boolean gearPress = false;
  private double correctRotate;
  private double heading;
  private boolean straightDriving;
  private boolean automatic = true;
  private Timer timer;
  private int front = 1;
  private boolean switchPress;

  private double currentLeftY = 0;
  private double currentRampY = 0;

  public void init(Environment e) {
    wheels = new RobotDrive3(1, 2);

    wheels.setSafetyEnabled(false);

    this.gyro = e.getGyroSystem();

    accelerometer = e.getAccelerometerSystem();

    gearbox = new Relay(2);
    this.gearsForward();
    gear = 1;
    accelerometer = new AccelerometerSystem();
    accelerometer.init(e);
    
    timer = new Timer();
    timer.start();
  }

  public void destroy() {
    wheels.free();
    gearbox.free();
  }

  public void setMotors(double left, double right) {
    wheels.setLeftRightMotorOutputs(left, right);
  }

  public void move(InputMethod input) {
    currentLeftY = -input.getLeftY();
    
    //allow for forward direction to be toggled
    currentLeftY *= front;

    currentRampY += (currentLeftY - currentRampY) * .5;
    
    // if user is using the left stick and not the right stick straight driving will be used
    if (Math.abs(input.getLeftY()) > .15 && Math.abs(input.getRightY()) < .15) {
      straightDrive(currentRampY);
    } else if(!gyroPID.getPIDController().isEnable()){
      wheels.arcadeDrive(currentRampY, input.getRightX());
      straightDriving = false;
    }

    if (!input.gearSwitch()) {
      gearPress = false;
    }

    if (gearPress == false) {
      if (input.gearSwitch()) {
        gearPress = true;

        if (automatic) {
          if (gear == 0) {
            this.gearsForward();
          } else if (gear == 1) {
            this.gearsOff();
          }
        }
        automatic = !automatic;
      }
    }
    if(automatic)
      automaticGearShift();

    // make left and right turns
    if (Math.abs(input.getRightX()) > 0.12 && gyroPID.getPIDController().isEnable()) {
      gyroPID.disable();
    } else if (input.getTurnAround() && !gyroPID.getPIDController().isEnable()){
    	// 180 degree turn
    	if(gyro.getHeading() >= 180)
    		gyroPID.setSetpoint(gyro.getHeading() - 180);
    	else
    		gyroPID.setSetpoint(gyro.getHeading() + 180);
    	gyroPID.enable();
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
    
    //toggle forward direction
    if(!switchPress && input.getSwitchFront())
    	front *= -1;
    switchPress = input.getSwitchFront();

    try{
      SmartDashboard.putBoolean("Low gear: ", gear == 1);
      SmartDashboard.putBoolean("Automatic shifting: ", automatic);
      SmartDashboard.putBoolean("Switched front: ", front == -1);
      SmartDashboard.putNumber("Angle: ", gyro.getHeading());
    }catch(NullPointerException ex){
    	
    }
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
    SmartDashboard.putBoolean("Straight driving: ", straightDriving);
  }

  public void straightDrive(double moveValue) {
    if (!straightDriving) {
      heading = gyro.getHeading();
    }
    straightDriving = true;
    if (Math.abs(heading - gyro.getHeading()) > 2 && !straightDrivePID.getPIDController().isEnable()) {
      straightDrivePID.setSetpoint(heading);
      straightDrivePID.enable();
    } else if (Math.abs(heading - gyro.getHeading()) <= 2 && straightDrivePID.getPIDController().isEnable()) {
      straightDrivePID.disable();
      correctRotate = 0;
    }
    wheels.arcadeDrive(moveValue, correctRotate);
  }

  //shift gears at 1.75 meters per second and won't shift again for 0.5 seconds
  public void automaticGearShift() {
    if (Math.abs(accelerometer.getSpeed()) > SHIFTING_SPEED && gear == 1) {
    	if(timer.get() > 0.5){
    		gearsOff();
    		timer.reset();
    	}
    } else if (Math.abs(accelerometer.getSpeed()) <= SHIFTING_SPEED && gear == 0) {
    	if(timer.get() > 0.5){
    		gearsForward();
    		timer.reset();
    	}
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

  public void turn(double angle) {
    if (!gyroPID.getPIDController().isEnable()) {
      gyroPID.setSetpoint(angle);
      gyroPID.enable();
    } else if (gyroPID.getPosition() == angle) {
      gyroPID.disable();
    }
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

  private class StraightDrivePID extends PIDSubsystem {

    private static final double Kp = .3;
    private static final double Ki = 0.0;
    private static final double Kd = 0.0;

    public StraightDrivePID() {
      super(Kp, Ki, Kd);
    }

    protected double returnPIDInput() {
      return gyro.getHeading();
    }

    protected void usePIDOutput(double d) {
      SmartDashboard.putNumber("Straight drive PID: ", d);
      correctRotate = d;
    }

    protected void initDefaultCommand() {

    }

  }
}
