package framework.fpga.vcu118

import freechips.rocketchip.devices.debug.DebugModuleKey
import freechips.rocketchip.devices.tilelink.BootROMLocated
import freechips.rocketchip.diplomacy.{DTSTimebase, SimpleLazyModule}
import freechips.rocketchip.subsystem.{ExtMem, SystemBusKey}
import freechips.rocketchip.tile.XLen
import org.chipsalliance.cde.config.{Config, Parameters}
import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}
import sifive.fpgashells.shell.DesignKey
import testchipip.SerialTLKey

import scala.sys.process._

// BootROOM Configuration
class WithSimpleBootROM extends Config((site, here, up) => {
  case BootROMLocated(x) => up(BootROMLocated(x), site).map{ p =>
    val freqMHz = (site(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toLong
    // Make sure that the bootrom is always rebuilt
    val clean = s"make -C framework/src/main/resources/bootROM/basic clean"
    require (clean.! == 0, "Failed to clean")
    // Build the bootrom
    val make = s"make -C framework/src/main/resources/bootROM/basic XLEN=${site(XLen)} PBUS_CLK=${freqMHz}"
    require (make.! == 0, "Failed to build bootrom")
    p.copy(hang = 0x10000, contentFileName = s"./framework/src/main/resources/bootROM/basic/build/sdboot.bin")
  }
})

// don't use FPGAShell's DesignKey
class WithNoDesignKey extends Config((site, here, up) => {
  case DesignKey => (p: Parameters) => new SimpleLazyModule()(p)
})

class WithNoSerialTL extends Config((site, here, up) => {
  case SerialTLKey => None // remove serialized tl port
})

class WithUART extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L)))
})

class WithDebug extends Config((site, here, up) => {
  case DebugModuleKey => up(DebugModuleKey).map{ debug =>
    debug.copy(clockGate = false)
  }
})

class WithDTS extends Config((site, here, up) => {
  case DTSTimebase => BigInt((1e6).toLong)
})

class WithDDR extends Config((site, here, up) => {
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(VCU118DDRSize)))) // set extmem
})

class WithVCU118Tweaks extends Config(
  // Clock configs
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
    new chipyard.harness.WithHarnessBinderClockFreqMHz(100) ++
    new chipyard.config.WithMemoryBusFrequency(100.0) ++
    new chipyard.config.WithSystemBusFrequency(100.0) ++
    new chipyard.config.WithPeripheryBusFrequency(100.0) ++
    new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
    new chipyard.clocking.WithPassthroughClockGenerator ++
    // Harness Binder
    new WithVCU118UART ++
    new WithVCU118DDRTL ++
    new WithVCU118JTAG ++
    // Peripheris
    new WithUART ++
    new WithDebug ++
    new WithDTS ++
    new WithDDR ++
    // Other configurations
    new WithNoDesignKey ++
    new WithNoSerialTL ++
    new WithSimpleBootROM ++
    new chipyard.config.WithTLBackingMemory ++ // FPGA-shells converts the AXI to TL for us
    new freechips.rocketchip.subsystem.WithoutTLMonitors)

class SmallRocketVCU118Config extends Config(
  new WithVCU118Tweaks ++
  new chipyard.config.WithBroadcastManager ++ // no l2
  new chipyard.SmallRocketConfig)