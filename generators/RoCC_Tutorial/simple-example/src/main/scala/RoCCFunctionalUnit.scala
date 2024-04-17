package tut_1

import chisel3._
import chisel3.util.HasBlackBoxResource
import freechips.rocketchip.tile.{LazyRoCC, LazyRoCCModuleImp, OpcodeSet}
import org.chipsalliance.cde.config.Parameters

//case object myroccXLen extends Field[Int]

class mymemIO (xLen: Int = 64)(implicit p: Parameters) extends Bundle {
  val clock   = Input(Clock())
  val rqvalid = Input(Bool())
  val rqaddr  = Input(UInt(5.W))
  val wren    = Input(Bool())
  val addr    = Input(UInt(10.W))
  val wrdata  = Input(UInt(xLen.W))
  val rdvalid = Output(Bool())
  val rdaddr  = Output(UInt(5.W))
  val rddata  = Output(UInt(xLen.W))
}

class mymem_bb (xLen: Int = 64)(implicit p: Parameters) extends BlackBox with HasBlackBoxResource {
  val io = IO(new mymemIO(xLen))

  addResource("/vsrc/mymem_bb.v")
}

class MyRoccAccel (opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC (
  opcodes   = opcodes,
  nPTWPorts = 0,
  usesFPU   = false,
  roccCSRs  = Nil) {
  override lazy val module = new MyRoccAccelImp(this)
}

class MyRoccAccelImp(outer: MyRoccAccel)(implicit p: Parameters) extends LazyRoCCModuleImp(outer) {
  val xLen = 64

  // Instantiate the rocc modules
  val myroccDecoupler  = Module(new RoCCDecoupler(xLen))

  val myroccController = Module(new RoCCController(xLen))

  val myroccBlackBox   = Module(new mymem_bb(xLen))

  // Connect
  io <> myroccDecoupler.io.rocc_io
  myroccDecoupler.io.clock := clock
  myroccDecoupler.io.reset := reset.asUInt

  myroccController.io.decoupler_io <> myroccDecoupler.io.controller_io
  myroccController.io.clock := clock
  myroccController.io.reset := reset.asUInt

  myroccController.io.bb_io <> myroccBlackBox.io
}