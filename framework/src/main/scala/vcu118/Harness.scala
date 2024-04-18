package framework.fpga.vcu118

import chipyard.ExtTLMem
import chipyard.harness.HasHarnessInstantiators
import chisel3._
import chisel3.util.log2Ceil
import freechips.rocketchip.diplomacy.{BundleBridgeSource, IdRange, LazyModule}
import freechips.rocketchip.subsystem.SystemBusKey
import freechips.rocketchip.tilelink.{TLBlockDuringReset, TLClientNode, TLMasterParameters, TLMasterPortParameters}
import org.chipsalliance.cde.config.Parameters
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams, UARTPortIO}
import sifive.fpgashells.clocks.{ClockGroup, ClockSinkNode, PLLFactoryKey, ResetWrangler}
import sifive.fpgashells.shell.{ClockInputDesignInput, ClockInputOverlayKey, DDRDesignInput, DDROverlayKey, JTAGDebugDesignInput, JTAGDebugOverlayKey, LEDDesignInput, LEDOverlayKey, UARTDesignInput, UARTOverlayKey}

class VCU118Harness(override implicit val p: Parameters) extends VCU118CustomShell {
  def dp = designParameters

  // ========= Clock =================
  require(dp(ClockInputOverlayKey).size >= 1)
  val clockOverlay = dp(ClockInputOverlayKey).head.place(ClockInputDesignInput())
  val sysClkNode = clockOverlay.overlayOutput.node
  // connect to the PLL that will generate multiple clocks
  val harnessSysPLL = dp(PLLFactoryKey)
  val harnessSysPLLNode = harnessSysPLL()
  harnessSysPLLNode := sysClkNode
  // create and connect to the dutClock
  val dutFreqMHz = (dp(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toInt
  val dutClock = ClockSinkNode(freqMHz = dutFreqMHz)
  println(s"VCU118 FPGA Base Clock Freq: ${dutFreqMHz} MHz")
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLLNode

  // ========= UART ==================
  val io_uart_bb = BundleBridgeSource(() => new UARTPortIO(dp(PeripheryUARTKey).headOption.getOrElse(UARTParams(0))))
  val uartOverlay = dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))

  // ========== DDR =================
  val ddrOverlay = dp(DDROverlayKey).head.place(DDRDesignInput(dp(ExtTLMem).get.master.base, dutWrangler.node, harnessSysPLLNode, true)).asInstanceOf[DDRVCU118PlacedOverlay]
  val ddrClient = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    name = "chip_ddr",
    sourceId = IdRange(0, 1 << dp(ExtTLMem).get.master.idBits)
  )))))
  val ddrBlockDuringReset = LazyModule(new TLBlockDuringReset(4))
  ddrOverlay.overlayOutput.ddr := ddrBlockDuringReset.node := ddrClient

  // ========= Status LED ============
  val ledStatusOverlays = dp(LEDOverlayKey).map(_.place(LEDDesignInput()))
  val status_leds = ledStatusOverlays.map(_.overlayOutput.led)

  // ========= JTAG ==================
  val jtagOverlay = dp(JTAGDebugOverlayKey).head.place(JTAGDebugDesignInput()).overlayOutput.jtag

  // ====== Module implementation ====
  override lazy val module = new HarnessLikeImpl
  class HarnessLikeImpl extends Impl with HasHarnessInstantiators {
    val clk_tick = clockOverlay.overlayOutput.node.out.head._1.clock

    // Blink the status LEDs for sanity
    withClockAndReset(clk_tick, dutClock.in.head._1.reset) {
      val period = (BigInt(100) << 20) / 2
      val counter = RegInit(0.U(log2Ceil(period).W))
      val on = RegInit(0.U(1.W))
      counter := Mux(counter === (period-1).U, 0.U, counter + 1.U)
      when (counter === 0.U) {
        on := !on
      }
      status_leds(2) := on
    }

    status_leds(0) := !resetPin

    harnessSysPLL.plls.foreach(_._1.getReset.get := pllReset)

    def referenceClockFreqMHz = dutFreqMHz
    def referenceClock = dutClock.in.head._1.clock
    def referenceReset = dutClock.in.head._1.reset
    def success = { require(false, "Unused"); false.B }

    ddrOverlay.mig.module.clock := harnessBinderClock
    ddrOverlay.mig.module.reset := harnessBinderReset
    ddrBlockDuringReset.module.clock := harnessBinderClock
    ddrBlockDuringReset.module.reset := harnessBinderReset.asBool || !ddrOverlay.mig.module.io.port.c0_init_calib_complete

    status_leds(1) := ddrOverlay.mig.module.io.port.c0_init_calib_complete

    instantiateChipTops()
  }

}