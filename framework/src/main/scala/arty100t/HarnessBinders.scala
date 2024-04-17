package framework.fpga.arty100t

import chipyard._
import chipyard.harness._
import chipyard.iobinders.JTAGChipIO
import chisel3._
import freechips.rocketchip.devices.debug.HasPeripheryDebug
import freechips.rocketchip.diplomacy.LazyRawModuleImp
import freechips.rocketchip.subsystem.PeripheryBusKey
import freechips.rocketchip.tilelink.TLBundle
import freechips.rocketchip.util.HeterogeneousBag
import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTParams, UARTPortIO}
import testchipip._

class WithArty100TGPIO

class WithArty100TUARTTSI(uartBaudRate: BigInt = 115200) extends OverrideHarnessBinder({
  (system: CanHavePeripheryTLSerial, th: HasHarnessInstantiators, ports: Seq[ClockedIO[SerialIO]]) => {
    implicit val p = chipyard.iobinders.GetSystemParameters(system)
    ports.map({ port =>
      val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[Arty100THarness]
      val freq = p(PeripheryBusKey).dtsFrequency.get
      val bits = port.bits
      port.clock := th.harnessBinderClock
      val ram = TSIHarness.connectRAM(system.serdesser.get, bits, th.harnessBinderReset)
      val uart_to_serial = Module(new UARTToSerial(
        freq, UARTParams(0, initBaudRate=uartBaudRate)))
      val serial_width_adapter = Module(new SerialWidthAdapter(
        narrowW = 8, wideW = TSI.WIDTH))
      serial_width_adapter.io.narrow.flipConnect(uart_to_serial.io.serial)

      ram.module.io.tsi.flipConnect(serial_width_adapter.io.wide)

      ath.io_uart_bb.bundle <> uart_to_serial.io.uart
    })
  }
})

class WithArty100TUART extends OverrideHarnessBinder ({
  (system: HasPeripheryUARTModuleImp, th: HasHarnessInstantiators, ports: Seq[UARTPortIO]) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[Arty100THarness]
    ath.io_uart_bb.bundle <> ports.head
  }
})

class WithArty100TDDRTL extends OverrideHarnessBinder({
  (system: CanHaveMasterTLMemPort, th: HasHarnessInstantiators, ports: Seq[HeterogeneousBag[TLBundle]]) => {
    require(ports.size == 1)
    val artyTh = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[Arty100THarness]
    val bundles = artyTh.ddrClient.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> ports.head
  }
})

class WithArty100TJTAG extends OverrideHarnessBinder ({
  (system: HasPeripheryDebug, th: HasHarnessInstantiators, ports: Seq[Data]) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[Arty100THarness]
    ports.map {
      case jtagIO: JTAGChipIO =>
        val jtagModule = ath.jtagOverlay
        jtagModule.TDO.data := jtagIO.TDO
        jtagModule.TDO.driven := true.B
        jtagIO.TCK := jtagModule.TCK
        jtagIO.TMS := jtagModule.TMS
        jtagIO.TDI := jtagModule.TDI
    }
  }
})