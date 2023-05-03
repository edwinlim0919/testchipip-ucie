package ucie 

import chisel3._
import freechips.rocketchip.subsystem.BaseSubsystem
import org.chipsalliance.cde.config.{Field, Config}
import freechips.rocketchip.diplomacy.{LazyModule, AddressSet}
import freechips.rocketchip.tilelink.{TLRAM}

case class BackingSbusScratchpadParams(
  base: BigInt,
  mask: BigInt,
  beatBytes: Int)

case object BackingSbusScratchpadKey extends Field[Option[BackingSbusScratchpadParams]](None)

/**
 * Trait to add a scratchpad on the sbus
 */
trait CanHaveBackingSbusScratchpad { this: BaseSubsystem =>
  private val portName = "Backing-SbusScratchpad"

  val spadOpt = p(BackingSbusScratchpadKey).map { param =>
    val spad = sbus { LazyModule(new TLRAM(address=AddressSet(param.base, param.mask), beatBytes=param.beatBytes, devName=Some("backing-sbusscratchpad"))) }
    sbus.toVariableWidthSlave(Some(portName)) { spad.node }
    spad
  }
}

class WithBackingSbusScratchpad(base: BigInt = 0x80000000L,
                                mask: BigInt = ((4 << 20) - 1),
                                beatBytes: UInt = 32) extends Config((site, here, up) => {
  case BackingSbusScratchpadKey => Some(BackingSbusScratchpadParams(base, mask, beatBytes))
})
