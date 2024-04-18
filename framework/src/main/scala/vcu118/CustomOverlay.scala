package framework.fpga.vcu118

import freechips.rocketchip.diplomacy.{AddressSet, BundleBridgeSource, InModuleBody, LazyModule, ValName}
import org.chipsalliance.cde.config.Field
import sifive.fpgashells.clocks.{ClockSinkNode, ClockSinkParameters, ClockSourceNode}
import sifive.fpgashells.devices.xilinx.xilinxvcu118mig.{XilinxVCU118MIG, XilinxVCU118MIGPads, XilinxVCU118MIGParams}
import sifive.fpgashells.shell.xilinx.{JTAGDebugXilinxPlacedOverlay, LEDXilinxPlacedOverlay, LVDSClockInputXilinxPlacedOverlay, UARTXilinxPlacedOverlay}
import sifive.fpgashells.shell.{ClockInputDesignInput, ClockInputShellInput, ClockInputShellPlacer, DDRDesignInput, DDROverlayOutput, DDRPlacedOverlay, DDRShellInput, DDRShellPlacer, IOPin, JTAGDebugDesignInput, JTAGDebugShellInput, JTAGDebugShellPlacer, LEDDesignInput, LEDShellInput, LEDShellPlacer, UARTDesignInput, UARTShellInput, UARTShellPlacer}

/* =============================================================
============================ Clock =============================
===============================================================*/
class SysClockVCU118PlacedOverlay(val shell: VCU118ShellCustomOverlays, name: String, val designInput: ClockInputDesignInput, val shellInput: ClockInputShellInput)
  extends LVDSClockInputXilinxPlacedOverlay(name, designInput, shellInput)
{
  val node = shell { ClockSourceNode(freqMHz = 250, jitterPS = 50)(ValName(name)) }

  shell { InModuleBody {
    shell.xdc.addPackagePin(io.p, "E12")
    shell.xdc.addPackagePin(io.n, "D12")
    shell.xdc.addIOStandard(io.p, "DIFF_SSTL12")
    shell.xdc.addIOStandard(io.n, "DIFF_SSTL12")
  } }
}
class SysClockVCU118ShellPlacer(shell: VCU118ShellCustomOverlays, val shellInput: ClockInputShellInput)(implicit val valName: ValName)
  extends ClockInputShellPlacer[VCU118ShellCustomOverlays]
{
  def place(designInput: ClockInputDesignInput) = new SysClockVCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}

/* =============================================================
========================== Ref Clock ===========================
===============================================================*/
class RefClockVCU118PlacedOverlay(val shell: VCU118ShellCustomOverlays, name: String, val designInput: ClockInputDesignInput, val shellInput: ClockInputShellInput)
  extends LVDSClockInputXilinxPlacedOverlay(name, designInput, shellInput) {
  val node = shell { ClockSourceNode(freqMHz = 125, jitterPS = 50)(ValName(name)) }

  shell { InModuleBody {
    shell.xdc.addPackagePin(io.p, "AY24")
    shell.xdc.addPackagePin(io.n, "AY23")
    shell.xdc.addIOStandard(io.p, "LVDS")
    shell.xdc.addIOStandard(io.n, "LVDS")
  } }
}
class RefClockVCU118ShellPlacer(shell: VCU118ShellCustomOverlays, val shellInput: ClockInputShellInput)(implicit val valName: ValName)
  extends ClockInputShellPlacer[VCU118ShellCustomOverlays] {
  def place(designInput: ClockInputDesignInput) = new RefClockVCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}

/* =============================================================
============================ DDR =============================
===============================================================*/
case object VCU118DDRSize extends Field[BigInt](0x40000000L * 2) // 2GB
class DDRVCU118PlacedOverlay(val shell: VCU118ShellCustomOverlays, name: String, val designInput: DDRDesignInput, val shellInput: DDRShellInput)
  extends DDRPlacedOverlay[XilinxVCU118MIGPads](name, designInput, shellInput)
{
  val size = p(VCU118DDRSize)

  val migParams = XilinxVCU118MIGParams(address = AddressSet.misaligned(di.baseAddress, size))
  val mig = LazyModule(new XilinxVCU118MIG(migParams))
  val ioNode = BundleBridgeSource(() => mig.module.io.cloneType)
  val topIONode = shell { ioNode.makeSink() }
  val ddrUI     = shell { ClockSourceNode(freqMHz = 200) }
  val areset    = shell { ClockSinkNode(Seq(ClockSinkParameters())) }
  areset := designInput.wrangler := ddrUI

  def overlayOutput = DDROverlayOutput(ddr = mig.node)
  def ioFactory = new XilinxVCU118MIGPads(size)

  InModuleBody { ioNode.bundle <> mig.module.io }

  shell { InModuleBody {
    require (shell.sys_clock.get.isDefined, "Use of DDRVCU118Overlay depends on SysClockVCU118Overlay")
    val (sys, _) = shell.sys_clock.get.get.overlayOutput.node.out(0)
    val (ui, _) = ddrUI.out(0)
    val (ar, _) = areset.in(0)
    val port = topIONode.bundle.port
    io <> port
    ui.clock := port.c0_ddr4_ui_clk
    ui.reset := /*!port.mmcm_locked ||*/ port.c0_ddr4_ui_clk_sync_rst
    port.c0_sys_clk_i := sys.clock.asUInt
    port.sys_rst := sys.reset // pllReset
    port.c0_ddr4_aresetn := !ar.reset

    val allddrpins = Seq(  "D14", "B15", "B16", "C14", "C15", "A13", "A14",
      "A15", "A16", "B12", "C12", "B13", "C13", "D15", "H14", "H15", "F15",
      "H13", "G15", "G13", "N20", "E13", "E14", "F14", "A10", "F13", "C8",
      "F11", "E11", "F10", "F9",  "H12", "G12", "E9",  "D9",  "R19", "P19",
      "M18", "M17", "N19", "N18", "N17", "M16", "L16", "K16", "L18", "K18",
      "J17", "H17", "H19", "H18", "F19", "F18", "E19", "E18", "G20", "F20",
      "E17", "D16", "D17", "C17", "C19", "C18", "D20", "D19", "C20", "B20",
      "N23", "M23", "R21", "P21", "R22", "P22", "T23", "R23", "K24", "J24",
      "M21", "L21", "K21", "J21", "K22", "J22", "H23", "H22", "E23", "E22",
      "F21", "E21", "F24", "F23", "D10", "P16", "J19", "E16", "A18", "M22",
      "L20", "G23", "D11", "P17", "K19", "F16", "A19", "N22", "M20", "H24",
      "G11", "R18", "K17", "G18", "B18", "P20", "L23", "G22")

    (IOPin.of(io) zip allddrpins) foreach { case (io, pin) => shell.xdc.addPackagePin(io, pin) }
  } }

  shell.sdc.addGroup(pins = Seq(mig.island.module.blackbox.io.c0_ddr4_ui_clk))
}
class DDRVCU118ShellPlacer(shell: VCU118ShellCustomOverlays, val shellInput: DDRShellInput)(implicit val valName: ValName)
  extends DDRShellPlacer[VCU118ShellCustomOverlays] {
  def place(designInput: DDRDesignInput) = new DDRVCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}

