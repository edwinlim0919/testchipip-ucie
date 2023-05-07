package testchipip

import chisel3._
import chisel3.util._

import freechips.rocketchip.subsystem._
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.HellaPeekingArbiter


case class SbusSerDesScratchpadParams (
  base: BigInt,
  mask: BigInt,
  beatBytes: Int,
  maxOutstandingReqs: Int,
  requestLength: Int,
  lineBytes: Int,
  serWidth: Int)


case object SbusSerDesScratchpadKey extends Field[Option[SbusSerDesScratchpadParams]](None)


class SbusSerDesScratchpad(implicit p: Parameters) extends LazyModule {
  val parameters = p(SbusSerDesScratchpadKey).get
  val addrRange = Seq(AddressSet(parameters.base, parameters.mask))

  override lazy val module = new SbusSerDesScratchpadModuleImp(this) 

  //val managerSerdes = LazyModule(new TLSerdes(
  //  w = parameters.serWidth,
  //  params = Seq(TLSlaveParameters.v1(
  //    address = addrRange,
  //    regionType = RegionType.IDEMPOTENT,
  //    executable = false,
  //    supportsGet = TransferSizes(1, parameters.beatBytes),
  //    supportsPutPartial = TransferSizes(1, parameters.beatBytes),
  //    supportsPutFull = TransferSizes(1, parameters.beatBytes),
  //    supportsArithmetic = TransferSizes.none,
  //    supportsLogical    = TransferSizes.none,
  //    fifoId = Some(0))),
  //  beatBytes = parameters.beatBytes))

  //val clientSerdes = LazyModule(new TLDesser(
  //  w = parameters.serWidth,
  //  params = Seq(TLMasterParameters.v1(
  //    name = "client-tl-desser",
  //    sourceId = IdRange(0, parameters.maxOutstandingReqs),
  //    requestFifo = false,
  //    visibility = addrRange))))

  val spad = LazyModule(new TLRAM(address=AddressSet(parameters.base, parameters.mask), beatBytes=parameters.beatBytes, devName=Some("spad-sram")))

  //spad.node := TLBuffer() :=
  //  TLFragmenter(parameters.beatBytes, parameters.lineBytes) := clientSerdes.node

  val managerNode = TLManagerNode(Seq(TLSlavePortParameters.v1(
    Seq(TLSlaveParameters.v1(
      address            = addrRange,
      //resources          = device.reg("mem"),
      regionType         = RegionType.IDEMPOTENT,
      executable         = false,
      supportsGet        = TransferSizes(1, parameters.beatBytes),
      supportsPutPartial = TransferSizes(1, parameters.beatBytes),
      supportsPutFull    = TransferSizes(1, parameters.beatBytes),
      supportsArithmetic = TransferSizes.none,
      supportsLogical    = TransferSizes.none,
      fifoId             = Some(0))), // requests are handled in order
    beatBytes  = parameters.beatBytes,
    minLatency = 1))) // no bypass needed for this device

  val clientNode = TLClientNode(Seq(TLMasterPortParameters.v2(
    Seq(TLMasterParameters.v1(
      name = "tlspammer-passthrough-client",
      sourceId = IdRange(0, parameters.maxOutstandingReqs),
      requestFifo = false,
      visibility = addrRange,
    )),
    channelBytes = TLChannelBeatBytes(parameters.beatBytes))))

  clientNode := TLBuffer() := managerNode
  spad.node := TLBuffer() := clientNode
}


class SbusSerDesScratchpadModuleImp(outer: SbusSerDesScratchpad)(implicit p: Parameters) extends LazyModuleImp(outer) {
  val parameters = p(SbusSerDesScratchpadKey).get
  val addrRange = Seq(AddressSet(parameters.base, parameters.mask))

  val (managertl, manageredge) = outer.managerNode.in(0)
  managertl.a.ready := true.B
  when (managertl.a.fire()) {
    printf("MANAGERTL A\n")
    printf("source: %d\n", managertl.a.bits.source)
    printf("address: %d\n", managertl.a.bits.address)
    printf("data: %d\n\n", managertl.a.bits.data)
  }

  //val qDepth = 64
  //outer.clientSerdes.module.io.ser.head.in <> Queue(outer.managerSerdes.module.io.ser.head.out, qDepth)
  //outer.managerSerdes.module.io.ser.head.in <> Queue(outer.clientSerdes.module.io.ser.head.out, qDepth)
}


trait CanHaveSbusSerDesScratchpad { this: BaseSubsystem =>
  implicit val p: Parameters
  private val portName = "manager-tl-desser"

  val sbusSerDesSpadOpt = p(SbusSerDesScratchpadKey).map { param =>
    val spad = sbus { LazyModule(new SbusSerDesScratchpad()(p)) }
    sbus.toVariableWidthSlave(Some(portName)) { spad.managerNode }
    spad
  }
}


class WithSbusSerDesScratchpad(base: BigInt,
                               mask: BigInt,
                               beatBytes: Int,
                               maxOutstandingReqs: Int,
                               requestLength: Int,
                               lineBytes: Int,
                               serWidth: Int) extends Config((site, here, up) => {
  case SbusSerDesScratchpadKey => Some(SbusSerDesScratchpadParams(base,
                                                                  mask,
                                                                  beatBytes,
                                                                  maxOutstandingReqs,
                                                                  requestLength,
                                                                  lineBytes,
                                                                  serWidth))
})
