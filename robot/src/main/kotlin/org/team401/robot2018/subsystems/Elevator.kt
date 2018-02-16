package org.team401.robot2018.subsystems

import com.ctre.phoenix.motorcontrol.*
import com.ctre.phoenix.motorcontrol.can.TalonSRX
import com.ctre.phoenix.motorcontrol.can.VictorSPX
import edu.wpi.first.wpilibj.Solenoid
import org.snakeskin.component.Gearbox
import org.snakeskin.dsl.*
import org.snakeskin.event.Events
import org.snakeskin.publish.Publisher
import org.team401.robot2018.*

//import org.team401.robot2018.MasherBox

/*
 * 2018-Robot-Code - Created on 1/15/18
 * Author: Cameron Earle
 * 
 * This code is licensed under the GNU GPL v3
 * You can find more info in the LICENSE file at project root
 */

/**
 * @author Cameron Earle
 * @version 1/15/18
 */

val ELEVATOR_DEPLOY_MACHINE = "elevator_deploy"
object ElevatorDeployStates {
    const val STOWED = "stowed"
    const val DEPLOY = "deploy"
    const val DEPLOYED = "deployed"
}

val ELEVATOR_MACHINE = "elevator"
object ElevatorStates {
    const val SIGNAL_CONTROL = "signal"
    const val OPEN_LOOP_CONTROL = "openloop"
    const val MANUAL_ADJUSTMENT = "closedloop"
    const val HOLD_POS_UNKNOWN = "pos_lock"
    const val HOMING = "homing"
}

val ELEVATOR_SHIFTER_MACHINE = "elevator_shifter"
object ElevatorShifterStates {
    const val RUN = "high"
    const val CLIMB = "low"
    const val HOLD_CARRIAGE = "hold_carriage"
}

val ELEVATOR_RATCHET_MACHINE = "elevator_ratchet"
object ElevatorRatchetStates {
    const val LOCKED = "locked"
    const val UNLOCKED = "unlocked"
}

val ELEVATOR_KICKER_MACHINE = "elevator_kicker"
object ElevatorKickerStates {
    const val KICK = "out"
    const val STOW = "in"
}

val ELEVATOR_CLAMP_MACHINE = "elevator_clamp"
object  ElevatorClampStates{
    const val CLAMPED = "out"
    const val UNCLAMPED = "in"
}

object Elevator {
    lateinit var gearbox: Gearbox
    lateinit var shifter: Solenoid
    lateinit var deployer: Solenoid
    lateinit var ratchet: Solenoid
    lateinit var kicker: Solenoid
    lateinit var clamp: Solenoid
}

object Signals {
    var elevatorPosition by Publisher(0.0)
    var elevatorHomed by Publisher(false)
}

