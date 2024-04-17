package framework.fpga.vc707

import freechips.rocketchip.diplomacy.{AddressSet, BundleBridgeSource, InModuleBody, LazyModule, ValName}
import org.chipsalliance.cde.config.Field
import sifive.fpgashells.clocks.{ClockSinkNode, ClockSinkParameters, ClockSourceNode}
import sifive.fpgashells.devices.xilinx.xilinxvc707mig.{XilinxVC707MIG, XilinxVC707MIGPads, XilinxVC707MIGParams}
import sifive.fpgashells.shell.xilinx._
import sifive.fpgashells.shell.{ClockInputDesignInput, ClockInputShellInput, ClockInputShellPlacer, DDRDesignInput, DDROverlayOutput, DDRPlacedOverlay, DDRShellInput, DDRShellPlacer, IOPin, JTAGDebugDesignInput, JTAGDebugShellInput, JTAGDebugShellPlacer, LEDDesignInput, LEDShellInput, LEDShellPlacer, UARTDesignInput, UARTShellInput, UARTShellPlacer}

/* =============================================================
============================ Clock =============================
===============================================================*/
class SysClockVC707PlacedOverlay(val shell: VC707ShellCustomOverlays, name: String, val designInput: ClockInputDesignInput, val shellInput: ClockInputShellInput)
  extends LVDSClockInputXilinxPlacedOverlay(name, designInput, shellInput)
{
  val node = shell { ClockSourceNode(freqMHz = 200, jitterPS = 50)(ValName(name)) }

  shell { InModuleBody {
    shell.xdc.addBoardPin(io.p, "clk_p")
    shell.xdc.addBoardPin(io.n, "clk_n")
  } }
}
class SysClockVC707ShellPlacer(val shell: VC707ShellCustomOverlays, val shellInput: ClockInputShellInput)(implicit val valName: ValName)
  extends ClockInputShellPlacer[VC707ShellCustomOverlays] {
  def place(designInput: ClockInputDesignInput) = new SysClockVC707PlacedOverlay(shell, valName.name, designInput, shellInput)
}

/* =============================================================
============================ DDR =============================
===============================================================*/
case object VC7071GDDRSize extends Field[BigInt](0x40000000L * 1) // 1GB
class DDRVC707PlacedOverlay(val shell: VC707ShellCustomOverlays, name: String, val designInput: DDRDesignInput, val shellInput: DDRShellInput)
  extends DDRPlacedOverlay[XilinxVC707MIGPads](name, designInput, shellInput)
{
  val size = p(VC7071GDDRSize)

  val migParams = XilinxVC707MIGParams(address = AddressSet.misaligned(di.baseAddress, size))
  val mig = LazyModule(new XilinxVC707MIG(migParams))
  val ioNode = BundleBridgeSource(() => mig.module.io.cloneType)
  val topIONode = shell { ioNode.makeSink() }
  val ddrUI     = shell { ClockSourceNode(freqMHz = 200) }
  val areset    = shell { ClockSinkNode(Seq(ClockSinkParameters())) }
  areset := designInput.wrangler := ddrUI

  def overlayOutput = DDROverlayOutput(ddr = mig.node)
  def ioFactory = new XilinxVC707MIGPads(size)

  InModuleBody { ioNode.bundle <> mig.module.io }

  shell { InModuleBody {
    require (shell.sys_clock.get.isDefined, "Use of DDRVC707PlacedOverlay depends on SysClockVC707PlacedOverlay")
    val (sys, _) = shell.sys_clock.get.get.overlayOutput.node.out(0)
    val (ui, _) = ddrUI.out(0)
    val (ar, _) = areset.in(0)
    val port = topIONode.bundle.port
    io <> port
    ui.clock := port.ui_clk
    ui.reset := !port.mmcm_locked || port.ui_clk_sync_rst
    port.sys_clk_i := sys.clock.asUInt
    port.sys_rst := sys.reset // pllReset
    port.aresetn := !ar.reset
  } }

  shell.sdc.addGroup(clocks = Seq("clk_pll_i"))
}
class DDRVC707ShellPlacer(val shell: VC707ShellCustomOverlays, val shellInput: DDRShellInput)(implicit val valName: ValName)
  extends DDRShellPlacer[VC707ShellCustomOverlays] {
  def place(designInput: DDRDesignInput) = new DDRVC707PlacedOverlay(shell, valName.name, designInput, shellInput)
}

