package org.team401.robot2018

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.PowerDistributionPanel
import org.snakeskin.annotation.PostStartup
import org.snakeskin.annotation.Setup
import org.snakeskin.auto.AutoLoop
import org.snakeskin.auto.AutoManager
import org.snakeskin.dsl.Publisher
import org.snakeskin.registry.Controllers
import org.snakeskin.registry.Sensors
import org.snakeskin.registry.Subsystems
import org.team401.robot2018.auto.motion.GyroTurn
import org.team401.robot2018.auto.motion.PDVA
import org.team401.robot2018.auto.motion.RioProfileRunner
import org.team401.robot2018.etc.Constants
import org.team401.robot2018.etc.Constants.Setup.MJPEGParameters.ADDRESS
import org.team401.robot2018.etc.Constants.Setup.MJPEGParameters.PORT
import org.team401.robot2018.etc.Reporting
import org.team401.robot2018.subsystems.*
import org.team401.robot2018.vision.VisionController
import org.team401.robot2018.vision.VisionDataClient

/*
 * 2018-Robot-Code - Created on 1/5/18
 * Author: Cameron Earle
 * 
 * This code is licensed under the GNU GPL v3
 * You can find more info in the LICENSE file at project root
 */

/**
 * @author Cameron Earle
 * @version 1/5/18
 */

val Vision = VisionController("10.4.1.3")
//val VisionData = VisionDataClient(ADDRESS, Integer.valueOf(PORT))
val PDP = PowerDistributionPanel()

object TestAuto: AutoLoop() {
    override val rate = 10L

    lateinit var runner: RioProfileRunner
    lateinit var turn: GyroTurn

    var started = false

    override fun entry() {
        done = false
        started = true
        turn = GyroTurn(Drivetrain.left.master, Drivetrain.right.master, Drivetrain.imu, -180.0, 0.0024, .02, 2.0, 1.0)
        turn.entry()

        /*
        runner = RioProfileRunner(Drivetrain.left.master, Drivetrain.right.master, Drivetrain.imu,
                PDVA(Constants.Setup.PDVA.P, Constants.Setup.PDVA.V),
                PDVA(Constants.Setup.PDVA.P, Constants.Setup.PDVA.V),
                Constants.Setup.HEADING_GAIN,
                tuning = true)

        runner.loadPoints("/home/lvuser/profiles/LEFT_TO_SWITCH_L.csv", "/home/lvuser/profiles/LEFT_TO_SWITCH_R.csv")
        runner.entry()
        */

    }

    override fun action() {
        turn.action()

        if (turn.done) {
            done = true
            println("TURN DONE")
        }
    }

    override fun exit() {
        if (started) {
            turn.exit()
        }
    }
}

@Setup
fun setup() {
    //AutoManager.auto = PowerUpAuto
    AutoManager.auto = TestAuto

    //PowerUpAuto.publish()

    val mjpeg = Array<String>(1) { Constants.Setup.MJPEGParameters.FULL_ADDRESS }
    NetworkTableInstance.getDefault().getEntry("MJPEG STREAMER").setStringArray(mjpeg)

    Subsystems.add(DrivetrainSubsystem, ElevatorSubsystem, IntakeSubsystem, RungsSubsystem)
    Controllers.add(LeftStick, RightStick, Gamepad)
    Sensors.add(VisionStopSensor)
}

@PostStartup private fun startReporting() = Reporting.start()