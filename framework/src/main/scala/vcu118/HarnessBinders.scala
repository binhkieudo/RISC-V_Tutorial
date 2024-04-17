package framework.fpga.vcu118

import chisel3._
import chipyard.CanHaveMasterTLMemPort
import chipyard.harness.{HasHarnessInstantiators, OverrideHarnessBinder}
import chipyard.iobinders.JTAGChipIO
import chisel3.{Data, Wire}
import freechips.rocketchip.devices.debug.HasPeripheryDebug
import freechips.rocketchip.diplomacy.LazyRawModuleImp
import freechips.rocketchip.tilelink.TLBundle
import freechips.rocketchip.util.HeterogeneousBag
import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTPortIO}

class WithVCU118UART extends OverrideHarnessBinder ({
  (system: HasPeripheryUARTModuleImp, th: HasHarnessInstantiators, ports: Seq[UARTPortIO]) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[VCU118Harness]
    ath.io_uart_bb.bundle <> ports.head
  }
})

class WithVCU118DDRTL extends OverrideHarnessBinder({
  (system: CanHaveMasterTLMemPort, th: HasHarnessInstantiators, ports: Seq[HeterogeneousBag[TLBundle]]) => {
    require(ports.size == 1)
    val artyTh = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[VCU118Harness]
    val bundles = artyTh.ddrClient.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> ports.head
  }
})

class WithVCU118JTAG extends OverrideHarnessBinder ({
  (system: HasPeripheryDebug, th: HasHarnessInstantiators, ports: Seq[Data]) => {
    val ath = th.asInstanceOf[LazyRawModuleImp].wrapper.asInstanceOf[VCU118Harness]
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