val ElevatorSubsystem: Subsystem = buildSubsystem {
    val master = TalonSRX(Constants.MotorControllers.ELEVATOR_MASTER_CAN)
    val slave1 = VictorSPX(Constants.MotorControllers.ELEVATOR_SLAVE_1_CAN)
    val slave2 = VictorSPX(Constants.MotorControllers.ELEVATOR_SLAVE_2_CAN)
    val slave3 = VictorSPX(Constants.MotorControllers.ELEVATOR_SLAVE_3_CAN)

    val gearbox = Gearbox(master, slave1, slave2, slave3)

    val shifter = Solenoid(Constants.Pneumatics.ELEVATOR_SHIFTER_SOLENOID)
    val deployer = Solenoid(Constants.Pneumatics.ELEVATOR_DEPLOY_SOLENOID)
    val ratchet = Solenoid(Constants.Pneumatics.ELEVATOR_RATCHET_SOLENOID)
    val kicker = Solenoid(Constants.Pneumatics.ELEVATOR_KICKER_SOLENOID)
    val clamp = Solenoid(Constants.Pneumatics.ELEVATOR_CLAMP_SOLENOID)

    setup {
        Elevator.gearbox = gearbox
        Elevator.shifter = shifter
        Elevator.deployer = deployer
        Elevator.ratchet = ratchet
        Elevator.kicker = kicker
        Elevator.clamp = clamp
        
        gearbox.setSensor(FeedbackDevice.CTRE_MagEncoder_Absolute)

        master.setSelectedSensorPosition(0, 0, 0)
        master.configMotionCruiseVelocity(2046, 0)
        master.configMotionAcceleration(1023, 0)
        master.config_kP(0, Constants.ElevatorParameters.PIDF.P, 0)
        master.config_kI(0, Constants.ElevatorParameters.PIDF.I, 0)
        master.config_kD(0, Constants.ElevatorParameters.PIDF.D, 0)
        master.config_kF(0, Constants.ElevatorParameters.PIDF.F, 0)

        gearbox.setCurrentLimit(Constants.ElevatorParameters.CURRENT_LIMIT_CONTINUOUS)
        master.configReverseLimitSwitchSource(LimitSwitchSource.FeedbackConnector, LimitSwitchNormal.NormallyOpen, 10)
        master.configForwardSoftLimitThreshold(Constants.ElevatorParameters.MAX_POS.toInt(), 10)
        master.configForwardSoftLimitEnable(true, 10)


    }

    val elevatorDeployMachine = stateMachine(ELEVATOR_DEPLOY_MACHINE) {

        state(ElevatorDeployStates.STOWED) {
            entry {
                deployer.set(Constants.ElevatorParameters.DeployMachine.LOCKED)
            }
        }

        state(ElevatorDeployStates.DEPLOY) {
            timeout(Constants.ElevatorParameters.DEPLOY_TIMER, ElevatorDeployStates.DEPLOYED)

            entry {
                deployer.set(Constants.ElevatorParameters.DeployMachine.UNLOCKED)
            }
        }

        state(ElevatorDeployStates.DEPLOYED) {
            entry {
                deployer.set(Constants.ElevatorParameters.DeployMachine.LOCKED)
            }
        }

        default {
            entry {
                deployer.set(false)
            }
        }
    }

    val elevatorMachine = stateMachine(ELEVATOR_MACHINE) {
        /**
         * Takes the elevator to the position specified by its control signal
         */
        fun toSignal() {
            gearbox.set(ControlMode.MotionMagic, Signals.elevatorPosition)
        }

        state(ElevatorStates.SIGNAL_CONTROL) {
            rejectIf { elevatorDeployMachine.getState() != ElevatorDeployStates.DEPLOYED }

            action {
                toSignal()
            }
        }

        state(ElevatorStates.OPEN_LOOP_CONTROL) {
            rejectIf { elevatorDeployMachine.getState() != ElevatorDeployStates.DEPLOYED }

            action {
                gearbox.set(ControlMode.PercentOutput, MasherBox.readAxis { PITCH_BLUE })
            }
        }

        state(ElevatorStates.MANUAL_ADJUSTMENT) {
            rejectIf { elevatorDeployMachine.getState() != ElevatorDeployStates.DEPLOYED}

            var adjustment: Double
            action {
                adjustment = Constants.ElevatorParameters.MANUAL_RATE * MasherBox.readAxis { PITCH_BLUE }
                Signals.elevatorPosition += adjustment
                toSignal()
            }
        }

        state(ElevatorStates.HOLD_POS_UNKNOWN) {
            entry {
                gearbox.setPosition(0)
            }

            action {
                gearbox.set(ControlMode.Position, 0.0)
            }
        }

        state(ElevatorStates.HOMING) {
            rejectIf { elevatorDeployMachine.getState() != ElevatorDeployStates.DEPLOYED }

            entry {
                master.configZeroPosOnReverseLimit(true)
                Signals.elevatorHomed = false
            }

            var sensorData: SensorCollection
            action {
                sensorData = master.sensorCollection
                gearbox.set(ControlMode.PercentOutput, Constants.ElevatorParameters.HOMING_RATE)
                if (sensorData.isRevLimitSwitchClosed) {
                    gearbox.stop()
                    Signals.elevatorPosition = 0.0
                    Signals.elevatorHomed = true
                    setState(ElevatorStates.SIGNAL_CONTROL)
                }
            }

            exit {
                master.configZeroPosOnReverseLimit(false)
            }
        }
    }

    val elevatorShifterMachine = stateMachine(ELEVATOR_SHIFTER_MACHINE) {

        state(ElevatorShifterStates.RUN) {
            entry {
                shifter.set(Constants.ElevatorParameters.ShifterMachine.HIGH)
            }
        }

        state(ElevatorShifterStates.CLIMB) {
            entry {
                shifter.set(Constants.ElevatorParameters.ShifterMachine.LOW)
            }
        }

        state(ElevatorShifterStates.HOLD_CARRIAGE) {
            entry {
                shifter.set(Constants.ElevatorParameters.ShifterMachine.HOLD)
            }
        }

        default {
            entry {
                shifter.set(false)
            }
        }
    }

    val elevatorRatchetMachine = stateMachine(ELEVATOR_RATCHET_MACHINE) {

        state(ElevatorRatchetStates.LOCKED) {
            entry {
                ratchet.set(Constants.ElevatorParameters.RachetMachine.LOCKED)
            }
        }

        state(ElevatorRatchetStates.UNLOCKED) {
            entry {
                ratchet.set(Constants.ElevatorParameters.RachetMachine.UNLOCKED)
            }
        }

        default {
            entry {
                ratchet.set(false)
            }
        }
    }

    val elevatorKickerMachine = stateMachine(ELEVATOR_KICKER_MACHINE) {

        state(ElevatorKickerStates.KICK) {
            entry {
                kicker.set(Constants.ElevatorParameters.KickerMachine.EXTENDED)
            }
        }

        state(ElevatorKickerStates.STOW) {
            entry {
                kicker.set(Constants.ElevatorParameters.KickerMachine.RETRACTED)
            }
        }

        default {
            entry {
                kicker.set(false)
            }
        }
    }

    val elevatorClampMachine = stateMachine(ELEVATOR_CLAMP_MACHINE){
        state(ElevatorClampStates.CLAMPED){
            entry{
                clamp.set(true)
            }
        }
        state(ElevatorClampStates.UNCLAMPED){
            entry {
                clamp.set(false)
            }
        }
        default {
            entry {
                clamp.set(false)
            }
        }
    }

    on (Events.TELEOP_ENABLED){
        elevatorMachine.setState(ElevatorStates.MANUAL_ADJUSTMENT)

    }
    test("Kicker test"){
        //test kicker
        elevatorKickerMachine.setState(ElevatorKickerStates.KICK)
        Thread.sleep(1000)
        elevatorKickerMachine.setState(ElevatorKickerStates.STOW)
        Thread.sleep(1000)
        elevatorKickerMachine.setState("")
        Thread.sleep(1000)

        true
    }
    test("Clamp test"){
        //test clamp
        elevatorClampMachine.setState(ElevatorClampStates.CLAMPED)
        Thread.sleep(1000)
        elevatorClampMachine.setState(ElevatorClampStates.UNCLAMPED)
        Thread.sleep(1000)
        elevatorClampMachine.setState("")
        Thread.sleep(1000)

        true
    }
    test("Ratchet test"){
        //test ratchet
        elevatorRatchetMachine.setState(ElevatorRatchetStates.LOCKED)
        Thread.sleep(1000)
        elevatorRatchetMachine.setState(ElevatorRatchetStates.UNLOCKED)
        Thread.sleep(1000)
        elevatorRatchetMachine.setState("")
        Thread.sleep(1000)

        true
    }
    test("Shifter test"){
        //test shifter
        elevatorShifterMachine.setState(ElevatorShifterStates.RUN)
        Thread.sleep(1000)
        elevatorShifterMachine.setState(ElevatorShifterStates.CLIMB)
        Thread.sleep(1000)
        elevatorShifterMachine.setState(ElevatorShifterStates.HOLD_CARRIAGE)
        Thread.sleep(1000)
        elevatorShifterMachine.setState("")
        Thread.sleep(1000)

        true
    }
    test("Elevator test"){
        //test elevator
        elevatorMachine.setState(ElevatorStates.HOMING)
        Thread.sleep(2000)
        elevatorMachine.setState(ElevatorStates.SIGNAL_CONTROL)
        Signals.elevatorPosition = Constants.ElevatorParameters.MAX_POS

        var masterPower by Publisher(0.0)
        var slave1Power by Publisher(0.0)
        var slave2Power by Publisher(0.0)
        var slave3Power by Publisher(0.0)

        masterPower = PDP.getCurrent(4)
        slave1Power = PDP.getCurrent(5)
        slave2Power = PDP.getCurrent(6)
        slave3Power = PDP.getCurrent(7)

        Thread.sleep(2000)
        Signals.elevatorPosition = Constants.ElevatorParameters.SCALE_POS_HIGH
        Thread.sleep(2000)
        Signals.elevatorPosition = Constants.ElevatorParameters.SCALE_POS
        Thread.sleep(2000)
        Signals.elevatorPosition = Constants.ElevatorParameters.SCALE_POS_LOW
        Thread.sleep(2000)
        Signals.elevatorPosition = Constants.ElevatorParameters.HOME_POS
        Thread.sleep(2000)
        elevatorMachine.setState("")
        Thread.sleep(1000)

        //results of the test
        val tolerance = 5.0
        (masterPower + slave1Power + slave2Power + slave3Power + tolerance) / 4 >= masterPower
    }
}
