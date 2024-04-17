package framework.fpga.vc707

import chisel3.{Bool, Clock, Input, Module, Wire}
import freechips.rocketchip.diplomacy.{InModuleBody, LazyModule, LazyRawModuleImp, ValName}
import org.chipsalliance.cde.config.Parameters
import sifive.fpgashells.ip.xilinx.{IBUF, PowerOnResetFPGAOnly}
import sifive.fpgashells.shell.xilinx.{Series7Shell}
import sifive.fpgashells.shell.{ClockInputDesignInput, ClockInputOverlayKey, ClockInputShellInput, DDROverlayKey, DDRShellInput, DesignKey, JTAGDebugOverlayKey, JTAGDebugShellInput, LEDOverlayKey, LEDShellInput, UARTOverlayKey, UARTShellInput}

abstract class VC707ShellCustomOverlays()(implicit p: Parameters) extends Series7Shell
{
  // System
  val sys_clock = Overlay(ClockInputOverlayKey, new SysClockVC707ShellPlacer(this, ClockInputShellInput()))
  val ddr      = Overlay(DDROverlayKey, new DDRVC707ShellPlacer(this, DDRShellInput()))

  // Peripheries
  val led       = Seq.tabulate(8)(i => Overlay(LEDOverlayKey, new LEDVC707ShellPlacer(this, LEDShellInput(color = "red", number = i))(valName = ValName(s"led_status_$i"))))
  val jtag      = Overlay(JTAGDebugOverlayKey, new JTAGDebugVC707ShellPlacer(this, JTAGDebugShellInput()))
  val uart  = Overlay(UARTOverlayKey, new UARTVC707ShellPlacer(this, UARTShellInput()))
}

class VC707CustomShell()(implicit p: Parameters) extends VC707ShellCustomOverlays
{
  val resetPin = InModuleBody { Wire(Bool()) }
  // PLL reset causes
  val pllReset = InModuleBody { Wire(Bool()) }

  val topDesign = LazyModule(p(DesignKey)(designParameters))

  // Place the sys_clock at the Shell if the user didn't ask for it
  p(ClockInputOverlayKey).foreach(_.place(ClockInputDesignInput()))

  override lazy val module = new Impl
  class Impl extends LazyRawModuleImp(this) {
    val reset = IO(Input(Bool()))
    xdc.addBoardPin(reset, "reset")

    val reset_ibuf = Module(new IBUF)
    reset_ibuf.io.I := reset

    val sysclk: Clock = sys_clock.get() match {
      case Some(x: SysClockVC707PlacedOverlay) => x.clock
    }
    val powerOnReset = PowerOnResetFPGAOnly(sysclk)
    sdc.addAsyncPath(Seq(powerOnReset))

    resetPin := !reset_ibuf.io.O

    pllReset := reset_ibuf.io.O || powerOnReset
  }
}
