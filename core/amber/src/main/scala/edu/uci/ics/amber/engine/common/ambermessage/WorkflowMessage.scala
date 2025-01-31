package edu.uci.ics.amber.engine.common.ambermessage

import edu.uci.ics.amber.engine.common.virtualidentity.ActorVirtualIdentity

sealed trait WorkflowMessage extends Serializable {
  val from: ActorVirtualIdentity
  val sequenceNumber: Long
}

case object WorkflowMessage {
  def getInMemSize(msg: WorkflowMessage): Long = {
    msg match {
      case dataMsg: WorkflowDataMessage =>
        dataMsg.payload match {
          case df: DataFrame => df.inMemSize
          case _             => 200L
        }
      case _ => 200L
    }
  }
}

case class WorkflowControlMessage(
    from: ActorVirtualIdentity,
    sequenceNumber: Long,
    payload: ControlPayload
) extends WorkflowMessage

case class WorkflowDataMessage(
    from: ActorVirtualIdentity,
    sequenceNumber: Long,
    payload: DataPayload
) extends WorkflowMessage

case class WorkflowRecoveryMessage(
    from: ActorVirtualIdentity,
    payload: RecoveryPayload
)

// sent from network communicator to next worker to poll for credit information
case class CreditRequest(
    from: ActorVirtualIdentity,
    sequenceNumber: Long = -1
) extends WorkflowMessage