/* =============================================================
======================= LED Status =============================
===============================================================*/
class LEDVC707PlacedOverlay(val shell: VC707ShellCustomOverlays, name: String, val designInput: LEDDesignInput, val shellInput: LEDShellInput)
  extends LEDXilinxPlacedOverlay(name, designInput, shellInput, boardPin = Some(s"leds_8bits_tri_o_${shellInput.number}"))
class LEDVC707ShellPlacer(val shell: VC707ShellCustomOverlays, val shellInput: LEDShellInput)(implicit val valName: ValName)
  extends LEDShellPlacer[VC707ShellCustomOverlays] {
  def place(designInput: LEDDesignInput) = new LEDVC707PlacedOverlay(shell, valName.name, designInput, shellInput)
}

/* =============================================================
======================= UART ===================================
===============================================================*/
class UARTVC707PlacedOverlay(val shell: VC707ShellCustomOverlays, name: String, val designInput: UARTDesignInput, val shellInput: UARTShellInput)
  extends UARTXilinxPlacedOverlay(name, designInput, shellInput, true)
{
  shell { InModuleBody {
    val packagePinsWithPackageIOs = Seq(("AT32", IOPin(io.ctsn.get)),
      ("AR34", IOPin(io.rtsn.get)),
      ("AU33", IOPin(io.rxd)),
      ("AU36", IOPin(io.txd)))

    packagePinsWithPackageIOs foreach { case (pin, io) => {
      shell.xdc.addPackagePin(io, pin)
      shell.xdc.addIOStandard(io, "LVCMOS18")
      shell.xdc.addIOB(io)
    } }
  } }
}
class UARTVC707ShellPlacer(val shell: VC707ShellCustomOverlays, val shellInput: UARTShellInput)(implicit val valName: ValName)
  extends UARTShellPlacer[VC707ShellCustomOverlays] {
  def place(designInput: UARTDesignInput) = new UARTVC707PlacedOverlay(shell, valName.name, designInput, shellInput)
}

/* =============================================================
============================= JTAG =============================
===============================================================*/
class JTAGDebugVC707PlacedOverlay(val shell: VC707ShellCustomOverlays, name: String, val designInput: JTAGDebugDesignInput, val shellInput: JTAGDebugShellInput)
  extends JTAGDebugXilinxPlacedOverlay(name, designInput, shellInput)
{
  shell { InModuleBody {
    shell.sdc.addClock("JTCK", IOPin(io.jtag_TCK), 10)
    shell.sdc.addGroup(clocks = Seq("JTCK"))
    shell.xdc.clockDedicatedRouteFalse(IOPin(io.jtag_TCK))
    /* if old method
        val packagePinsWithPackageIOs = Seq(("R32", IOPin(io.jtag_TCK)),
                                            ("W36", IOPin(io.jtag_TMS)),
                                            ("W37", IOPin(io.jtag_TDI)),
                                            ("V40", IOPin(io.jtag_TDO)))
    */
    /*
           #Olimex Pin  Olimex Function LCD Pin LCD Function FPGA Pin
           #1           VREF            14      5V
           #3           TTRST_N         1       LCD_DB7       AN40
           #5           TTDI            2       LCD_DB6       AR39
           #7           TTMS            3       LCD_DB5       AR38
           #9           TTCK            4       LCD_DB4       AT42
           #11          TRTCK           NC      NC            NC
           #13          TTDO            9       LCD_E         AT40
           #15          TSRST_N         10      LCD_RW        AR42
           #2           VREF            14      5V
           #18          GND             13      GND
     */
    val packagePinsWithPackageIOs = Seq(("AT42", IOPin(io.jtag_TCK)),
      ("AR38", IOPin(io.jtag_TMS)),
      ("AR39", IOPin(io.jtag_TDI)),
      ("AR42", IOPin(io.srst_n)),
      ("AT40", IOPin(io.jtag_TDO)))
    packagePinsWithPackageIOs foreach { case (pin, io) => {
      shell.xdc.addPackagePin(io, pin)
      shell.xdc.addIOStandard(io, "LVCMOS18")
      shell.xdc.addPullup(io)
    } }
  } }
}
class JTAGDebugVC707ShellPlacer(val shell: VC707ShellCustomOverlays, val shellInput: JTAGDebugShellInput)(implicit val valName: ValName)
  extends JTAGDebugShellPlacer[VC707ShellCustomOverlays] {
  def place(designInput: JTAGDebugDesignInput) = new JTAGDebugVC707PlacedOverlay(shell, valName.name, designInput, shellInput)
}