/* =============================================================
======================= LED Status =============================
===============================================================*/
object LEDVCU118PinConstraints {
  val pins = Seq("AT32", "AV34", "AY30", "BB32", "BF32", "AU37", "AV36", "BA37")
}
class LEDVCU118PlacedOverlay(val shell: VCU118ShellCustomOverlays, name: String, val designInput: LEDDesignInput, val shellInput: LEDShellInput)
  extends LEDXilinxPlacedOverlay(name, designInput, shellInput, packagePin = Some(LEDVCU118PinConstraints.pins(shellInput.number)), ioStandard = "LVCMOS12")
class LEDVCU118ShellPlacer(shell: VCU118ShellCustomOverlays, val shellInput: LEDShellInput)(implicit val valName: ValName)
  extends LEDShellPlacer[VCU118ShellCustomOverlays] {
  def place(designInput: LEDDesignInput) = new LEDVCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}

/* =============================================================
=========================== UART ===============================
===============================================================*/
class UARTVCU118PlacedOverlay(val shell: VCU118ShellCustomOverlays, name: String, val designInput: UARTDesignInput, val shellInput: UARTShellInput)
  extends UARTXilinxPlacedOverlay(name, designInput, shellInput, true)
{
  shell { InModuleBody {
    val packagePinsWithPackageIOs = Seq(("AY25", IOPin(io.ctsn.get)),
      ("BB22", IOPin(io.rtsn.get)),
      ("AW25", IOPin(io.rxd)),
      ("BB21", IOPin(io.txd)))

    packagePinsWithPackageIOs foreach { case (pin, io) => {
      shell.xdc.addPackagePin(io, pin)
      shell.xdc.addIOStandard(io, "LVCMOS18")
      shell.xdc.addIOB(io)
    } }
  } }
}
class UARTVCU118ShellPlacer(shell: VCU118ShellCustomOverlays, val shellInput: UARTShellInput)(implicit val valName: ValName)
  extends UARTShellPlacer[VCU118ShellCustomOverlays] {
  def place(designInput: UARTDesignInput) = new UARTVCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}

/* =============================================================
============================= JTAG =============================
===============================================================*/
class JTAGDebugVCU118PlacedOverlay(val shell: VCU118ShellCustomOverlays, name: String, val designInput: JTAGDebugDesignInput, val shellInput: JTAGDebugShellInput)
  extends JTAGDebugXilinxPlacedOverlay(name, designInput, shellInput)
{
  shell { InModuleBody {
    val pin_locations = Seq("AV15", "AY14", "AY15", "AW15", "AV16")
    val pins      = Seq(io.jtag_TCK, io.jtag_TMS, io.jtag_TDI, io.jtag_TDO, io.srst_n)

    shell.sdc.addClock("JTCK", IOPin(io.jtag_TCK), 10)
    shell.sdc.addGroup(clocks = Seq("JTCK"))
    shell.xdc.clockDedicatedRouteFalse(IOPin(io.jtag_TCK))

    val pin_voltage:String = "LVCMOS18"

    (pin_locations zip pins) foreach { case (pin_location, ioport) =>
      val io = IOPin(ioport)
      shell.xdc.addPackagePin(io, pin_location)
      shell.xdc.addIOStandard(io, pin_voltage)
      shell.xdc.addPullup(io)
      shell.xdc.addIOB(io)
    }
  } }
}
class JTAGDebugVCU118ShellPlacer(shell: VCU118ShellCustomOverlays, val shellInput: JTAGDebugShellInput)(implicit val valName: ValName)
  extends JTAGDebugShellPlacer[VCU118ShellCustomOverlays] {
  def place(designInput: JTAGDebugDesignInput) = new JTAGDebugVCU118PlacedOverlay(shell, valName.name, designInput, shellInput)
}
